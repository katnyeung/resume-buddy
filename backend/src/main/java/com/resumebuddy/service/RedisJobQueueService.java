package com.resumebuddy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.model.JobQueueEntry;
import com.resumebuddy.model.JobQueueEntry.JobStatus;
import com.resumebuddy.model.JobQueueEntry.JobType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis-based job queue service
 * Uses Redisson for distributed locks and sorted sets for priority queue
 *
 * Redis data structures:
 * - "job:{jobId}" -> RBucket<JobQueueEntry> (job data)
 * - "queue:jobs" -> RScoredSortedSet (priority queue: score = priority + timestamp)
 * - "processing:jobs" -> RSet (currently processing jobs)
 * - "user:{userId}:jobs" -> RSet (user's jobs for rate limiting)
 * - "index:active:{jobType}" -> RSet (active jobs by type for duplicate detection)
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.job-queue.storage", havingValue = "redis", matchIfMissing = false)
public class RedisJobQueueService {

    private final RedissonClient redissonClient;
    private final UserCreditService userCreditService;
    private final TokenCostCalculator tokenCostCalculator;
    private final ObjectMapper objectMapper;

    private static final String JOB_PREFIX = "job:";
    private static final String QUEUE_KEY = "queue:jobs";
    private static final String PROCESSING_KEY = "processing:jobs";
    private static final String USER_PREFIX = "user:";
    private static final String INDEX_ACTIVE_PREFIX = "index:active:";

    /**
     * Enqueue a new job
     */
    public JobQueueEntry enqueueJob(String userId, JobType jobType, Map<String, Object> inputParams, Integer priority) {
        log.info("Enqueuing job for user {}: type={}, priority={}", userId, jobType, priority);

        // Calculate cost
        BigDecimal estimatedCredits = tokenCostCalculator.calculateCost(jobType);

        // Create job entry
        JobQueueEntry job = new JobQueueEntry();
        job.setId(UUID.randomUUID().toString());
        job.setUserId(userId);
        job.setJobType(jobType);
        job.setStatus(JobStatus.QUEUED);
        job.setPriority(priority != null ? priority : 5);
        job.setEstimatedCredits(estimatedCredits);
        job.setQueuedAt(LocalDateTime.now());
        job.setRetryCount(0);
        job.setMaxRetries(3);

        try {
            job.setInputParams(objectMapper.writeValueAsString(inputParams));
        } catch (Exception e) {
            log.error("Failed to serialize input params", e);
            throw new RuntimeException("Failed to serialize job parameters", e);
        }

        // Deduct credits NOW (at queue time, not execution time)
        log.info("Deducting {} credits for job {}", estimatedCredits, job.getId());
        userCreditService.deductCredits(
            userId,
            estimatedCredits,
            job.getId(),
            "Job queued: " + jobType
        );

        // Store job in Redis
        RBucket<JobQueueEntry> jobBucket = redissonClient.getBucket(JOB_PREFIX + job.getId());
        jobBucket.set(job);

        // Add to priority queue (lower score = higher priority)
        // Score = priority * 1000000 + timestamp (so priority 1 always beats priority 2)
        RScoredSortedSet<String> queue = redissonClient.getScoredSortedSet(QUEUE_KEY);
        double score = job.getPriority() * 1000000.0 + System.currentTimeMillis() / 1000.0;
        queue.add(score, job.getId());

        // Add to user's job set
        RSet<String> userJobs = redissonClient.getSet(USER_PREFIX + userId + ":jobs");
        userJobs.add(job.getId());

        // Add to active jobs index for duplicate detection
        RSet<String> activeIndex = redissonClient.getSet(INDEX_ACTIVE_PREFIX + jobType);
        activeIndex.add(job.getId());

        log.info("Job queued successfully in Redis: id={}, type={}, estimated credits={}",
            job.getId(), job.getJobType(), job.getEstimatedCredits());

        return job;
    }

    /**
     * Fetch next jobs to process (with rate limiting)
     */
    public List<JobQueueEntry> fetchNextJobs(int maxJobs) {
        RScoredSortedSet<String> queue = redissonClient.getScoredSortedSet(QUEUE_KEY);

        // Get top N job IDs from priority queue
        Collection<String> jobIds = queue.valueRange(0, maxJobs - 1);

        if (jobIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Fetch job data
        List<JobQueueEntry> jobs = new ArrayList<>();
        for (String jobId : jobIds) {
            RBucket<JobQueueEntry> jobBucket = redissonClient.getBucket(JOB_PREFIX + jobId);
            JobQueueEntry job = jobBucket.get();
            if (job != null && job.getStatus() == JobStatus.QUEUED) {
                jobs.add(job);
            }
        }

        return jobs;
    }

    /**
     * Try to lock and update job status to PROCESSING
     * Returns true if successfully locked, false if already locked by another worker
     */
    public boolean tryStartJob(String jobId) {
        RLock lock = redissonClient.getLock("lock:" + jobId);

        try {
            // Try to acquire lock with 1 second timeout
            if (!lock.tryLock(1, TimeUnit.SECONDS)) {
                log.debug("Failed to acquire lock for job {}", jobId);
                return false;
            }

            try {
                RBucket<JobQueueEntry> jobBucket = redissonClient.getBucket(JOB_PREFIX + jobId);
                JobQueueEntry job = jobBucket.get();

                if (job == null) {
                    log.warn("Job not found: {}", jobId);
                    return false;
                }

                // Check if still in QUEUED status
                if (job.getStatus() != JobStatus.QUEUED) {
                    log.debug("Job {} already picked up by another worker (status: {})", jobId, job.getStatus());
                    return false;
                }

                // Update to PROCESSING
                job.setStatus(JobStatus.PROCESSING);
                job.setStartedAt(LocalDateTime.now());
                jobBucket.set(job);

                // Move from queue to processing set
                RScoredSortedSet<String> queue = redissonClient.getScoredSortedSet(QUEUE_KEY);
                queue.remove(jobId);

                RSet<String> processing = redissonClient.getSet(PROCESSING_KEY);
                processing.add(jobId);

                log.info("Job {} locked and started processing", jobId);
                return true;

            } finally {
                lock.unlock();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while acquiring lock for job {}", jobId, e);
            return false;
        } catch (Exception e) {
            log.error("Error starting job {}", jobId, e);
            return false;
        }
    }

    /**
     * Mark job as completed
     */
    public void completeJob(String jobId, Object result, BigDecimal actualCreditsUsed) {
        log.info("Completing job {}: actualCredits={}", jobId, actualCreditsUsed);

        RBucket<JobQueueEntry> jobBucket = redissonClient.getBucket(JOB_PREFIX + jobId);
        JobQueueEntry job = jobBucket.get();

        if (job == null) {
            throw new RuntimeException("Job not found: " + jobId);
        }

        try {
            job.setResultData(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            log.error("Failed to serialize result data for job {}", jobId, e);
        }

        job.setStatus(JobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        job.setActualCreditsUsed(actualCreditsUsed);
        jobBucket.set(job);

        // Remove from processing set
        RSet<String> processing = redissonClient.getSet(PROCESSING_KEY);
        processing.remove(jobId);

        // Remove from active jobs index
        RSet<String> activeIndex = redissonClient.getSet(INDEX_ACTIVE_PREFIX + job.getJobType());
        activeIndex.remove(jobId);

        // Set expiry on completed job (keep for 24 hours for status polling)
        jobBucket.expire(24, TimeUnit.HOURS);

        log.info("Job {} completed successfully", jobId);
    }

    /**
     * Mark job as failed (with retry logic)
     */
    public void failJob(String jobId, String errorMessage, BigDecimal creditsToRefund) {
        log.error("Failing job {}: error={}", jobId, errorMessage);

        RBucket<JobQueueEntry> jobBucket = redissonClient.getBucket(JOB_PREFIX + jobId);
        JobQueueEntry job = jobBucket.get();

        if (job == null) {
            throw new RuntimeException("Job not found: " + jobId);
        }

        job.incrementRetry();

        if (job.canRetry()) {
            // Retry: reset to QUEUED
            log.info("Job {} will be retried (attempt {}/{})", jobId, job.getRetryCount(), job.getMaxRetries());
            job.setStatus(JobStatus.QUEUED);
            job.setStartedAt(null);
            job.setErrorMessage(String.format("Retry %d/%d: %s",
                job.getRetryCount(), job.getMaxRetries(), errorMessage));
            jobBucket.set(job);

            // Move back to queue
            RSet<String> processing = redissonClient.getSet(PROCESSING_KEY);
            processing.remove(jobId);

            RScoredSortedSet<String> queue = redissonClient.getScoredSortedSet(QUEUE_KEY);
            double score = job.getPriority() * 1000000.0 + System.currentTimeMillis() / 1000.0;
            queue.add(score, jobId);

        } else {
            // Max retries reached: mark as FAILED
            log.error("Job {} failed permanently after {} retries", jobId, job.getRetryCount());
            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(String.format("Failed after %d retries: %s",
                job.getRetryCount(), errorMessage));
            jobBucket.set(job);

            // Remove from processing set
            RSet<String> processing = redissonClient.getSet(PROCESSING_KEY);
            processing.remove(jobId);

            // Remove from active jobs index
            RSet<String> activeIndex = redissonClient.getSet(INDEX_ACTIVE_PREFIX + job.getJobType());
            activeIndex.remove(jobId);

            // Refund credits
            if (creditsToRefund != null && creditsToRefund.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    log.info("Refunding {} credits for failed job {}", creditsToRefund, jobId);
                    userCreditService.refundCredits(
                        job.getUserId(),
                        creditsToRefund,
                        jobId,
                        "Job failed: " + errorMessage
                    );
                } catch (Exception e) {
                    log.error("Failed to refund credits for job {}", jobId, e);
                }
            }

            // Set expiry on failed job (keep for 24 hours)
            jobBucket.expire(24, TimeUnit.HOURS);
        }
    }

    /**
     * Get job status
     */
    public Optional<JobQueueEntry> getJob(String jobId) {
        RBucket<JobQueueEntry> jobBucket = redissonClient.getBucket(JOB_PREFIX + jobId);
        JobQueueEntry job = jobBucket.get();
        return Optional.ofNullable(job);
    }

    /**
     * Get user's jobs
     */
    public List<JobQueueEntry> getUserJobs(String userId) {
        RSet<String> userJobIds = redissonClient.getSet(USER_PREFIX + userId + ":jobs");

        List<JobQueueEntry> jobs = new ArrayList<>();
        for (String jobId : userJobIds) {
            RBucket<JobQueueEntry> jobBucket = redissonClient.getBucket(JOB_PREFIX + jobId);
            JobQueueEntry job = jobBucket.get();
            if (job != null) {
                jobs.add(job);
            }
        }

        // Sort by queued time descending
        jobs.sort((a, b) -> b.getQueuedAt().compareTo(a.getQueuedAt()));
        return jobs;
    }

    /**
     * Get queue position for a job
     */
    public int getQueuePosition(String jobId) {
        RBucket<JobQueueEntry> jobBucket = redissonClient.getBucket(JOB_PREFIX + jobId);
        JobQueueEntry job = jobBucket.get();

        if (job == null || job.getStatus() != JobStatus.QUEUED) {
            return 0; // Not in queue
        }

        RScoredSortedSet<String> queue = redissonClient.getScoredSortedSet(QUEUE_KEY);
        Integer rank = queue.rank(jobId);

        return (rank != null) ? rank + 1 : 0;
    }

    /**
     * Check rate limits
     */
    public boolean canProcessMoreJobs(int maxConcurrent) {
        RSet<String> processing = redissonClient.getSet(PROCESSING_KEY);
        int count = processing.size();
        boolean canProcess = count < maxConcurrent;
        log.debug("Currently processing: {}, max: {}, can process more: {}", count, maxConcurrent, canProcess);
        return canProcess;
    }

    public boolean userCanProcessMoreJobs(String userId, int maxPerUser) {
        RSet<String> userJobIds = redissonClient.getSet(USER_PREFIX + userId + ":jobs");
        RSet<String> processing = redissonClient.getSet(PROCESSING_KEY);

        // Count how many of user's jobs are currently processing
        int count = 0;
        for (String jobId : userJobIds) {
            if (processing.contains(jobId)) {
                count++;
            }
        }

        boolean canProcess = count < maxPerUser;
        log.debug("User {} processing: {}, max: {}, can process more: {}", userId, count, maxPerUser, canProcess);
        return canProcess;
    }

    /**
     * Check if there's an active job (QUEUED or PROCESSING) for a specific resume + experience
     */
    public Optional<JobQueueEntry> findActiveJobForExperience(String resumeId, String experienceId) {
        log.debug("Checking for active job: resumeId={}, experienceId={}", resumeId, experienceId);

        RSet<String> activeJobIds = redissonClient.getSet(INDEX_ACTIVE_PREFIX + JobType.JOB_EXPERIENCE_ANALYSIS);

        for (String jobId : activeJobIds) {
            RBucket<JobQueueEntry> jobBucket = redissonClient.getBucket(JOB_PREFIX + jobId);
            JobQueueEntry job = jobBucket.get();

            if (job == null) continue;

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = objectMapper.readValue(job.getInputParams(), Map.class);
                String jobResumeId = (String) params.get("resumeId");
                String jobExperienceId = (String) params.get("experienceId");

                if (resumeId.equals(jobResumeId) && experienceId.equals(jobExperienceId)) {
                    log.debug("Found active job {} for resumeId={}, experienceId={}", job.getId(), resumeId, experienceId);
                    return Optional.of(job);
                }
            } catch (Exception e) {
                log.warn("Failed to parse inputParams for job {}", job.getId(), e);
            }
        }

        log.debug("No active job found for resumeId={}, experienceId={}", resumeId, experienceId);
        return Optional.empty();
    }

    /**
     * Check if there's an active job (QUEUED or PROCESSING) for a specific resume analysis
     */
    public Optional<JobQueueEntry> findActiveJobForResume(String resumeId) {
        log.debug("Checking for active resume analysis job: resumeId={}", resumeId);

        RSet<String> activeJobIds = redissonClient.getSet(INDEX_ACTIVE_PREFIX + JobType.RESUME_ANALYSIS);

        for (String jobId : activeJobIds) {
            RBucket<JobQueueEntry> jobBucket = redissonClient.getBucket(JOB_PREFIX + jobId);
            JobQueueEntry job = jobBucket.get();

            if (job == null) continue;

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = objectMapper.readValue(job.getInputParams(), Map.class);
                String jobResumeId = (String) params.get("resumeId");

                if (resumeId.equals(jobResumeId)) {
                    log.debug("Found active resume analysis job {} for resumeId={}", job.getId(), resumeId);
                    return Optional.of(job);
                }
            } catch (Exception e) {
                log.warn("Failed to parse inputParams for job {}", job.getId(), e);
            }
        }

        log.debug("No active resume analysis job found for resumeId={}", resumeId);
        return Optional.empty();
    }
}
