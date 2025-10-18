package com.resumebuddy.jobsearch.dto.analysis;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO: Job Analysis Request
 * Parameters for triggering batch skill extraction from job listings
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request parameters for job analysis and skill extraction")
public class JobAnalysisRequest {

    @Schema(
            description = "Number of jobs to process in each batch (1-100)",
            example = "20",
            defaultValue = "20"
    )
    @Min(1)
    @Max(100)
    private Integer batchSize = 20;

    @Schema(
            description = "Force re-analysis of already analyzed jobs",
            example = "false",
            defaultValue = "false"
    )
    private Boolean forceReanalyze = false;

    @Schema(
            description = "Only analyze jobs fetched after this many days ago (optional)",
            example = "30"
    )
    @Min(1)
    @Max(365)
    private Integer maxDaysOld;

    @Schema(
            description = "Maximum total number of jobs to analyze (useful for testing, optional)",
            example = "10"
    )
    @Min(1)
    @Max(10000)
    private Integer maxJobs;
}
