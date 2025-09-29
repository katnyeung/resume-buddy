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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoclingHttpService {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.docling.service-url:http://localhost:8081}")
    private String doclingServiceUrl;

    public ParsedResume parseResume(MultipartFile file, String resumeId) {
        log.info("Starting Docling HTTP parsing for resume: {}", file.getOriginalFilename());

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

    public ParsedResume parseResumeFromUrl(String fileUrl, String resumeId) {
        log.info("Starting Docling HTTP parsing from URL: {}", fileUrl);

        try {
            // Skip health check - just try to call Docling service directly
            JsonNode doclingResult = callDoclingServiceWithUrl(fileUrl);

            if (doclingResult != null && doclingResult.get("success").asBoolean()) {
                return convertDoclingToParsedResumeFromUrl(doclingResult, fileUrl, resumeId);
            } else {
                log.warn("Docling parsing failed, creating basic parsed resume");
                return createBasicParsedResumeFromUrl(fileUrl, resumeId);
            }

        } catch (Exception e) {
            log.error("Error in Docling HTTP parsing from URL: ", e);
            return createBasicParsedResumeFromUrl(fileUrl, resumeId);
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

    private JsonNode callDoclingServiceWithUrl(String fileUrl) {
        try {
            // Create JSON request body
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("file_url", fileUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);

            // Call Docling service with URL
            log.info("Calling Docling service at: {}/parse-url with URL: {}", doclingServiceUrl, fileUrl);
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                doclingServiceUrl + "/parse-url",
                requestEntity,
                JsonNode.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Successfully received response from Docling service for URL parsing");
                return response.getBody();
            } else {
                log.error("Docling service returned error status: {}", response.getStatusCode());
                return null;
            }

        } catch (Exception e) {
            log.error("Error calling Docling service with URL: ", e);
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

    private ParsedResume convertDoclingToParsedResumeFromUrl(JsonNode doclingResult, String fileUrl, String resumeId) {
        ParsedResume parsedResume = new ParsedResume();
        parsedResume.setId(resumeId);

        // Extract filename from URL or use default
        String filename = extractFilenameFromUrl(fileUrl);
        parsedResume.setFilename(filename);

        // Get content type from Docling result
        String contentType = doclingResult.get("content_type").asText();
        parsedResume.setContentType(contentType);

        parsedResume.setCreatedAt(LocalDateTime.now());
        parsedResume.setUpdatedAt(LocalDateTime.now());

        // Get the full text and markdown
        String fullText = doclingResult.get("text").asText();
        String markdown = doclingResult.get("markdown").asText();

        // Set original text only - clean for LLM processing
        parsedResume.setOriginalText(fullText);

        log.info("Successfully converted Docling URL result to ParsedResume");
        return parsedResume;
    }

    private String extractFilenameFromUrl(String fileUrl) {
        try {
            String[] urlParts = fileUrl.split("/");
            String lastPart = urlParts[urlParts.length - 1];
            if (lastPart.equals("file")) {
                return "resume_" + urlParts[urlParts.length - 2] + ".pdf";
            }
            return lastPart;
        } catch (Exception e) {
            return "resume.pdf";
        }
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

    private ParsedResume createBasicParsedResumeFromUrl(String fileUrl, String resumeId) {
        ParsedResume parsedResume = new ParsedResume();
        parsedResume.setId(resumeId);

        // Extract filename from URL
        String filename = extractFilenameFromUrl(fileUrl);
        parsedResume.setFilename(filename);

        // Default content type
        parsedResume.setContentType("application/pdf");
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
}