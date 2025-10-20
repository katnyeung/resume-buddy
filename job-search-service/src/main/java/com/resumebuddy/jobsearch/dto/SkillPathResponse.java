package com.resumebuddy.jobsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for skill path drilldown
 * Returns matching job count and related skills for the selected path
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillPathResponse {
    /**
     * Skills in the selected path
     */
    private List<String> selectedSkills;

    /**
     * Number of jobs matching ALL selected skills (AND logic)
     */
    private int jobCount;

    /**
     * Related skills that co-occur with selected skills
     * Key: skill name, Value: number of jobs with this skill
     */
    private Map<String, Long> relatedSkills;
}
