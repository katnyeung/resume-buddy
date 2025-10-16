package com.resumebuddy.jobsearch.dto.jsearch;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * DTO: Individual Job from JSearch (RapidAPI) API
 * Maps job listing fields from JSearch search results
 *
 * JSearch aggregates jobs from Google Jobs, Indeed, LinkedIn, Glassdoor, etc.
 * Documentation: https://rapidapi.com/letscrape-6bRBa3QguO5/api/jsearch
 */
@Data
public class JSearchJobDto {

    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("job_title")
    private String jobTitle;

    @JsonProperty("employer_name")
    private String employerName;

    @JsonProperty("employer_logo")
    private String employerLogo;

    @JsonProperty("employer_website")
    private String employerWebsite;

    @JsonProperty("job_publisher")
    private String jobPublisher;

    @JsonProperty("job_employment_type")
    private String jobEmploymentType; // e.g., "Full-time", "Contractor"

    @JsonProperty("job_employment_types")
    private List<String> jobEmploymentTypes; // e.g., ["FULLTIME", "CONTRACTOR"]

    @JsonProperty("job_apply_link")
    private String jobApplyLink;

    @JsonProperty("job_apply_is_direct")
    private Boolean jobApplyIsDirect;

    @JsonProperty("apply_options")
    private List<ApplyOption> applyOptions;

    @JsonProperty("job_description")
    private String jobDescription;

    @JsonProperty("job_is_remote")
    private Boolean jobIsRemote;

    @JsonProperty("job_posted_at")
    private String jobPostedAt; // Human-readable, e.g., "1 day ago"

    @JsonProperty("job_posted_at_timestamp")
    private Long jobPostedAtTimestamp;

    @JsonProperty("job_posted_at_datetime_utc")
    private String jobPostedAtDatetimeUtc; // ISO 8601 format

    @JsonProperty("job_location")
    private String jobLocation; // e.g., "London"

    @JsonProperty("job_city")
    private String jobCity;

    @JsonProperty("job_state")
    private String jobState;

    @JsonProperty("job_country")
    private String jobCountry; // e.g., "GB"

    @JsonProperty("job_latitude")
    private Double jobLatitude;

    @JsonProperty("job_longitude")
    private Double jobLongitude;

    @JsonProperty("job_benefits")
    private List<String> jobBenefits;

    @JsonProperty("job_google_link")
    private String jobGoogleLink;

    @JsonProperty("job_salary")
    private String jobSalary; // Human-readable, e.g., "£80,000 - £95,000"

    @JsonProperty("job_min_salary")
    private Double jobMinSalary;

    @JsonProperty("job_max_salary")
    private Double jobMaxSalary;

    @JsonProperty("job_salary_period")
    private String jobSalaryPeriod; // "YEAR", "MONTH", "HOUR", etc.

    @JsonProperty("job_highlights")
    private Map<String, Object> jobHighlights;

    @JsonProperty("job_onet_soc")
    private String jobOnetSoc;

    @JsonProperty("job_onet_job_zone")
    private String jobOnetJobZone;

    /**
     * Nested apply option object
     */
    @Data
    public static class ApplyOption {
        @JsonProperty("publisher")
        private String publisher;

        @JsonProperty("apply_link")
        private String applyLink;

        @JsonProperty("is_direct")
        private Boolean isDirect;
    }
}
