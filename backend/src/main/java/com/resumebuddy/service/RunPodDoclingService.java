package com.resumebuddy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.model.dto.ParsedResume;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * RunPod Serverless Docling Service Adapter
 *
 * Handles resume parsing using RunPod serverless GPU instances.
 * Uses polling to wait for async serverless execution to complete.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunPodDoclingService {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.docling.runpod.endpoint-id:khifa2wo4o0p5x}")
    private String runpodEndpointId;

    @Value("${app.docling.runpod.api-key:}")
    private String runpodApiKey;

    @Value("${app.docling.runpod.timeout-seconds:300}")
    private int timeoutSeconds;

    @Value("${app.docling.runpod.poll-interval-ms:2000}")
    private int pollIntervalMs;

    private static final String RUNPOD_BASE_URL = "https://api.runpod.ai/v2";

    @PostConstruct
    public void logConfig() {
        log.info("RunPod Configuration:");
        log.info("  Endpoint ID: {}", runpodEndpointId);
        log.info("  API Key present: {}", runpodApiKey != null && !runpodApiKey.isEmpty());
        log.info("  API Key length: {}", runpodApiKey != null ? runpodApiKey.length() : 0);
    }

    /**
     * Parse resume from byte array using RunPod serverless Docling instance
     */
    public ParsedResume parseResumeFromBytes(byte[] fileContent, String filename, String contentType, String resumeId) {
        log.info("Starting RunPod Docling parsing for resume: {} ({} bytes)", filename, fileContent.length);

        try {
            // Encode file as base64
            String base64File = Base64.getEncoder().encodeToString(fileContent);

            // Submit job to RunPod
            String jobId = submitRunPodJob(base64File, filename, contentType);
            log.info("RunPod job submitted with ID: {}", jobId);

            // Poll for results
            JsonNode result = pollRunPodJob(jobId);

            if (result != null && result.has("text")) {
                return convertRunPodResultToParsedResume(result, filename, contentType, resumeId);
            } else {
                throw new RuntimeException("RunPod parsing failed: No text in response");
            }

        } catch (Exception e) {
            log.error("Error in RunPod Docling parsing: ", e);
            throw new RuntimeException("RunPod parsing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parse resume file using RunPod serverless Docling instance
     */
    public ParsedResume parseResume(MultipartFile file, String resumeId) {
        log.info("Starting RunPod Docling parsing for resume: {}", file.getOriginalFilename());

        try {
            // Submit job to RunPod
            String jobId = submitRunPodJob(file);
            log.info("RunPod job submitted with ID: {}", jobId);

            // Poll for results
            JsonNode result = pollRunPodJob(jobId);

            if (result != null && result.has("text")) {
                return convertRunPodResultToParsedResume(result, file, resumeId);
            } else {
                throw new RuntimeException("RunPod parsing failed: No text in response");
            }

        } catch (Exception e) {
            log.error("Error in RunPod Docling parsing: ", e);
            throw new RuntimeException("RunPod parsing failed: " + e.getMessage(), e);
        }
    }


    /**
     * Submit parsing job to RunPod serverless endpoint (from MultipartFile)
     * @return Job ID for polling
     */
    private String submitRunPodJob(MultipartFile file) throws Exception {
        byte[] fileBytes = file.getBytes();
        String base64File = Base64.getEncoder().encodeToString(fileBytes);
        return submitRunPodJob(base64File, file.getOriginalFilename(), file.getContentType());
    }

    /**
     * Submit parsing job to RunPod serverless endpoint (from base64 string)
     * @return Job ID for polling
     */
    private String submitRunPodJob(String base64File, String filename, String contentType) throws Exception {
        String url = String.format("%s/%s/run", RUNPOD_BASE_URL, runpodEndpointId);

        // Build request body matching RunPod format
        Map<String, Object> input = new HashMap<>();
        input.put("file_base64", base64File);
        input.put("filename", filename);
        input.put("content_type", contentType);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("input", input);

        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + runpodApiKey);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        // Submit job
        log.info("Submitting job to RunPod endpoint: {}", url);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, requestEntity, JsonNode.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            JsonNode body = response.getBody();
            if (body.has("id")) {
                return body.get("id").asText();
            } else {
                throw new RuntimeException("RunPod response missing job ID: " + body);
            }
        } else {
            throw new RuntimeException("Failed to submit RunPod job: " + response.getStatusCode());
        }
    }


    /**
     * Poll RunPod job status until completion
     * @return Parsed result or null if failed
     */
    private JsonNode pollRunPodJob(String jobId) throws Exception {
        String statusUrl = String.format("%s/%s/status/%s", RUNPOD_BASE_URL, runpodEndpointId, jobId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + runpodApiKey);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;
        int pollCount = 0;
        int maxPolls = timeoutSeconds / (pollIntervalMs / 1000) + 10; // Safety margin

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            pollCount++;

            // Extra safety: prevent infinite loop even if time calculation fails
            if (pollCount > maxPolls) {
                throw new RuntimeException("RunPod job exceeded max poll attempts: " + maxPolls);
            }
            try {
                ResponseEntity<JsonNode> response = restTemplate.exchange(
                    statusUrl,
                    HttpMethod.GET,
                    requestEntity,
                    JsonNode.class
                );

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    JsonNode body = response.getBody();
                    String status = body.get("status").asText();

                    log.debug("RunPod job {} status: {}", jobId, status);

                    if ("COMPLETED".equals(status)) {
                        // Extract output
                        if (body.has("output")) {
                            JsonNode output = body.get("output");
                            log.info("RunPod job {} completed successfully after {} polls (~{}s)",
                                jobId, pollCount, (System.currentTimeMillis() - startTime) / 1000);
                            return output;
                        } else {
                            log.error("RunPod job completed but missing output: {}", body);
                            return null;
                        }
                    } else if ("FAILED".equals(status)) {
                        log.error("RunPod job failed: {}", body.has("error") ? body.get("error").asText() : "Unknown error");
                        return null;
                    } else if ("CANCELLED".equals(status) || "TIMED_OUT".equals(status)) {
                        // Handle terminal statuses that aren't success or explicit failure
                        log.error("RunPod job terminated with status: {}", status);
                        return null;
                    }
                    // Status is IN_QUEUE or IN_PROGRESS - continue polling
                }

                // Wait before next poll
                Thread.sleep(pollIntervalMs);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Polling interrupted", e);
            } catch (Exception e) {
                log.warn("Error polling RunPod job status: {}", e.getMessage());
                Thread.sleep(pollIntervalMs);
            }
        }

        throw new RuntimeException("RunPod job timed out after " + timeoutSeconds + " seconds");
    }

    private ParsedResume convertRunPodResultToParsedResume(JsonNode result, MultipartFile file, String resumeId) {
        return convertRunPodResultToParsedResume(result, file.getOriginalFilename(), file.getContentType(), resumeId);
    }

    private ParsedResume convertRunPodResultToParsedResume(JsonNode result, String filename, String contentType, String resumeId) {
        ParsedResume parsedResume = new ParsedResume();
        parsedResume.setId(resumeId);
        parsedResume.setFilename(filename);
        parsedResume.setContentType(contentType);
        parsedResume.setCreatedAt(LocalDateTime.now());
        parsedResume.setUpdatedAt(LocalDateTime.now());

        // Extract text from RunPod result
        String fullText = result.get("text").asText();
        parsedResume.setOriginalText(fullText);

        log.info("Successfully converted RunPod result to ParsedResume");
        return parsedResume;
    }

}
