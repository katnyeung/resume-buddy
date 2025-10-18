package com.resumebuddy.service;

import com.resumebuddy.model.JobQueueEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.job-queue.enabled", havingValue = "true", matchIfMissing = true)
public class JobQueueWorker {

    private final JobQueueService jobQueueService;
    private final UserCreditService userCreditService;
    private final JobExecutor jobExecutor;

    @Value("${app.job-queue.max-concurrent-workers:3}")
    private int maxConcurrentWorkers;

    @Value("${app.job-queue.user-rate-limit:2}")
    private int maxJobsPerUser;

    /**
     * Poll queue every 2 seconds and process jobs
     */
    @Scheduled(fixedDelayString = "${app.job-queue.poll-interval-ms:2000}")
    public void processQueue() {
        try {
            // Check global rate limit
            if (!jobQueueService.canProcessMoreJobs(maxConcurrentWorkers)) {
                log.debug("Max concurrent jobs reached ({}), skipping poll", maxConcurrentWorkers);
                return;
            }

            // Fetch next jobs
            List<JobQueueEntry> jobs = jobQueueService.fetchNextJobs(maxConcurrentWorkers);

            if (jobs.isEmpty()) {
                return;
            }

            log.info("Fetched {} jobs from queue", jobs.size());

            // Filter by user rate limit
            List<JobQueueEntry> eligibleJobs = jobs.stream()
                .filter(job -> jobQueueService.userCanProcessMoreJobs(job.getUserId(), maxJobsPerUser))
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
            if (!jobQueueService.tryStartJob(jobId)) {
                log.debug("Failed to lock job {}, already taken by another worker", jobId);
                return;
            }

            log.info("Processing job {}: type={}, user={}", jobId, job.getJobType(), job.getUserId());

            // Deduct credits BEFORE execution
            userCreditService.deductCredits(
                job.getUserId(),
                job.getEstimatedCredits(),
                jobId,
                "Job execution: " + job.getJobType()
            );

            // Execute job
            Object result = jobExecutor.executeJob(job);

            // Calculate actual credits used
            BigDecimal actualCredits = jobExecutor.calculateActualCredits(job, result);

            // Mark as completed
            jobQueueService.completeJob(jobId, result, actualCredits);

            log.info("Job {} completed successfully", jobId);

        } catch (UserCreditService.InsufficientCreditsException e) {
            log.error("Insufficient credits for job {}: {}", jobId, e.getMessage());
            jobQueueService.failJob(jobId, e.getMessage(), BigDecimal.ZERO); // No refund (credits weren't deducted)

        } catch (Exception e) {
            log.error("Job {} failed with error", jobId, e);

            // Refund credits (they were deducted before execution)
            jobQueueService.failJob(
                jobId,
                e.getMessage(),
                job.getEstimatedCredits() // Refund estimated amount
            );
        }
    }
}
