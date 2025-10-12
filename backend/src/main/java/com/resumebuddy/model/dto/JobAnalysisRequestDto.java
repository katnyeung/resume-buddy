package com.resumebuddy.model.dto;

import lombok.Data;
import lombok.Builder;

/**
 * Request DTO for job analysis.
 *
 * This is the input from the user:
 * - Job title
 * - Company name
 * - Job description
 */
@Data
@Builder
public class JobAnalysisRequestDto {

    private String title;
    private String company;
    private String description;
    private String startDate;  // Optional
    private String endDate;    // Optional
    private String location;   // Optional
    private String resumeId;   // Optional: link back to resume
    private String experienceId;  // Optional: link to ResumeAnalysisExperience
}
