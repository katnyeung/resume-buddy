package com.resumebuddy.jobsearch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO: Jobs by IDs Request
 * Request to fetch job details by list of IDs
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to fetch job listings by IDs")
public class JobsByIdsRequest {

    @NotEmpty(message = "At least one job ID is required")
    @Size(max = 500, message = "Maximum 500 job IDs allowed per request")
    @Schema(description = "List of job listing IDs from Neo4j", example = "[\"uuid1\", \"uuid2\", \"uuid3\"]")
    private List<String> jobIds;

    @Schema(description = "Maximum days old for filtering results (0 = no filter)", example = "30", defaultValue = "0")
    private Integer maxDaysOld = 0;
}
