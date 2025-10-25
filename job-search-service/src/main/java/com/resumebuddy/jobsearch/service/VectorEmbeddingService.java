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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Infrastructure Service: Vector Embedding Client
 * Calls OpenAI API to convert text to vector embeddings
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VectorEmbeddingService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.openai.api-key}")
    private String apiKey;

    @Value("${app.openai.embedding-model:text-embedding-3-small}")
    private String embeddingModel;

    private static final String OPENAI_EMBEDDING_URL = "https://api.openai.com/v1/embeddings";

    /**
     * Generate vector embedding for text
     */
    public float[] generateEmbedding(String text) {
        try {
            log.debug("Generating embedding for text of length: {}", text.length());

            Map<String, Object> request = Map.of(
                    "model", embeddingModel,
                    "input", text
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            String response = restTemplate.exchange(
                    OPENAI_EMBEDDING_URL,
                    HttpMethod.POST,
                    entity,
                    String.class
            ).getBody();

            // Parse response to extract embedding array
            JsonNode root = objectMapper.readTree(response);
            JsonNode embeddingNode = root.path("data").get(0).path("embedding");

            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new RuntimeException("Invalid embedding response from OpenAI");
            }

            // Convert JSON array to float[]
            int size = embeddingNode.size();
            float[] embedding = new float[size];
            for (int i = 0; i < size; i++) {
                embedding[i] = (float) embeddingNode.get(i).asDouble();
            }

            log.info("Generated embedding with dimension: {}", embedding.length);
            return embedding;

        } catch (Exception e) {
            log.error("Failed to generate embedding", e);
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    /**
     * Batch generate embeddings for multiple texts (OpenAI supports up to 2048 inputs per request)
     */
    public List<float[]> generateEmbeddings(List<String> texts) {
        try {
            if (texts == null || texts.isEmpty()) {
                return new ArrayList<>();
            }

            log.info("Generating batch embeddings for {} texts", texts.size());

            // OpenAI supports up to 2048 inputs per request
            if (texts.size() > 2048) {
                log.warn("Text count {} exceeds OpenAI batch limit (2048), processing in chunks", texts.size());
                // Process in chunks
                List<float[]> allEmbeddings = new ArrayList<>();
                for (int i = 0; i < texts.size(); i += 2048) {
                    int end = Math.min(i + 2048, texts.size());
                    List<String> chunk = texts.subList(i, end);
                    allEmbeddings.addAll(generateEmbeddings(chunk));
                }
                return allEmbeddings;
            }

            Map<String, Object> request = Map.of(
                    "model", embeddingModel,
                    "input", texts  // Send all texts in one request
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            String response = restTemplate.exchange(
                    OPENAI_EMBEDDING_URL,
                    HttpMethod.POST,
                    entity,
                    String.class
            ).getBody();

            // Parse response to extract all embeddings
            JsonNode root = objectMapper.readTree(response);
            JsonNode dataArray = root.path("data");

            if (dataArray == null || !dataArray.isArray()) {
                throw new RuntimeException("Invalid batch embedding response from OpenAI");
            }

            List<float[]> embeddings = new ArrayList<>();
            for (JsonNode dataNode : dataArray) {
                JsonNode embeddingNode = dataNode.path("embedding");
                if (embeddingNode != null && embeddingNode.isArray()) {
                    int size = embeddingNode.size();
                    float[] embedding = new float[size];
                    for (int i = 0; i < size; i++) {
                        embedding[i] = (float) embeddingNode.get(i).asDouble();
                    }
                    embeddings.add(embedding);
                }
            }

            log.info("Generated {} embeddings with dimension: {}", embeddings.size(),
                    embeddings.isEmpty() ? 0 : embeddings.get(0).length);
            return embeddings;

        } catch (Exception e) {
            log.error("Failed to generate batch embeddings", e);
            throw new RuntimeException("Failed to generate batch embeddings", e);
        }
    }
}
