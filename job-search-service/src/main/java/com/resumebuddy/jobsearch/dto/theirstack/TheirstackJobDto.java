package com.resumebuddy.jobsearch.dto.theirstack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DTO: Individual Job from Theirstack API
 * Maps job listing fields from Theirstack search results
 */
@Data
public class TheirstackJobDto {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("job_title")
    private String jobTitle;

    @JsonProperty("url")
    private String url;  // Theirstack aggregator URL

    @JsonProperty("date_posted")
    private String datePosted;  // "2025-10-18" format

    @JsonProperty("has_blurred_data")
    private Boolean hasBlurredData;

    @JsonProperty("company")
    private String company;

    @JsonProperty("final_url")
    private String finalUrl;  // nullable

    @JsonProperty("source_url")
    private String sourceUrl;  // Actual job board URL (LinkedIn, Indeed, etc.)

    // Location fields
    @JsonProperty("location")
    private String location;  // "Greater London, England"

    @JsonProperty("short_location")
    private String shortLocation;

    @JsonProperty("long_location")
    private String longLocation;

    @JsonProperty("state_code")
    private String stateCode;  // "ENG"

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("postal_code")
    private String postalCode;

    // Job type flags
    @JsonProperty("remote")
    private Boolean remote;

    @JsonProperty("hybrid")
    private Boolean hybrid;

    // Salary fields (often null)
    @JsonProperty("salary_string")
    private String salaryString;

    @JsonProperty("min_annual_salary")
    private Double minAnnualSalary;

    @JsonProperty("min_annual_salary_usd")
    private Double minAnnualSalaryUsd;

    @JsonProperty("max_annual_salary")
    private Double maxAnnualSalary;

    @JsonProperty("max_annual_salary_usd")
    private Double maxAnnualSalaryUsd;

    @JsonProperty("avg_annual_salary_usd")
    private Double avgAnnualSalaryUsd;

    @JsonProperty("salary_currency")
    private String salaryCurrency;

    // Country information
    @JsonProperty("country")
    private String country;  // "United Kingdom"

    @JsonProperty("country_codes")
    private List<String> countryCodes;

    @JsonProperty("countries")
    private List<String> countries;

    // Job description (full HTML/text description)
    @JsonProperty("description")
    private String description;

    // Employment details
    @JsonProperty("employment_statuses")
    private List<String> employmentStatuses;  // ["full_time", "part_time", etc.]

    @JsonProperty("seniority")
    private String seniority;  // "entry_level", "mid_level", "senior", "c_level", etc.

    // Technology/skills metadata
    @JsonProperty("technology_slugs")
    private List<String> technologySlugs;  // ["google-firebase", "postgresql", "jira"]

    @JsonProperty("easy_apply")
    private Boolean easyApply;

    @JsonProperty("reposted")
    private Boolean reposted;

    @JsonProperty("date_reposted")
    private String dateReposted;
}
