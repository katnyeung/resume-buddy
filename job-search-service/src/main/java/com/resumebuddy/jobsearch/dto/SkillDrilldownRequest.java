package com.resumebuddy.jobsearch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO: Skill Drilldown Request
 * Request to find jobs by multiple skills using Neo4j graph
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to find jobs by skills using Neo4j")
public class SkillDrilldownRequest {

    @NotEmpty(message = "At least one skill is required")
    @Size(max = 10, message = "Maximum 10 skills allowed")
    @Schema(description = "List of skill names to search for (AND logic)", example = "[\"Java\", \"Docker\", \"Kubernetes\"]")
    private List<String> skills;

    @Schema(description = "Maximum days old for job postings (0 = all time)", example = "30", defaultValue = "30")
    private Integer maxDaysOld = 30;
}
