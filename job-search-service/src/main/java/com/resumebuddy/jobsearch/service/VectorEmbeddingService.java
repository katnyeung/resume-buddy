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

    @Value("${app.openai.embedding-url:https://api.openai.com/v1/embeddings}")
    private String embeddingUrl;

    /**
     * Generate vector embedding for text
     */
    public float[] generateEmbedding(String text) {
        try {
            long startTime = System.currentTimeMillis();
            log.info("╔════════════════════════════════════════════════════════════════════════════════");
            log.info("║ 🚀 NVIDIA NIM API Call - Embedding Generation");
            log.info("║ Model: {} (1024-dimensional vectors)", embeddingModel);
            log.info("║ Endpoint: {}", embeddingUrl);
            log.info("║ Input text length: {} characters", text.length());
            log.info("║ Input preview: {}", text.length() > 100 ? text.substring(0, 100) + "..." : text);
            log.info("╚════════════════════════════════════════════════════════════════════════════════");

            Map<String, Object> request = Map.of(
                    "model", embeddingModel,
                    "input", text,
                    "input_type", "passage"  // Required by NVIDIA NIM for asymmetric models
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            String response = restTemplate.exchange(
                    embeddingUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            ).getBody();

            // Parse response to extract embedding array
            JsonNode root = objectMapper.readTree(response);
            JsonNode embeddingNode = root.path("data").get(0).path("embedding");

            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new RuntimeException("Invalid embedding response from NVIDIA NIM");
            }

            // Convert JSON array to float[]
            int size = embeddingNode.size();
            float[] embedding = new float[size];
            for (int i = 0; i < size; i++) {
                embedding[i] = (float) embeddingNode.get(i).asDouble();
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("╔════════════════════════════════════════════════════════════════════════════════");
            log.info("║ ✅ NVIDIA NIM Response - Success!");
            log.info("║ Vector dimension: {} (NV-Embed-v2 model)", embedding.length);
            log.info("║ Response time: {} ms (sub-second inference)", duration);
            log.info("║ Sample vector values: [{}, {}, {}, ...]",
                embedding[0], embedding[1], embedding[2]);
            log.info("╚════════════════════════════════════════════════════════════════════════════════");
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

            long startTime = System.currentTimeMillis();
            log.info("╔════════════════════════════════════════════════════════════════════════════════");
            log.info("║ 🚀 NVIDIA NIM Batch API Call - Batch Embedding Generation");
            log.info("║ Model: {} (1024-dimensional vectors)", embeddingModel);
            log.info("║ Endpoint: {}", embeddingUrl);
            log.info("║ Batch size: {} texts", texts.size());
            log.info("╚════════════════════════════════════════════════════════════════════════════════");

            // NVIDIA NIM supports up to 2048 inputs per request
            if (texts.size() > 2048) {
                log.warn("Text count {} exceeds NVIDIA NIM batch limit (2048), processing in chunks", texts.size());
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
                    "input", texts,  // Send all texts in one request
                    "input_type", "passage"  // Required by NVIDIA NIM for asymmetric models
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            String response = restTemplate.exchange(
                    embeddingUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            ).getBody();

            // Parse response to extract all embeddings
            JsonNode root = objectMapper.readTree(response);
            JsonNode dataArray = root.path("data");

            if (dataArray == null || !dataArray.isArray()) {
                throw new RuntimeException("Invalid batch embedding response from NVIDIA NIM");
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

            long duration = System.currentTimeMillis() - startTime;
            log.info("╔════════════════════════════════════════════════════════════════════════════════");
            log.info("║ ✅ NVIDIA NIM Batch Response - Success!");
            log.info("║ Generated {} vectors of dimension {} (NV-Embed-v2 model)",
                embeddings.size(), embeddings.isEmpty() ? 0 : embeddings.get(0).length);
            log.info("║ Total response time: {} ms", duration);
            log.info("║ Average time per embedding: {} ms", embeddings.isEmpty() ? 0 : duration / embeddings.size());
            log.info("╚════════════════════════════════════════════════════════════════════════════════");
            return embeddings;

        } catch (Exception e) {
            log.error("Failed to generate batch embeddings", e);
            throw new RuntimeException("Failed to generate batch embeddings", e);
        }
    }
}
