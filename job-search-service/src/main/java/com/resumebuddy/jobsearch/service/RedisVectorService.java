package com.resumebuddy.jobsearch.service;

import com.resumebuddy.jobsearch.domain.JobListingLine;
import com.resumebuddy.jobsearch.domain.JobSearchProfileLine;
import com.resumebuddy.jobsearch.repository.JobListingLineRepository;
import com.resumebuddy.jobsearch.repository.JobSearchProfileLineRepository;
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
 * Also stores vectors in MySQL for backup/recovery
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RedisVectorService {

    private final JedisPooled jedis;
    private final JobListingLineRepository jobListingLineRepository;
    private final JobSearchProfileLineRepository jobSearchProfileLineRepository;
    private final com.resumebuddy.jobsearch.repository.JobListingRepository jobListingRepository;

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
     * Store vector embedding in Redis ONLY (legacy method)
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
     * Store job listing line vector in Redis ONLY (no PostgreSQL backup)
     * If Redis fails, re-vectorize recent jobs (14 days) via OpenAI API
     *
     * @param lineId Database ID of the job_listing_line
     * @param embedding OpenAI embedding (1536 floats)
     */
    public void storeJobListingLineVector(String lineId, float[] embedding) {
        if (embedding.length != VECTOR_DIM) {
            throw new IllegalArgumentException("Vector dimension must be " + VECTOR_DIM);
        }

        String redisKey = "listing:line:" + lineId;
        byte[] vectorBytes = floatArrayToBytes(embedding);

        try {
            // Store in Redis for fast vector search
            jedis.hset(redisKey, "key", redisKey);
            jedis.hset(redisKey.getBytes(), "vector".getBytes(), vectorBytes);
            log.debug("Stored vector in Redis for key: {} (~{} KB)", redisKey, vectorBytes.length / 1024);

            // Update Redis key reference in PostgreSQL (no vector backup)
            jobListingLineRepository.findById(lineId).ifPresent(line -> {
                line.setRedisVectorKey(redisKey);
                jobListingLineRepository.save(line);
            });

        } catch (Exception e) {
            log.error("Failed to store vector for line: {}", lineId, e);
            throw new RuntimeException("Failed to store vector", e);
        }
    }

    /**
     * Store job search profile line vector in Redis ONLY (no PostgreSQL backup)
     * If Redis fails, re-vectorize user profiles (<100 profiles) via OpenAI API
     *
     * @param lineId Database ID of the job_search_profile_line
     * @param embedding OpenAI embedding (1536 floats)
     */
    public void storeProfileLineVector(String lineId, float[] embedding) {
        if (embedding.length != VECTOR_DIM) {
            throw new IllegalArgumentException("Vector dimension must be " + VECTOR_DIM);
        }

        String redisKey = "profile:line:" + lineId;
        byte[] vectorBytes = floatArrayToBytes(embedding);

        try {
            // Store in Redis for fast vector search
            jedis.hset(redisKey, "key", redisKey);
            jedis.hset(redisKey.getBytes(), "vector".getBytes(), vectorBytes);
            log.debug("Stored vector in Redis for key: {} (~{} KB)", redisKey, vectorBytes.length / 1024);

            // Update Redis key reference in PostgreSQL (no vector backup)
            jobSearchProfileLineRepository.findById(lineId).ifPresent(line -> {
                line.setRedisVectorKey(redisKey);
                jobSearchProfileLineRepository.save(line);
            });

        } catch (Exception e) {
            log.error("Failed to store vector for profile line: {}", lineId, e);
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

            // Build KNN query with explicit LIMIT
            // Redis default LIMIT is 10, so we must explicitly set it to topK
            Query query = new Query("*=>[KNN " + topK + " @vector $vector AS score]")
                    .addParam("vector", vectorBytes)
                    .returnFields("key", "score")
                    .setSortBy("score", true)
                    .limit(0, topK)  // CRITICAL: Set explicit LIMIT to override Redis default of 10
                    .dialect(2);

            SearchResult result = jedis.ftSearch(VECTOR_INDEX, query);

            List<VectorSearchResult> results = new ArrayList<>();
            for (Document doc : result.getDocuments()) {
                String key = doc.getString("key");
                double score = Double.parseDouble(doc.getString("score"));
                results.add(new VectorSearchResult(key, score));
            }

            log.debug("Vector search returned {} results (requested: {})", results.size(), topK);
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
     * Delete vectors in batch (for cleanup operations)
     * Uses batching to avoid Redis timeout on large deletions
     */
    public void deleteVectors(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            log.debug("No keys to delete");
            return;
        }

        final int BATCH_SIZE = 100; // Delete 100 keys at a time to avoid timeout
        int totalDeleted = 0;
        int batchCount = (keys.size() + BATCH_SIZE - 1) / BATCH_SIZE;

        log.info("Deleting {} keys in {} batches of {}", keys.size(), batchCount, BATCH_SIZE);

        try {
            for (int i = 0; i < keys.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, keys.size());
                List<String> batch = keys.subList(i, end);
                String[] keyArray = batch.toArray(new String[0]);

                long deleted = jedis.del(keyArray);
                totalDeleted += deleted;

                if ((i / BATCH_SIZE + 1) % 10 == 0) {
                    log.info("Progress: Deleted {}/{} keys ({} batches)", totalDeleted, keys.size(), (i / BATCH_SIZE + 1));
                }
            }

            log.info("Successfully deleted {} vectors from Redis in {} batches", totalDeleted, batchCount);
        } catch (Exception e) {
            log.error("Failed to delete batch of {} vectors (deleted {} before error)", keys.size(), totalDeleted, e);
            throw new RuntimeException("Failed to delete vectors (partial deletion: " + totalDeleted + " keys)", e);
        }
    }

    /**
     * Clean up old job listing vectors (older than N days)
     * Removes vectors from Redis for jobs fetched before the cutoff date
     * Useful for saving Redis memory and keeping only recent job matches
     *
     * @param daysToKeep Keep only jobs from last N days (default: 14)
     * @return Number of vectors deleted
     */
    public int cleanupOldJobVectors(int daysToKeep) {
        log.info("=== Starting cleanup of job vectors older than {} days ===", daysToKeep);
        long startTime = System.currentTimeMillis();

        try {
            // Calculate cutoff date
            java.time.LocalDateTime cutoffDate = java.time.LocalDateTime.now().minusDays(daysToKeep);
            log.info("Cutoff date: {} (deleting vectors for jobs older than this)", cutoffDate);

            // Step 1: Find all old jobs using repository
            List<com.resumebuddy.jobsearch.domain.JobListing> oldJobs =
                    jobListingRepository.findByFetchedAtBefore(cutoffDate);
            log.info("Found {} old job listings (fetched before {})", oldJobs.size(), cutoffDate);

            if (oldJobs.isEmpty()) {
                log.info("No old jobs to clean up");
                return 0;
            }

            // Step 2: Collect listing IDs
            Set<String> oldJobIds = oldJobs.stream()
                    .map(com.resumebuddy.jobsearch.domain.JobListing::getId)
                    .collect(java.util.stream.Collectors.toSet());

            log.info("Collected {} old job IDs", oldJobIds.size());

            // Step 3: Find all lines belonging to old jobs
            List<JobListingLine> oldLines = jobListingLineRepository.findAll().stream()
                    .filter(line -> oldJobIds.contains(line.getListingId()))
                    .toList();

            log.info("Found {} lines belonging to old jobs", oldLines.size());

            // Step 4: Collect Redis keys to delete
            List<String> keysToDelete = oldLines.stream()
                    .filter(line -> line.getRedisVectorKey() != null && !line.getRedisVectorKey().isEmpty())
                    .map(JobListingLine::getRedisVectorKey)
                    .toList();

            if (keysToDelete.isEmpty()) {
                log.info("No old vectors to delete (lines have no vector keys)");
                return 0;
            }

            log.info("Found {} Redis keys to delete", keysToDelete.size());

            // Step 5: Delete vectors from Redis in batch
            deleteVectors(keysToDelete);

            // Step 6: Clear Redis vector keys from database (batch update)
            Set<String> keysDeletedSet = new HashSet<>(keysToDelete);
            List<JobListingLine> linesToUpdate = new ArrayList<>();

            for (JobListingLine line : oldLines) {
                if (keysDeletedSet.contains(line.getRedisVectorKey())) {
                    line.setRedisVectorKey(null);
                    linesToUpdate.add(line);
                }
            }

            if (!linesToUpdate.isEmpty()) {
                log.info("Updating {} database records to clear vector keys", linesToUpdate.size());
                jobListingLineRepository.saveAll(linesToUpdate);
                log.info("Database update complete");
            }

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("=== Cleanup completed - Deleted {} vectors in {}ms ===", keysToDelete.size(), processingTime);

            return keysToDelete.size();

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Failed to cleanup old vectors after {}ms: {}", processingTime, e.getMessage(), e);
            throw new RuntimeException("Failed to cleanup old vectors", e);
        }
    }

    /**
     * DEPRECATED: PostgreSQL vector backup removed (2025-10-22) to save storage costs
     *
     * For disaster recovery after Redis data loss, use re-vectorization endpoints instead:
     * - POST /api/job-search/admin/revectorize/listing-lines?daysBack=14
     * - POST /api/job-search/admin/revectorize/profile-lines
     *
     * Cost comparison:
     * - Old: $0.00 (instant recovery from PostgreSQL), but ongoing storage cost
     * - New: ~$0.10 for 14 days (one-time re-vectorization), saves 50% PostgreSQL storage
     *
     * @deprecated Use re-vectorization endpoints for disaster recovery
     * @return Nothing - throws UnsupportedOperationException
     */
    @Deprecated(since = "2025-10-22", forRemoval = true)
    public int rebuildRedisFromMysql() {
        throw new UnsupportedOperationException(
            "PostgreSQL vector backup removed to save storage. " +
            "Use re-vectorization instead: POST /api/job-search/admin/revectorize/listing-lines?daysBack=14"
        );
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
