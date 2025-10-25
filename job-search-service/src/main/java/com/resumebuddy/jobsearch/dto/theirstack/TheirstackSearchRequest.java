package com.resumebuddy.jobsearch.dto.theirstack;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO: Theirstack Job Search Request (POST body)
 * Used for POST /v1/jobs/search endpoint
 * Note: Null fields are excluded from JSON to avoid API validation errors
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TheirstackSearchRequest {

    @JsonProperty("page")
    private Integer page;  // 0-indexed pagination

    @JsonProperty("limit")
    private Integer limit;  // Results per page (e.g., 25, 50)

    @JsonProperty("job_title_or")
    private List<String> jobTitleOr;  // Job title keywords (OR logic)

    @JsonProperty("posted_at_max_age_days")
    private Integer postedAtMaxAgeDays;  // Filter: posted within last N days

    @JsonProperty("company_country_code_or")
    private List<String> companyCountryCodeOr;  // Country codes (e.g., ["GB"], ["US"])

    @JsonProperty("location_or")
    private List<String> locationOr;  // Optional: City/region filter (e.g., ["London"])

    @JsonProperty("remote")
    private Boolean remote;  // Optional: filter remote jobs

    @JsonProperty("hybrid")
    private Boolean hybrid;  // Optional: filter hybrid jobs
}
