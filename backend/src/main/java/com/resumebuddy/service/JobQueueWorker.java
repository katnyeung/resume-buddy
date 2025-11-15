package com.resumebuddy.service;

import com.resumebuddy.model.JobQueueEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Job Queue Worker - Scheduled processor
 * Polls job_queue table every 2 seconds and processes QUEUED jobs
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "app.job-queue.enabled", havingValue = "true", matchIfMissing = true)
public class JobQueueWorker {

    private final UserCreditService userCreditService;
    private final JobExecutor jobExecutor;

    // Support both PostgreSQL and Redis job queue implementations
    private final JobQueueService postgresQueueService;
    private final RedisJobQueueService redisQueueService;

    @Value("${app.job-queue.storage:postgres}")
    private String storageType;

    public JobQueueWorker(
        UserCreditService userCreditService,
        JobExecutor jobExecutor,
        JobQueueService postgresQueueService,
        @Autowired(required = false) RedisJobQueueService redisQueueService
    ) {
        this.userCreditService = userCreditService;
        this.jobExecutor = jobExecutor;
        this.postgresQueueService = postgresQueueService;
        this.redisQueueService = redisQueueService;
    }

    @Value("${app.job-queue.max-concurrent-workers:3}")
    private int maxConcurrentWorkers;

    @Value("${app.job-queue.user-rate-limit:2}")
    private int maxJobsPerUser;

    // Helper methods to abstract storage type
    private boolean canProcessMoreJobs(int maxConcurrent) {
        return "redis".equalsIgnoreCase(storageType) && redisQueueService != null
            ? redisQueueService.canProcessMoreJobs(maxConcurrent)
            : postgresQueueService.canProcessMoreJobs(maxConcurrent);
    }

    private List<JobQueueEntry> fetchNextJobs(int maxJobs) {
        return "redis".equalsIgnoreCase(storageType) && redisQueueService != null
            ? redisQueueService.fetchNextJobs(maxJobs)
            : postgresQueueService.fetchNextJobs(maxJobs);
    }

    private boolean userCanProcessMoreJobs(String userId, int maxPerUser) {
        return "redis".equalsIgnoreCase(storageType) && redisQueueService != null
            ? redisQueueService.userCanProcessMoreJobs(userId, maxPerUser)
            : postgresQueueService.userCanProcessMoreJobs(userId, maxPerUser);
    }

    private boolean tryStartJob(String jobId) {
        return "redis".equalsIgnoreCase(storageType) && redisQueueService != null
            ? redisQueueService.tryStartJob(jobId)
            : postgresQueueService.tryStartJob(jobId);
    }

    private void completeJob(String jobId, Object result, BigDecimal actualCredits) {
        if ("redis".equalsIgnoreCase(storageType) && redisQueueService != null) {
            redisQueueService.completeJob(jobId, result, actualCredits);
        } else {
            postgresQueueService.completeJob(jobId, result, actualCredits);
        }
    }

    private void failJob(String jobId, String errorMessage, BigDecimal creditsToRefund) {
        if ("redis".equalsIgnoreCase(storageType) && redisQueueService != null) {
            redisQueueService.failJob(jobId, errorMessage, creditsToRefund);
        } else {
            postgresQueueService.failJob(jobId, errorMessage, creditsToRefund);
        }
    }

    /**
     * Poll queue at configured interval (default: 5 minutes) and process jobs
     * Increased from 2s to allow Neon database to scale-to-zero during idle periods
     * With Redis storage, can use shorter intervals (10s) since Redis is always in memory
     */
    @Scheduled(fixedDelayString = "${app.job-queue.poll-interval-ms:300000}")
    public void processQueue() {
        try {
            // Check global rate limit
            if (!canProcessMoreJobs(maxConcurrentWorkers)) {
                log.debug("Max concurrent jobs reached ({}), skipping poll", maxConcurrentWorkers);
                return;
            }

            // Fetch next jobs
            List<JobQueueEntry> jobs = fetchNextJobs(maxConcurrentWorkers);

            if (jobs.isEmpty()) {
                return;
            }

            log.info("Fetched {} jobs from {} queue", jobs.size(), storageType);

            // Filter by user rate limit
            List<JobQueueEntry> eligibleJobs = jobs.stream()
                .filter(job -> userCanProcessMoreJobs(job.getUserId(), maxJobsPerUser))
                .collect(Collectors.toList());

            if (eligibleJobs.size() < jobs.size()) {
                log.debug("Filtered out {} jobs due to user rate limits", jobs.size() - eligibleJobs.size());
            }

            // Process jobs in parallel
            List<CompletableFuture<Void>> futures = eligibleJobs.stream()
                .map(job -> CompletableFuture.runAsync(() -> processJob(job)))
                .collect(Collectors.toList());

            // Wait for all to complete (non-blocking for scheduler)
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .exceptionally(ex -> {
                    log.error("Error in parallel job processing", ex);
                    return null;
                });

        } catch (Exception e) {
            log.error("Error in job queue worker poll", e);
        }
    }

    /**
     * Process a single job
     */
    private void processJob(JobQueueEntry job) {
        String jobId = job.getId();

        try {
            // Try to lock job
            if (!tryStartJob(jobId)) {
                log.debug("Failed to lock job {}, already taken by another worker", jobId);
                return;
            }

            log.info("Processing job {}: type={}, user={}, retry={}",
                jobId, job.getJobType(), job.getUserId(), job.getRetryCount());

            // Credits already deducted at queue time - just execute
            Object result = jobExecutor.executeJob(job);

            // Calculate actual credits used
            BigDecimal actualCredits = jobExecutor.calculateActualCredits(job, result);

            // Mark as completed
            completeJob(jobId, result, actualCredits);

            log.info("Job {} completed successfully", jobId);

        } catch (Exception e) {
            log.error("Job {} failed with error", jobId, e);

            // Refund credits (they were deducted at queue time)
            failJob(
                jobId,
                e.getMessage(),
                job.getEstimatedCredits() // Refund estimated amount
            );
        }
    }
}
