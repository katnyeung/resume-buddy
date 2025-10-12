package com.resumebuddy.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobAnalysisResultDto {

    private String id;
    private String resumeId;
    private String experienceId;

    // Normalization
    private String normalizedTitle;
    private String primarySocCode;
    private String seniorityLevel;

    // Scores
    private BigDecimal impactScore;
    private BigDecimal technicalDepthScore;
    private BigDecimal leadershipScore;
    private BigDecimal overallScore;

    // Analysis
    private String recruiterSummary;
    private List<WorkActivityDto> workActivities;
    private List<String> keyStrengths;
    private List<String> improvementAreas;

    // Comprehensive Report (NEW)
    private List<SocCodeDto> socCodes;
    private List<String> jobFamilies;
    private List<String> keyResponsibilities;
    private List<SkillDetailDto> extractedSkills;
    private List<LineMappingDto> descriptionLineMappings;
    private BigDecimal technicalDepth;
    private Boolean hasLeadership;
    private String leadershipScope;

    // Industry Classification (NEW)
    private String primaryIndustry;       // e.g., "Financial Services"
    private String industryVertical;      // e.g., "Fintech", "Banking", "Investment"
    private List<String> industrySectors; // e.g., ["Financial Technology", "Cloud Services"]

    // Experience details (for display)
    private String jobTitle;
    private String companyName;
    private String startDate;
    private String endDate;

    // Deep Graph Analysis (PHASE 6)
    private List<SkillDemonstrationDto> skillDemonstrationAnalysis;
    private List<DescriptionLineValueDto> descriptionLineValues;
    private List<MissingSkillTaskOpportunityDto> missingOpportunities;
    private List<MissingTaskDto> missingTasks;
    private List<MissingActivityDto> missingActivities;
    private List<MissingSkillByCategoryDto> missingSkillsByCategory;

    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkActivityDto {
        private String id;
        private String name;
        private String category;
        private Double importance;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SocCodeDto {
        private String code;
        private String title;
        private Double confidence;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillDetailDto {
        private String name;
        private String category;
        private String subcategory;
        private Integer proficiencyLevel;
        private Boolean isTechnical;
        private Boolean isPrimary;
        private Integer mentionedCount;
    }

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
        private String impactLevel;
        private String scope;
        private RecruiterInsightsDto recruiterInsights;

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
        public static class RecruiterInsightsDto {
            private List<SignalDto> strongSignals;
            private List<String> potentialQuestions;
            private Integer recruiterAppealScore;
            private String appealReasoning;
            private List<String> bestFitRoles;
            private List<String> redFlags;

            @Data
            @NoArgsConstructor
            @AllArgsConstructor
            public static class SignalDto {
                private String category;
                private String insight;
                private String weight;
            }
        }
    }

    // ==================== PHASE 6: Deep Graph Analysis DTOs ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillDemonstrationDto {
        private String skillName;
        private String category;
        private Integer tasksLinked;
        private Integer linesShowcasing;
        private List<String> exampleLines;
        private List<String> taskNames;  // O*NET task names
        private Boolean isPrimary;
        private String evidenceStrength;  // STRONG, MODERATE, WEAK, NONE
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DescriptionLineValueDto {
        private Integer sequence;
        private String text;
        private List<String> skillsShowcased;
        private Integer skillCount;
        private String impactLevel;
        private String valueRating;  // EXCELLENT, GOOD, MODERATE, LOW
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissingSkillTaskOpportunityDto {
        private String skillName;
        private String taskId;
        private String taskName;
        private Double importance;
        private String taskCategory;
        private String occupationTitle;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissingTaskDto {
        private String taskId;
        private String taskName;
        private Double importance;
        private String category;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissingActivityDto {
        private String activityId;
        private String activityName;
        private Double importance;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissingSkillByCategoryDto {
        private String skillName;
        private String category;
        private Integer requiredByTasks;
        private Double avgImportance;
        private List<String> taskNames;
    }
}
