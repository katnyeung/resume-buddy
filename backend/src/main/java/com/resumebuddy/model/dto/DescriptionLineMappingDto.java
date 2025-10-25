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
    private List<SkillToTaskMappingDto> skillToTaskMappings;

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
        private RecruiterInsightsDto recruiterInsights;
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillToTaskMappingDto {
        private String skillName;
        private String taskName;
        private String taskId;
        private Double confidence;
        private String reasoning;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecruiterInsightsDto {
        private ScanOptimizationDto scanOptimization;
        private MicroStarsBalanceDto microStarsBalance;
        private Integer concisenessScore;
        private String concisenessReasoning;
        private String scanSurvivability;  // "YES", "MAYBE", "NO"
        private List<SignalDto> strongSignals;
        private List<String> potentialQuestions;
        private List<String> starsAnalysis;
        private Integer recruiterAppealScore;
        private String appealReasoning;
        private List<String> bestFitRoles;
        private List<RedFlagDto> redFlags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignalDto {
        private String category;
        private String insight;
        private String weight;  // "high", "medium", "low"
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScanOptimizationDto {
        private String first3WordsImpact;  // "high", "medium", "low"
        private String first3Words;
        private String visualDensity;  // "optimal", "too-long", "too-short"
        private Integer characterCount;
        private String metricVisibility;  // "excellent", "good", "poor"
        private String readingEffort;  // "low", "medium", "high"
        private String keywordStrength;  // "strong", "moderate", "weak"
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MicroStarsBalanceDto {
        private Integer situationPercent;
        private Integer taskPercent;
        private Integer actionPercent;
        private Integer resultPercent;
        private String assessment;
        private String compressionOpportunity;
        private String rewriteSuggestion;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RedFlagDto {
        private String type;
        private String severity;  // "low", "medium", "high"
        private String description;
    }
}

