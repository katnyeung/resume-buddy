package com.resumebuddy.jobsearch.dto.fantasticjobs;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO: Individual Job from Fantastic Jobs LinkedIn API (RapidAPI)
 * API: https://rapidapi.com/fantastic-jobs-fantastic-jobs-default/api/linkedin-job-search-api
 *
 * Indexes 20,000+ jobs hourly from LinkedIn
 * Contains jobs from last 7 days (2M+ jobs/week)
 *
 * Response typically includes:
 * - Full job description
 * - Company details (industry, followers, specialties)
 * - Location information
 * - Employment type
 * - Salary data (if available)
 * - Remote work options
 */
@Data
public class FantasticJobsDto {

    /**
     * Unique job ID from LinkedIn
     */
    @JsonProperty("id")
    private String jobId;

    /**
     * Job title
     */
    @JsonProperty("title")
    private String title;

    /**
     * Company/Organization name
     */
    @JsonProperty("organization")
    private String company;

    /**
     * Company LinkedIn URL
     */
    @JsonProperty("organization_url")
    private String companyUrl;

    /**
     * Company ID (not directly in response, derived from slug)
     */
    @JsonProperty("linkedin_org_slug")
    private String companyId;

    /**
     * Company industry (e.g., "Software Development", "Financial Services")
     */
    @JsonProperty("linkedin_org_industry")
    private String industry;

    /**
     * Number of company followers on LinkedIn
     */
    @JsonProperty("linkedin_org_followers")
    private Integer followers;

    /**
     * Company specialties/areas of expertise (array in response)
     */
    @JsonProperty("linkedin_org_specialties")
    private java.util.List<String> specialties;

    /**
     * Company description
     */
    @JsonProperty("linkedin_org_description")
    private String companyDescription;

    /**
     * Job location (derived array, use first element)
     */
    @JsonProperty("locations_derived")
    private java.util.List<String> location;

    /**
     * Job description (full text)
     */
    @JsonProperty("description_text")
    private String description;

    /**
     * Employment type (array in response)
     * Values: FULL_TIME, PART_TIME, CONTRACTOR, TEMPORARY, INTERN, VOLUNTEER, PER_DIEM, OTHER
     */
    @JsonProperty("employment_type")
    private java.util.List<String> employmentType;

    /**
     * Job function (e.g., "Engineering", "Sales")
     */
    @JsonProperty("job_function")
    private String jobFunction;

    /**
     * Seniority level (e.g., "Mid-Senior level", "Entry level")
     */
    @JsonProperty("seniority_level")
    private String seniorityLevel;

    /**
     * Minimum salary
     */
    @JsonProperty("salary_min")
    private Double salaryMin;

    /**
     * Maximum salary
     */
    @JsonProperty("salary_max")
    private Double salaryMax;

    /**
     * Salary currency (e.g., "USD", "GBP")
     */
    @JsonProperty("salary_currency")
    private String salaryCurrency;

    /**
     * Salary period (e.g., "YEARLY", "MONTHLY")
     */
    @JsonProperty("salary_period")
    private String salaryPeriod;

    /**
     * Job posted date (ISO 8601 format)
     */
    @JsonProperty("date_posted")
    private String postedDate;

    /**
     * Job created timestamp (ISO format)
     */
    @JsonProperty("date_created")
    private String postedAt;

    /**
     * Job URL on LinkedIn
     */
    @JsonProperty("url")
    private String jobUrl;

    /**
     * Direct apply link
     */
    @JsonProperty("external_apply_url")
    private String applyLink;

    /**
     * Remote work option (derived field)
     */
    @JsonProperty("remote_derived")
    private Boolean remote;

    /**
     * Number of applicants
     */
    @JsonProperty("applicants")
    private Integer applicants;

    /**
     * Job views count
     */
    @JsonProperty("views")
    private Integer views;

    /**
     * Experience level required
     */
    @JsonProperty("experience_level")
    private String experienceLevel;

    /**
     * Education level required
     */
    @JsonProperty("education")
    private String education;

    /**
     * Required skills (comma-separated or array)
     */
    @JsonProperty("skills")
    private String skills;

    /**
     * Job type (e.g., "Full-time", "Contract")
     */
    @JsonProperty("job_type")
    private String jobType;

    /**
     * Company size range
     */
    @JsonProperty("company_size")
    private String companySize;

    /**
     * City
     */
    @JsonProperty("city")
    private String city;

    /**
     * State/Region
     */
    @JsonProperty("state")
    private String state;

    /**
     * Country
     */
    @JsonProperty("country")
    private String country;

    /**
     * Latitude
     */
    @JsonProperty("latitude")
    private Double latitude;

    /**
     * Longitude
     */
    @JsonProperty("longitude")
    private Double longitude;
}
