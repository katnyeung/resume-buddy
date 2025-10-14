package com.resumebuddy.jobsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for generated job profile with metadata
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedJobProfileDto {
    private String jobPost;
    private String location;
    private String experienceLevel;
    private Map<String, Integer> skillProficiencies; // skill name -> score (0-100)
}
