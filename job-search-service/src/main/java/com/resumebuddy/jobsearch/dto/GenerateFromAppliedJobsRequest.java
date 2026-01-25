package com.resumebuddy.jobsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for generating mock job requirements from applied jobs
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateFromAppliedJobsRequest {

    /**
     * Optional custom name for the new version
     * If not provided, defaults to "From X Applied Jobs"
     */
    private String versionName;
}
