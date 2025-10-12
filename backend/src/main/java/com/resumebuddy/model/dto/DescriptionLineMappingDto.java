package com.resumebuddy.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * DTO for description line to O*NET activity/task mapping response from LLM
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DescriptionLineMappingDto {

    private List<LineMappingDto> lineMappings;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineMappingDto {
        private Integer sequence;
        private String text;
        private List<ActivityMappingDto> activities;
        private List<TaskMappingDto> tasks;
        private String impactMetrics;
        private Boolean hasQuantifiableImpact;
        private String impactLevel;  // Low/Medium/High/Critical
        private String scope;  // Individual/Team/Department/Company/Industry
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityMappingDto {
        private String activityName;
        private String activityId;
        private Double confidence;
        private String reasoning;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskMappingDto {
        private String taskName;
        private String taskId;
        private Double confidence;
        private String reasoning;
    }
}
