package com.resumebuddy.jobsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO: Job Matching Results
 * Contains comprehensive matching results with job details and skill analysis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobMatchingResultResponse {

    private String profileId;
    private int totalMatches;
    private String profileSummary; // Brief summary of the profile's job post
    private List<JobMatchResponse> matches;

    /**
     * Metadata about the search
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchMetadata {
        private int requestedTopK;
        private int actualResults;
        private String searchStrategy; // e.g., "vector_similarity + skill_matching"
    }

    private SearchMetadata metadata;
}
