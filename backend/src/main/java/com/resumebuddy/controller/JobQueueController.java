package com.resumebuddy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.model.JobQueueEntry;
import com.resumebuddy.model.JobQueueEntry.JobType;
import com.resumebuddy.model.dto.EnqueueJobResponseDto;
import com.resumebuddy.model.dto.JobQueueStatusDto;
import com.resumebuddy.service.JobQueueService;
import com.resumebuddy.service.RedisJobQueueService;
import com.resumebuddy.service.UserCreditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@Slf4j
public class JobQueueController {

    private final JobQueueService postgresQueueService;
    private final RedisJobQueueService redisQueueService;
    private final ObjectMapper objectMapper;

    @Value("${app.job-queue.storage:postgres}")
    private String storageType;

    public JobQueueController(
        JobQueueService postgresQueueService,
        @Autowired(required = false) RedisJobQueueService redisQueueService,
        ObjectMapper objectMapper
    ) {
        this.postgresQueueService = postgresQueueService;
        this.redisQueueService = redisQueueService;
        this.objectMapper = objectMapper;
    }

    // Helper methods to abstract storage type
    private JobQueueEntry enqueueJob(String userId, JobType jobType, Map<String, Object> params, Integer priority) {
        return "redis".equalsIgnoreCase(storageType) && redisQueueService != null
            ? redisQueueService.enqueueJob(userId, jobType, params, priority)
            : postgresQueueService.enqueueJob(userId, jobType, params, priority);
    }

    private java.util.Optional<JobQueueEntry> getJob(String jobId) {
        return "redis".equalsIgnoreCase(storageType) && redisQueueService != null
            ? redisQueueService.getJob(jobId)
            : postgresQueueService.getJob(jobId);
    }

    private int getQueuePosition(String jobId) {
        return "redis".equalsIgnoreCase(storageType) && redisQueueService != null
            ? redisQueueService.getQueuePosition(jobId)
            : postgresQueueService.getQueuePosition(jobId);
    }

    private java.util.Optional<JobQueueEntry> findActiveJobForExperience(String resumeId, String experienceId) {
        return "redis".equalsIgnoreCase(storageType) && redisQueueService != null
            ? redisQueueService.findActiveJobForExperience(resumeId, experienceId)
            : postgresQueueService.findActiveJobForExperience(resumeId, experienceId);
    }

    private java.util.Optional<JobQueueEntry> findActiveJobForResume(String resumeId) {
        return "redis".equalsIgnoreCase(storageType) && redisQueueService != null
            ? redisQueueService.findActiveJobForResume(resumeId)
            : postgresQueueService.findActiveJobForResume(resumeId);
    }

    /**
     * Enqueue resume analysis job
     */
    @PostMapping("/resumes/{resumeId}/analyze/async")
    public ResponseEntity<EnqueueJobResponseDto> enqueueResumeAnalysis(
            @PathVariable String resumeId,
            @RequestParam(required = false) Integer priority,
            @RequestHeader(value = "X-User-Id", defaultValue = "default_user") String userId) {

        try {
            Map<String, Object> params = Map.of("resumeId", resumeId);

            JobQueueEntry job = enqueueJob(userId, JobType.RESUME_ANALYSIS, params, priority);

            EnqueueJobResponseDto response = new EnqueueJobResponseDto();
            response.setJobId(job.getId());
            response.setJobType(job.getJobType());
            response.setStatus(job.getStatus().name());
            response.setEstimatedCredits(job.getEstimatedCredits());
            response.setQueuePosition(getQueuePosition(job.getId()));
            response.setMessage("Resume analysis queued successfully");

            return ResponseEntity.ok(response);

        } catch (UserCreditService.InsufficientCreditsException e) {
            log.error("Insufficient credits for resume analysis", e);
            EnqueueJobResponseDto response = new EnqueueJobResponseDto();
            response.setMessage(e.getMessage());
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response);

        } catch (Exception e) {
            log.error("Failed to enqueue resume analysis", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse(e.getMessage()));
        }
    }

    /**
     * Enqueue job experience analysis job
     */
    @PostMapping("/resumes/{resumeId}/experiences/{experienceId}/analyze/async")
    public ResponseEntity<EnqueueJobResponseDto> enqueueJobExperienceAnalysis(
            @PathVariable String resumeId,
            @PathVariable String experienceId,
            @RequestParam(required = false) Integer priority,
            @RequestHeader(value = "X-User-Id", defaultValue = "default_user") String userId) {

        try {
            Map<String, Object> params = Map.of(
                "resumeId", resumeId,
                "experienceId", experienceId
            );

            JobQueueEntry job = enqueueJob(userId, JobType.JOB_EXPERIENCE_ANALYSIS, params, priority);

            EnqueueJobResponseDto response = new EnqueueJobResponseDto();
            response.setJobId(job.getId());
            response.setJobType(job.getJobType());
            response.setStatus(job.getStatus().name());
            response.setEstimatedCredits(job.getEstimatedCredits());
            response.setQueuePosition(getQueuePosition(job.getId()));
            response.setMessage("Job experience analysis queued successfully");

            return ResponseEntity.ok(response);

        } catch (UserCreditService.InsufficientCreditsException e) {
            log.error("Insufficient credits for job experience analysis", e);
            EnqueueJobResponseDto response = new EnqueueJobResponseDto();
            response.setMessage(e.getMessage());
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response);

        } catch (Exception e) {
            log.error("Failed to enqueue job experience analysis", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse(e.getMessage()));
        }
    }

    /**
     * Get job status
     */
    @GetMapping("/{jobId}/status")
    public ResponseEntity<JobQueueStatusDto> getJobStatus(@PathVariable String jobId) {
        try {
            JobQueueEntry job = getJob(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

            JobQueueStatusDto status = new JobQueueStatusDto();
            status.setJobId(job.getId());
            status.setJobType(job.getJobType());
            status.setStatus(job.getStatus());
            status.setQueuePosition(getQueuePosition(jobId));
            status.setEstimatedCredits(job.getEstimatedCredits());
            status.setActualCreditsUsed(job.getActualCreditsUsed());
            status.setQueuedAt(job.getQueuedAt());
            status.setStartedAt(job.getStartedAt());
            status.setCompletedAt(job.getCompletedAt());
            status.setErrorMessage(job.getErrorMessage());
            status.setRetryCount(job.getRetryCount());
            status.setMaxRetries(job.getMaxRetries());

            // Include result if completed
            if (job.getStatus() == JobQueueEntry.JobStatus.COMPLETED && job.getResultData() != null) {
                try {
                    Object result = objectMapper.readValue(job.getResultData(), Object.class);
                    status.setResult(result);
                } catch (Exception e) {
                    log.error("Failed to parse result data", e);
                }
            }

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Failed to get job status", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Check if there's an active job for a specific resume + experience combination
     */
    @GetMapping("/check-active")
    public ResponseEntity<Map<String, String>> checkActiveJob(
            @RequestParam String resumeId,
            @RequestParam String experienceId) {
        try {
            return findActiveJobForExperience(resumeId, experienceId)
                .map(job -> ResponseEntity.ok(Map.of("jobId", job.getId())))
                .orElse(ResponseEntity.ok(Map.of()));
        } catch (Exception e) {
            log.error("Failed to check active job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Check if there's an active resume analysis job for a specific resume
     */
    @GetMapping("/resumes/{resumeId}/check-active")
    public ResponseEntity<Map<String, String>> checkActiveResumeAnalysisJob(
            @PathVariable String resumeId) {
        try {
            return findActiveJobForResume(resumeId)
                .map(job -> ResponseEntity.ok(Map.of("jobId", job.getId())))
                .orElse(ResponseEntity.ok(Map.of()));
        } catch (Exception e) {
            log.error("Failed to check active resume analysis job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private EnqueueJobResponseDto createErrorResponse(String message) {
        EnqueueJobResponseDto response = new EnqueueJobResponseDto();
        response.setMessage("Error: " + message);
        return response;
    }
}
