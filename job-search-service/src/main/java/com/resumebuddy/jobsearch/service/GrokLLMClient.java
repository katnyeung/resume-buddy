package com.resumebuddy.jobsearch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Infrastructure Service: Grok LLM Client
 * Generates job post text using Grok-4 model
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GrokLLMClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.grok.api-key}")
    private String apiKey;

    @Value("${app.grok.model:grok-4-fast-reasoning}")
    private String model;

    @Value("${app.grok.base-url:https://api.x.ai/v1}")
    private String baseUrl;

    /**
     * Call Grok LLM with prompt
     */
    public String callLLM(String prompt) {
        try {
            log.debug("Calling Grok LLM with prompt length: {}", prompt.length());

            Map<String, Object> request = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0.7
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            String response = restTemplate.exchange(
                    baseUrl + "/chat/completions",
                    HttpMethod.POST,
                    entity,
                    String.class
            ).getBody();

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();

            log.info("Successfully generated LLM response with length: {}", content.length());
            return content;

        } catch (Exception e) {
            log.error("Failed to call Grok LLM", e);
            throw new RuntimeException("Failed to generate job post with LLM", e);
        }
    }

    /**
     * Call LLM with JSON response format
     */
    public String callLLMWithJsonResponse(String prompt) {
        try {
            log.debug("Calling Grok LLM with JSON format");

            Map<String, Object> request = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0.7,
                    "response_format", Map.of("type", "json_object")
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            String response = restTemplate.exchange(
                    baseUrl + "/chat/completions",
                    HttpMethod.POST,
                    entity,
                    String.class
            ).getBody();

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();

            log.info("Successfully generated JSON LLM response");
            return content;

        } catch (Exception e) {
            log.error("Failed to call Grok LLM with JSON format", e);
            throw new RuntimeException("Failed to generate JSON response with LLM", e);
        }
    }
}
