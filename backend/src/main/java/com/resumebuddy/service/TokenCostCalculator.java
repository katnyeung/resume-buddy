package com.resumebuddy.service;

import com.resumebuddy.model.JobQueueEntry.JobType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Slf4j
public class TokenCostCalculator {

    @Value("${app.token-costs.resume-analysis:50}")
    private int resumeAnalysisCost;

    @Value("${app.token-costs.job-experience-analysis:50}")
    private int jobExperienceAnalysisCost;

    @Value("${app.token-costs.job-profile-generation:25}")
    private int jobProfileGenerationCost;

    /**
     * Calculate estimated credits for a job type
     */
    public BigDecimal calculateCost(JobType jobType) {
        int cost = switch (jobType) {
            case RESUME_ANALYSIS -> resumeAnalysisCost;
            case JOB_EXPERIENCE_ANALYSIS -> jobExperienceAnalysisCost;
            case JOB_PROFILE_GENERATION -> jobProfileGenerationCost;
        };

        log.debug("Calculated cost for {}: {} credits", jobType, cost);
        return new BigDecimal(cost);
    }

    /**
     * Get human-readable cost description
     */
    public String getCostDescription(JobType jobType) {
        BigDecimal cost = calculateCost(jobType);
        return switch (jobType) {
            case RESUME_ANALYSIS -> String.format("Full resume analysis (%s credits)", cost);
            case JOB_EXPERIENCE_ANALYSIS -> String.format("Job experience analysis (%s credits)", cost);
            case JOB_PROFILE_GENERATION -> String.format("Job profile generation (%s credits)", cost);
        };
    }
}
