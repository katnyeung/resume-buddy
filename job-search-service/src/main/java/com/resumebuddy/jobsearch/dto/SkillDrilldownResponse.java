package com.resumebuddy.jobsearch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO: Skill Drilldown Response
 * Contains Neo4j query results for skill-based job discovery
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response for skill-based job drilldown query")
public class SkillDrilldownResponse {

    @Schema(description = "Total number of jobs matching ALL selected skills", example = "87")
    private Long totalJobs;

    @Schema(description = "List of job listing IDs from Neo4j", example = "[\"uuid1\", \"uuid2\"]")
    private List<String> jobIds;

    @Schema(description = "Date distribution (date string -> job count)", example = "{\"2025-01-01\": 12, \"2025-01-08\": 18}")
    private Map<String, Long> dateDistribution;

    @Schema(description = "Skills that co-occur with selected skills (sorted by count)")
    private List<SkillCooccurrence> relatedSkills;
}
