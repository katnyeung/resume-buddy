package com.resumebuddy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.model.JobQueueEntry;
import com.resumebuddy.model.JobQueueEntry.JobType;
import com.resumebuddy.model.dto.LineAnalysisDto;
import com.resumebuddy.model.dto.JobAnalysisResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Job Executor - Delegates to existing analysis services
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobExecutor {

    private final ResumeAnalysisService resumeAnalysisService;
    private final JobAnalysisService jobAnalysisService;
    private final ObjectMapper objectMapper;
    // Note: Job profile generation will be added when integrating job-search-service

    /**
     * Execute a job based on its type
     * @return Result object to be stored in job_queue.result_data
     */
    public Object executeJob(JobQueueEntry job) throws Exception {
        log.info("Executing job {}: type={}", job.getId(), job.getJobType());

        Map<String, Object> inputParams = objectMapper.readValue(
            job.getInputParams(),
            objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
        );

        return switch (job.getJobType()) {
            case RESUME_ANALYSIS -> executeResumeAnalysis(inputParams);
            case JOB_EXPERIENCE_ANALYSIS -> executeJobExperienceAnalysis(inputParams);
            case JOB_PROFILE_GENERATION -> executeJobProfileGeneration(inputParams);
        };
    }

    private Object executeResumeAnalysis(Map<String, Object> params) {
        String resumeId = (String) params.get("resumeId");
        log.info("Executing resume analysis for resumeId: {}", resumeId);

        List<LineAnalysisDto> result = resumeAnalysisService.analyzeResume(resumeId);

        // Return structured response
        return Map.of(
            "success", true,
            "resumeId", resumeId,
            "linesAnalyzed", result.size(),
            "message", "Resume analyzed successfully"
        );
    }

    private Object executeJobExperienceAnalysis(Map<String, Object> params) {
        String resumeId = (String) params.get("resumeId");
        String experienceId = (String) params.get("experienceId");
        log.info("Executing job experience analysis for resumeId: {}, experienceId: {}", resumeId, experienceId);

        JobAnalysisResultDto result = jobAnalysisService.analyzeJob(resumeId, experienceId);

        // Return reference to analysis (full result stored in job_analysis table)
        return Map.of(
            "success", true,
            "resumeId", resumeId,
            "experienceId", experienceId,
            "analysisId", result.getId() != null ? result.getId() : "generated",
            "message", "Job experience analyzed successfully"
        );
    }

    private Object executeJobProfileGeneration(Map<String, Object> params) {
        // TODO: Integrate with job-search-service when ready
        log.warn("Job profile generation not yet implemented in executor");
        throw new UnsupportedOperationException("Job profile generation not yet integrated");
    }

    /**
     * Calculate actual credits used (can be refined based on actual LLM usage)
     */
    public BigDecimal calculateActualCredits(JobQueueEntry job, Object result) {
        // For MVP, actual credits = estimated credits
        // In production, you could track actual LLM token usage and calculate precise cost
        return job.getEstimatedCredits();
    }
}
