package com.resumebuddy.jobsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for skill co-occurrence heatmap
 * Shows how often top skills appear together in job listings
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillHeatmapResponse {
    /**
     * Ordered list of skill names (same order for rows and columns)
     */
    private List<String> skillNames;

    /**
     * Co-occurrence matrix: skill1 -> skill2 -> count
     * Symmetric matrix where matrix[i][j] = number of jobs requiring both skills[i] and skills[j]
     */
    private Map<String, Map<String, Long>> cooccurrenceMatrix;

    /**
     * Maximum co-occurrence value in the matrix (for color scaling)
     */
    private Long maxCooccurrence;

    /**
     * Total number of jobs analyzed
     */
    private Long totalJobs;
}
