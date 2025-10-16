package com.resumebuddy.jobsearch.dto.jsearch;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * DTO: JSearch API Response Wrapper
 * Contains search metadata and job results from JSearch (RapidAPI)
 */
@Data
public class JSearchSearchResponse {

    @JsonProperty("status")
    private String status; // "OK" or error status

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("parameters")
    private SearchParameters parameters;

    @JsonProperty("data")
    private List<JSearchJobDto> data;

    /**
     * Nested search parameters object
     */
    @Data
    public static class SearchParameters {
        @JsonProperty("query")
        private String query;

        @JsonProperty("page")
        private Integer page;

        @JsonProperty("num_pages")
        private Integer numPages;

        @JsonProperty("date_posted")
        private String datePosted; // "all", "today", "3days", "week", "month"

        @JsonProperty("country")
        private String country; // e.g., "gb", "us"

        @JsonProperty("language")
        private String language; // e.g., "en"
    }
}
