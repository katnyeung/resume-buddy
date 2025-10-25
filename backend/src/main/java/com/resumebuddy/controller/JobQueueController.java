package com.resumebuddy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.model.JobQueueEntry;
import com.resumebuddy.model.JobQueueEntry.JobType;
import com.resumebuddy.model.dto.EnqueueJobResponseDto;
import com.resumebuddy.model.dto.JobQueueStatusDto;
import com.resumebuddy.service.JobQueueService;
import com.resumebuddy.service.UserCreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@Slf4j
@RequiredArgsConstructor
public class JobQueueController {

    private final JobQueueService jobQueueService;
    private final ObjectMapper objectMapper;

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

            JobQueueEntry job = jobQueueService.enqueueJob(userId, JobType.RESUME_ANALYSIS, params, priority);

            EnqueueJobResponseDto response = new EnqueueJobResponseDto();
            response.setJobId(job.getId());
            response.setJobType(job.getJobType());
            response.setStatus(job.getStatus().name());
            response.setEstimatedCredits(job.getEstimatedCredits());
            response.setQueuePosition(jobQueueService.getQueuePosition(job.getId()));
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

            JobQueueEntry job = jobQueueService.enqueueJob(userId, JobType.JOB_EXPERIENCE_ANALYSIS, params, priority);

            EnqueueJobResponseDto response = new EnqueueJobResponseDto();
            response.setJobId(job.getId());
            response.setJobType(job.getJobType());
            response.setStatus(job.getStatus().name());
            response.setEstimatedCredits(job.getEstimatedCredits());
            response.setQueuePosition(jobQueueService.getQueuePosition(job.getId()));
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
            JobQueueEntry job = jobQueueService.getJob(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

            JobQueueStatusDto status = new JobQueueStatusDto();
            status.setJobId(job.getId());
            status.setJobType(job.getJobType());
            status.setStatus(job.getStatus());
            status.setQueuePosition(jobQueueService.getQueuePosition(jobId));
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
            return jobQueueService.findActiveJobForExperience(resumeId, experienceId)
                .map(job -> ResponseEntity.ok(Map.of("jobId", job.getId())))
                .orElse(ResponseEntity.ok(Map.of()));
        } catch (Exception e) {
            log.error("Failed to check active job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private EnqueueJobResponseDto createErrorResponse(String message) {
        EnqueueJobResponseDto response = new EnqueueJobResponseDto();
        response.setMessage("Error: " + message);
        return response;
    }
}
