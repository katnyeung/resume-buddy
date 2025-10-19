package com.resumebuddy.jobsearch.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateJobPostRequest {

    @NotBlank
    private String editedJobPost;

    /**
     * Deal-breaker keywords to highlight in job results (e.g., "SC Clearance", "PhD required")
     */
    private List<String> excludedKeywords;
}
