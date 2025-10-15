package com.resumebuddy.jobsearch.dto.reed;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;

/**
 * DTO: Individual Job from Reed.co.uk API
 * Maps job listing fields from Reed job search API
 *
 * Note: Reed returns dates as strings in "dd/MM/yyyy" format
 */
@Data
public class ReedJobDto {

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

    // Reed returns dates as strings like "12/11/2025" (dd/MM/yyyy)
    @JsonProperty("expirationDate")
    private String expirationDate;

    @JsonProperty("date")
    private String date; // Posted date in dd/MM/yyyy format

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
}
