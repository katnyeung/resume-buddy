package com.resumebuddy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.model.JobQueueEntry;
import com.resumebuddy.model.JobQueueEntry.JobStatus;
import com.resumebuddy.model.JobQueueEntry.JobType;
import com.resumebuddy.repository.JobQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobQueueService {

    private final JobQueueRepository jobQueueRepository;
    private final UserCreditService userCreditService;
    private final TokenCostCalculator tokenCostCalculator;
    private final ObjectMapper objectMapper;

    /**
     * Enqueue a new job
     */
    @Transactional
    public JobQueueEntry enqueueJob(String userId, JobType jobType, Map<String, Object> inputParams, Integer priority) {
        log.info("Enqueuing job for user {}: type={}, priority={}", userId, jobType, priority);

        // Calculate cost
        BigDecimal estimatedCredits = tokenCostCalculator.calculateCost(jobType);

        // Check if user has enough credits
        if (!userCreditService.hasEnoughCredits(userId, estimatedCredits)) {
            throw new UserCreditService.InsufficientCreditsException(
                String.format("Insufficient credits to queue job. Required: %s credits", estimatedCredits));
        }

        // Create job entry
        JobQueueEntry job = new JobQueueEntry();
        job.setId(UUID.randomUUID().toString());
        job.setUserId(userId);
        job.setJobType(jobType);
        job.setStatus(JobStatus.QUEUED);
        job.setPriority(priority != null ? priority : 5);
        job.setEstimatedCredits(estimatedCredits);

        try {
            job.setInputParams(objectMapper.writeValueAsString(inputParams));
        } catch (Exception e) {
            log.error("Failed to serialize input params", e);
            throw new RuntimeException("Failed to serialize job parameters", e);
        }

        JobQueueEntry saved = jobQueueRepository.save(job);
        log.info("Job queued successfully: id={}, type={}, estimated credits={}",
            saved.getId(), saved.getJobType(), saved.getEstimatedCredits());

        return saved;
    }

    /**
     * Fetch next jobs to process (with rate limiting)
     */
    @Transactional
    public List<JobQueueEntry> fetchNextJobs(int maxJobs) {
        return jobQueueRepository.findTopByStatusOrderByPriorityAscQueuedAtAsc(
            JobStatus.QUEUED,
            PageRequest.of(0, maxJobs)
        );
    }

    /**
     * Try to lock and update job status to PROCESSING
     * Returns true if successfully locked, false if already locked by another worker
     */
    @Transactional
    public boolean tryStartJob(String jobId) {
        try {
            Optional<JobQueueEntry> jobOpt = jobQueueRepository.findByIdWithLock(jobId);
            if (jobOpt.isEmpty()) {
                log.warn("Job not found: {}", jobId);
                return false;
            }

            JobQueueEntry job = jobOpt.get();

            // Check if still in QUEUED status
            if (job.getStatus() != JobStatus.QUEUED) {
                log.debug("Job {} already picked up by another worker (status: {})", jobId, job.getStatus());
                return false;
            }

            // Update to PROCESSING
            job.setStatus(JobStatus.PROCESSING);
            job.setStartedAt(LocalDateTime.now());
            jobQueueRepository.save(job);

            log.info("Job {} locked and started processing", jobId);
            return true;

        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("Job {} locked by another worker (optimistic lock conflict)", jobId);
            return false;
        }
    }

    /**
     * Mark job as completed
     */
    @Transactional
    public void completeJob(String jobId, Object result, BigDecimal actualCreditsUsed) {
        log.info("Completing job {}: actualCredits={}", jobId, actualCreditsUsed);

        JobQueueEntry job = jobQueueRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        try {
            job.setResultData(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            log.error("Failed to serialize result data for job {}", jobId, e);
        }

        job.setStatus(JobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        job.setActualCreditsUsed(actualCreditsUsed);
        jobQueueRepository.save(job);

        log.info("Job {} completed successfully", jobId);
    }

    /**
     * Mark job as failed (with retry logic)
     */
    @Transactional
    public void failJob(String jobId, String errorMessage, BigDecimal creditsToRefund) {
        log.error("Failing job {}: error={}", jobId, errorMessage);

        JobQueueEntry job = jobQueueRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        job.incrementRetry();

        if (job.canRetry()) {
            // Retry: reset to QUEUED
            log.info("Job {} will be retried (attempt {}/{})", jobId, job.getRetryCount(), job.getMaxRetries());
            job.setStatus(JobStatus.QUEUED);
            job.setStartedAt(null);
            job.setErrorMessage(String.format("Retry %d/%d: %s",
                job.getRetryCount(), job.getMaxRetries(), errorMessage));
        } else {
            // Max retries reached: mark as FAILED
            log.error("Job {} failed permanently after {} retries", jobId, job.getRetryCount());
            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(String.format("Failed after %d retries: %s",
                job.getRetryCount(), errorMessage));

            // Refund credits
            if (creditsToRefund != null && creditsToRefund.compareTo(BigDecimal.ZERO) > 0) {
                try {
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
        }

        jobQueueRepository.save(job);
    }

    /**
     * Get job status
     */
    @Transactional(readOnly = true)
    public Optional<JobQueueEntry> getJob(String jobId) {
        return jobQueueRepository.findById(jobId);
    }

    /**
     * Get user's jobs
     */
    @Transactional(readOnly = true)
    public List<JobQueueEntry> getUserJobs(String userId) {
        return jobQueueRepository.findByUserIdOrderByQueuedAtDesc(userId);
    }

    /**
     * Get queue position for a job
     */
    @Transactional(readOnly = true)
    public int getQueuePosition(String jobId) {
        Optional<JobQueueEntry> jobOpt = jobQueueRepository.findById(jobId);
        if (jobOpt.isEmpty() || jobOpt.get().getStatus() != JobStatus.QUEUED) {
            return 0; // Not in queue
        }

        return jobQueueRepository.countQueuedJobsBefore(jobId) + 1;
    }

    /**
     * Check rate limits
     */
    @Transactional(readOnly = true)
    public boolean canProcessMoreJobs(int maxConcurrent) {
        int processing = jobQueueRepository.countAllProcessingJobs();
        boolean canProcess = processing < maxConcurrent;
        log.debug("Currently processing: {}, max: {}, can process more: {}", processing, maxConcurrent, canProcess);
        return canProcess;
    }

    @Transactional(readOnly = true)
    public boolean userCanProcessMoreJobs(String userId, int maxPerUser) {
        int processing = jobQueueRepository.countProcessingJobsByUser(userId);
        boolean canProcess = processing < maxPerUser;
        log.debug("User {} processing: {}, max: {}, can process more: {}", userId, processing, maxPerUser, canProcess);
        return canProcess;
    }
}
