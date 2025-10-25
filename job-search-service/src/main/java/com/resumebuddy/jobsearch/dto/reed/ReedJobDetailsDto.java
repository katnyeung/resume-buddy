package com.resumebuddy.jobsearch.dto.reed;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO: Full Job Details from Reed.co.uk API
 * Returned from GET /api/1.0/jobs/{jobId} endpoint
 * Contains the FULL job description (not truncated like search results)
 *
 * Reed API Documentation: https://www.reed.co.uk/developers/Jobseeker
 */
@Data
public class ReedJobDetailsDto {

    @JsonProperty("jobId")
    private Long jobId;

    @JsonProperty("employerId")
    private Long employerId;

    @JsonProperty("employerName")
    private String employerName;

    @JsonProperty("employerProfileId")
    private Long employerProfileId;

    @JsonProperty("employerProfileName")
    private String employerProfileName;

    @JsonProperty("jobTitle")
    private String jobTitle;

    @JsonProperty("locationName")
    private String locationName;

    @JsonProperty("minimumSalary")
    private Double minimumSalary;

    @JsonProperty("maximumSalary")
    private Double maximumSalary;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("expirationDate")
    private String expirationDate;

    @JsonProperty("date")
    private String date; // Posted date in dd/MM/yyyy format

    /**
     * FULL job description (not truncated)
     * This is the key difference from ReedJobDto (search results)
     */
    @JsonProperty("jobDescription")
    private String jobDescription;

    @JsonProperty("applications")
    private Integer applications;

    @JsonProperty("jobUrl")
    private String jobUrl;

    @JsonProperty("partTime")
    private Boolean partTime;

    @JsonProperty("fullTime")
    private Boolean fullTime;

    @JsonProperty("contractType")
    private String contractType; // e.g., "Permanent", "Contract", "Temporary"
}
