package com.resumebuddy.jobsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for job search profile version data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileVersionDto {

    private String id;
    private String profileId;
    private Integer versionNumber;
    private String versionName;
    private String sourceType;
    private Boolean isActive;
    private LocalDateTime createdAt;

    /**
     * The mock job requirements text
     * Only included when fetching a specific version or when needed for editor
     */
    private String generatedJobPost;
}
