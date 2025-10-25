package com.resumebuddy.jobsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for skill path drilldown
 * Finds jobs matching ALL skills (AND logic) + related skills
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillPathRequest {
    /**
     * Selected skills in the path (AND logic)
     */
    private List<String> skills;

    /**
     * Maximum number of related skills to return (default: 20)
     */
    private Integer maxRelatedSkills = 20;
}
