package com.resumebuddy.jobsearch.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProfileRequest {

    @NotNull
    private String resumeId;

    @NotEmpty
    private List<String> experienceIds;

    /**
     * Deal-breaker keywords to highlight in job results (e.g., "SC Clearance", "PhD required")
     */
    private List<String> excludedKeywords;
}
