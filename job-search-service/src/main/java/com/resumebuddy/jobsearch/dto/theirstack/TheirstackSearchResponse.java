package com.resumebuddy.jobsearch.dto.theirstack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DTO: Theirstack API Search Response
 * Wrapper for job search results with metadata
 */
@Data
public class TheirstackSearchResponse {

    @JsonProperty("metadata")
    private Metadata metadata;

    @JsonProperty("data")
    private List<TheirstackJobDto> data;

    /**
     * Metadata about search results
     */
    @Data
    public static class Metadata {
        @JsonProperty("total_results")
        private Integer totalResults;

        @JsonProperty("truncated_results")
        private Integer truncatedResults;

        @JsonProperty("truncated_companies")
        private Integer truncatedCompanies;

        @JsonProperty("total_companies")
        private Integer totalCompanies;
    }
}
