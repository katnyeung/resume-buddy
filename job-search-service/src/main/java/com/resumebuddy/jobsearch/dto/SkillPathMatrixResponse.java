package com.resumebuddy.jobsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for skill path matrix visualization
 * Shows co-occurrence between user's existing skills and top market gap skills
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillPathMatrixResponse {
    /**
     * User's existing skills (row labels)
     */
    private List<String> userSkills;

    /**
     * Top market skills that user doesn't have (column labels)
     */
    private List<String> marketSkills;

    /**
     * Co-occurrence matrix: userSkill -> marketSkill -> count
     * matrix[userSkill][marketSkill] = number of jobs requiring both skills
     */
    private Map<String, Map<String, Long>> cooccurrenceMatrix;

    /**
     * Maximum co-occurrence value in the matrix (for color scaling)
     */
    private Long maxCooccurrence;
}
