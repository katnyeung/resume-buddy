package com.resumebuddy.jobsearch.dto.adzuna;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DTO: Adzuna API Search Response Wrapper
 * Maps the top-level response from Adzuna job search API
 */
@Data
public class AdzunaSearchResponse {

    @JsonProperty("results")
    private List<AdzunaJobDto> results;

    @JsonProperty("count")
    private Integer count;

    @JsonProperty("mean")
    private Double mean; // Average salary

    @JsonProperty("__CLASS__")
    private String className;
}
