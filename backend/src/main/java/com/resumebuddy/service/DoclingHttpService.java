package com.resumebuddy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.model.dto.ParsedResume;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoclingHttpService {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private final RunPodDoclingService runPodDoclingService;

    @Value("${app.docling.service-url:http://localhost:8081}")
    private String doclingServiceUrl;

    @Value("${app.docling.use-runpod:false}")
    private boolean useRunPod;

    /**
     * Parse resume from byte array (used when file is already stored in database)
     */
    public ParsedResume parseResumeFromBytes(byte[] fileContent, String filename, String contentType, String resumeId) {
        log.info("Starting Docling parsing for resume: {} (useRunPod={})", filename, useRunPod);

        // Delegate to RunPod if configured
        if (useRunPod) {
            return runPodDoclingService.parseResumeFromBytes(fileContent, filename, contentType, resumeId);
        }

        // For local Docling, convert to MultipartFile
        MultipartFile file = new ByteArrayMultipartFile(fileContent, filename, contentType);
        return parseResume(file, resumeId);
    }

    public ParsedResume parseResume(MultipartFile file, String resumeId) {
        log.info("Starting Docling parsing for resume: {} (useRunPod={})", file.getOriginalFilename(), useRunPod);

        // Delegate to RunPod if configured
        if (useRunPod) {
            return runPodDoclingService.parseResume(file, resumeId);
        }

        // Otherwise use local/HTTP Docling service
        try {
            // Check if Docling service is available
            if (!isDoclingServiceAvailable()) {
                log.warn("Docling service not available, skipping Docling parsing");
                return createBasicParsedResume(file, resumeId);
            }

            // Call Docling microservice
            JsonNode doclingResult = callDoclingService(file);

            if (doclingResult != null && doclingResult.get("success").asBoolean()) {
                return convertDoclingToParsedResume(doclingResult, file, resumeId);
            } else {
                log.warn("Docling parsing failed, creating basic parsed resume");
                return createBasicParsedResume(file, resumeId);
            }

        } catch (Exception e) {
            log.error("Error in Docling HTTP parsing: ", e);
            return createBasicParsedResume(file, resumeId);
        }
    }


    private boolean isDoclingServiceAvailable() {
        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                doclingServiceUrl + "/health",
                JsonNode.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode body = response.getBody();
                if (body != null && body.has("docling_available")) {
                    boolean available = body.get("docling_available").asBoolean();
                    log.info("Docling service health check: docling_available = {}", available);
                    return available;
                } else {
                    log.warn("Docling service health response missing 'docling_available' field: {}", body);
                    return false;
                }
            }
        } catch (Exception e) {
            log.debug("Docling service health check failed: {}", e.getMessage());
        }
        return false;
    }

    private JsonNode callDoclingService(MultipartFile file) {
        try {
            // Prepare multipart request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // Create file resource
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            // Build multipart body
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // Call Docling service
            log.info("Calling Docling service at: {}/parse", doclingServiceUrl);
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                doclingServiceUrl + "/parse",
                requestEntity,
                JsonNode.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Successfully received response from Docling service");
                return response.getBody();
            } else {
                log.error("Docling service returned error status: {}", response.getStatusCode());
                return null;
            }

        } catch (Exception e) {
            log.error("Error calling Docling service: ", e);
            return null;
        }
    }


    private ParsedResume convertDoclingToParsedResume(JsonNode doclingResult, MultipartFile file, String resumeId) {
        ParsedResume parsedResume = new ParsedResume();
        parsedResume.setId(resumeId);
        parsedResume.setFilename(file.getOriginalFilename());
        parsedResume.setContentType(file.getContentType());
        parsedResume.setCreatedAt(LocalDateTime.now());
        parsedResume.setUpdatedAt(LocalDateTime.now());

        // Get the full text and markdown
        String fullText = doclingResult.get("text").asText();
        String markdown = doclingResult.get("markdown").asText();

        // Set original text only - clean for LLM processing
        parsedResume.setOriginalText(fullText);

        log.info("Successfully converted Docling result to ParsedResume");
        return parsedResume;
    }


    private ParsedResume createBasicParsedResume(MultipartFile file, String resumeId) {
        ParsedResume parsedResume = new ParsedResume();
        parsedResume.setId(resumeId);
        parsedResume.setFilename(file.getOriginalFilename());
        parsedResume.setContentType(file.getContentType());
        parsedResume.setCreatedAt(LocalDateTime.now());
        parsedResume.setUpdatedAt(LocalDateTime.now());

        // No text extraction available for fallback
        parsedResume.setOriginalText("");

        return parsedResume;
    }



    public String convertToJson(ParsedResume parsedResume) {
        try {
            return objectMapper.writeValueAsString(parsedResume);
        } catch (Exception e) {
            log.error("Error converting parsed resume to JSON", e);
            return "{}";
        }
    }

    /**
     * Simple MultipartFile implementation for byte arrays
     */
    private static class ByteArrayMultipartFile implements MultipartFile {
        private final byte[] content;
        private final String name;
        private final String originalFilename;
        private final String contentType;

        public ByteArrayMultipartFile(byte[] content, String originalFilename, String contentType) {
            this.content = content;
            this.name = "file";
            this.originalFilename = originalFilename;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content == null || content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return content;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            throw new UnsupportedOperationException("transferTo not supported");
        }
    }
}