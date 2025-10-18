package com.resumebuddy.jobsearch.dto.analysis;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO: Job Analysis Response
 * Results from batch skill extraction process
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Results from job analysis and skill extraction")
public class JobAnalysisResponse {

    @Schema(description = "Overall status of the analysis", example = "completed")
    private String status;

    @Schema(description = "Total number of jobs found for analysis", example = "47")
    private Integer totalJobs;

    @Schema(description = "Number of jobs successfully analyzed", example = "47")
    private Integer analyzed;

    @Schema(description = "Number of jobs skipped (already analyzed or no description)", example = "3")
    private Integer skipped;

    @Schema(description = "Number of jobs that failed to process", example = "0")
    private Integer errors;

    @Schema(description = "Total number of skills extracted across all jobs", example = "312")
    private Integer totalSkillsExtracted;

    @Schema(description = "Average number of skills per job", example = "6.6")
    private Double avgSkillsPerJob;

    @Schema(description = "Total processing time in milliseconds", example = "12456")
    private Long processingTimeMs;

    @Schema(description = "Estimated cost of LLM API calls in USD", example = "0.005000")
    private String estimatedCost;

    @Schema(description = "List of failed job IDs (for debugging)")
    private List<String> failedJobIds = new ArrayList<>();

    @Schema(description = "Additional message or error details")
    private String message;

    /**
     * Constructor for successful completion
     */
    public JobAnalysisResponse(Integer totalJobs, Integer analyzed, Integer skipped, Integer errors,
                               Integer totalSkillsExtracted, Long processingTimeMs, String estimatedCost) {
        this.status = errors == 0 ? "completed" : "completed_with_errors";
        this.totalJobs = totalJobs;
        this.analyzed = analyzed;
        this.skipped = skipped;
        this.errors = errors;
        this.totalSkillsExtracted = totalSkillsExtracted;
        this.avgSkillsPerJob = analyzed > 0 ? (double) totalSkillsExtracted / analyzed : 0.0;
        this.processingTimeMs = processingTimeMs;
        this.estimatedCost = estimatedCost;
        this.message = String.format("Successfully analyzed %d/%d jobs, extracted %d skills",
                analyzed, totalJobs, totalSkillsExtracted);
    }
}
