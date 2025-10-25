package com.resumebuddy.jobsearch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO: Skill Co-occurrence
 * Represents a skill that co-occurs with selected skills in job listings
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Skill that appears together with selected skills in job listings")
public class SkillCooccurrence {

    @Schema(description = "Skill name", example = "Docker")
    private String skillName;

    @Schema(description = "Number of jobs that have this skill along with selected skills", example = "145")
    private Long jobCount;
}
