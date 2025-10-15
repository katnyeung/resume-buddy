package com.resumebuddy.jobsearch.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.*;
import redis.clients.jedis.search.schemafields.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * Infrastructure Service: Redis Vector Store
 * Manages vector embeddings in Redis using RediSearch
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RedisVectorService {

    private final JedisPooled jedis;
    private static final String VECTOR_INDEX = "idx:vectors";
    private static final int VECTOR_DIM = 1536; // OpenAI embedding dimension

    /**
     * Initialize vector index on startup
     */
    @PostConstruct
    public void init() {
        log.info("Initializing Redis vector index...");
        createVectorIndex();
    }

    /**
     * Create vector index (called on startup)
     */
    public void createVectorIndex() {
        try {
            // Check if index exists
            jedis.ftInfo(VECTOR_INDEX);
            log.info("Vector index '{}' already exists", VECTOR_INDEX);
        } catch (Exception e) {
            // Index doesn't exist, create it
            try {
                Schema schema = new Schema()
                        .addTextField("key", 1.0)
                        .addVectorField("vector",
                                Schema.VectorField.VectorAlgo.HNSW,
                                Map.of(
                                        "TYPE", "FLOAT32",
                                        "DIM", VECTOR_DIM,
                                        "DISTANCE_METRIC", "COSINE"
                                )
                        );

                // IMPORTANT: Use line-level prefixes, NOT full-document prefixes
                IndexDefinition def = new IndexDefinition()
                        .setPrefixes(new String[]{"profile:line:", "listing:line:"});

                jedis.ftCreate(VECTOR_INDEX, IndexOptions.defaultOptions().setDefinition(def), schema);
                log.info("Created vector index '{}' with line-level prefixes", VECTOR_INDEX);
            } catch (Exception ex) {
                log.error("Failed to create vector index", ex);
                throw new RuntimeException("Failed to create vector index", ex);
            }
        }
    }

    /**
     * Rebuild index for line-by-line matching (drops old index)
     */
    public void rebuildIndexForLineMatching() {
        try {
            log.info("Dropping existing vector index '{}'", VECTOR_INDEX);
            jedis.ftDropIndex(VECTOR_INDEX);
            log.info("Successfully dropped index '{}'", VECTOR_INDEX);
        } catch (Exception e) {
            log.warn("Failed to drop index (may not exist): {}", e.getMessage());
        }

        // Recreate with line-level prefixes
        try {
            Schema schema = new Schema()
                    .addTextField("key", 1.0)
                    .addVectorField("vector",
                            Schema.VectorField.VectorAlgo.HNSW,
                            Map.of(
                                    "TYPE", "FLOAT32",
                                    "DIM", VECTOR_DIM,
                                    "DISTANCE_METRIC", "COSINE"
                            )
                    );

            IndexDefinition def = new IndexDefinition()
                    .setPrefixes(new String[]{"profile:line:", "listing:line:"});

            jedis.ftCreate(VECTOR_INDEX, IndexOptions.defaultOptions().setDefinition(def), schema);
            log.info("Rebuilt vector index '{}' with line-level prefixes (profile:line:, listing:line:)", VECTOR_INDEX);
        } catch (Exception ex) {
            log.error("Failed to rebuild vector index", ex);
            throw new RuntimeException("Failed to rebuild vector index", ex);
        }
    }

    /**
     * Store vector embedding in Redis
     */
    public void storeVector(String key, float[] embedding) {
        if (embedding.length != VECTOR_DIM) {
            throw new IllegalArgumentException("Vector dimension must be " + VECTOR_DIM);
        }

        try {
            // Store key as string field
            jedis.hset(key, "key", key);

            // Store vector as raw bytes (NOT Base64)
            byte[] vectorBytes = floatArrayToBytes(embedding);
            jedis.hset(key.getBytes(), "vector".getBytes(), vectorBytes);

            log.debug("Stored vector for key: {} ({} bytes)", key, vectorBytes.length);
        } catch (Exception e) {
            log.error("Failed to store vector for key: {}", key, e);
            throw new RuntimeException("Failed to store vector", e);
        }
    }

    /**
     * Search for similar vectors using KNN
     */
    public List<VectorSearchResult> vectorSearch(String queryVectorKey, int topK) {
        try {
            // Get query vector as raw bytes
            byte[] vectorBytes = jedis.hget(queryVectorKey.getBytes(), "vector".getBytes());
            if (vectorBytes == null) {
                log.warn("Query vector not found: {}", queryVectorKey);
                return Collections.emptyList();
            }

            // Build KNN query
            Query query = new Query("*=>[KNN " + topK + " @vector $vector AS score]")
                    .addParam("vector", vectorBytes)
                    .returnFields("key", "score")
                    .setSortBy("score", true)
                    .dialect(2);

            SearchResult result = jedis.ftSearch(VECTOR_INDEX, query);

            List<VectorSearchResult> results = new ArrayList<>();
            for (Document doc : result.getDocuments()) {
                String key = doc.getString("key");
                double score = Double.parseDouble(doc.getString("score"));
                results.add(new VectorSearchResult(key, score));
            }

            return results;

        } catch (Exception e) {
            log.error("Failed to search vectors", e);
            throw new RuntimeException("Failed to search vectors", e);
        }
    }

    /**
     * Delete vector by key
     */
    public void deleteVector(String key) {
        try {
            jedis.del(key);
            log.debug("Deleted vector for key: {}", key);
        } catch (Exception e) {
            log.error("Failed to delete vector for key: {}", key, e);
            throw new RuntimeException("Failed to delete vector", e);
        }
    }

    /**
     * Convert float array to raw bytes for Redis vector storage
     */
    private byte[] floatArrayToBytes(float[] array) {
        ByteBuffer buffer = ByteBuffer.allocate(array.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : array) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    /**
     * Result class for vector search
     */
    public static class VectorSearchResult {
        private final String key;
        private final double score;

        public VectorSearchResult(String key, double score) {
            this.key = key;
            this.score = score;
        }

        public String getKey() {
            return key;
        }

        public double getScore() {
            return score;
        }
    }
}
