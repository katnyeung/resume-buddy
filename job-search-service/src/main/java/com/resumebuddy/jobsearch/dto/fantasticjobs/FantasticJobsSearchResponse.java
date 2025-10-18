package com.resumebuddy.jobsearch.dto.fantasticjobs;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DTO: Search Response from Fantastic Jobs LinkedIn API
 * Wrapper for list of job results
 */
@Data
public class FantasticJobsSearchResponse {

    /**
     * Status of the API request
     */
    @JsonProperty("status")
    private String status;

    /**
     * List of job results
     */
    @JsonProperty("data")
    private List<FantasticJobsDto> data;

    /**
     * Total number of results available
     */
    @JsonProperty("total")
    private Integer total;

    /**
     * Number of results in this response
     */
    @JsonProperty("count")
    private Integer count;

    /**
     * Request ID for debugging
     */
    @JsonProperty("request_id")
    private String requestId;

    /**
     * API message (if any)
     */
    @JsonProperty("message")
    private String message;
}
