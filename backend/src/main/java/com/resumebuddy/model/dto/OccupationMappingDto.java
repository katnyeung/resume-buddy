package com.resumebuddy.model.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO for occupation mapping from LLM.
 *
 * Represents ONE occupation that the LLM identified for a job.
 * In step 1, we only use the primary (highest confidence) occupation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OccupationMappingDto {

    private String socCode;        // e.g., "15-1252.00"
    private String title;          // e.g., "Software Developers"
    private Double confidence;     // 0.0-1.0
    private Boolean isPrimary;     // true if this is the main occupation
    private String reasoning;      // Why the LLM chose this occupation
}
