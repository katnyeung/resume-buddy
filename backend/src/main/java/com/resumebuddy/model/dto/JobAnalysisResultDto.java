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
    private List<String> starsAnalysisSuggestions;

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

    // Task Demonstration Analysis (PHASE 11.3)
    private List<TaskDemonstrationDto> taskDemonstrationAnalysis;

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

            @Data
            @NoArgsConstructor
            @AllArgsConstructor
            public static class SignalDto {
                private String category;
                private String insight;
                private String weight;
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
        private Double avgTaskImportance;  // Average importance of linked tasks
        private Boolean isPrimary;
        private String evidenceStrength;  // STRONG, MODERATE, WEAK, NONE

        // PHASE 6.5: Task-level popularity data
        private List<TaskPopularityDataDto> taskPopularityData;
        private List<SkillCooccurrenceDto> cooccurringSkills;
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

    // ==================== PHASE 6.5: Task-Level Skill Popularity DTOs ====================

    /**
     * Represents task-level popularity analysis for a specific O*NET task
     * Shows which skills can accomplish this task and their popularity
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskPopularityDataDto {
        private String taskId;
        private String taskName;
        private List<SkillPopularityDto> skillPopularity;
        private List<MissingTaskSkillDto> missingSkills;
    }

    /**
     * Represents a single skill's popularity for a specific task
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillPopularityDto {
        private String skillName;
        private String skillCategory;
        private Integer peopleWithSkill;
        private Boolean userHasSkill;
    }

    /**
     * Represents skills that co-occur with the user's skill
     * Example: "72% of Spring Boot users also have Docker"
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillCooccurrenceDto {
        private String skillName;
        private String category;
        private Integer peopleCount;
        private Double percentage;
        private Boolean userHasSkill;
    }

    /**
     * Represents a skill missing for a specific task
     * Shows competitive gaps at the task level
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissingTaskSkillDto {
        private String skillName;
        private String category;
        private Integer peopleWithSkill;
        private Double taskImportance;
    }

    // ==================== PHASE 11.3: Task Demonstration Analysis ====================

    /**
     * Task-centric view of how user demonstrates O*NET tasks
     * Shows which skills and resume lines demonstrate each task
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskDemonstrationDto {
        private String taskId;
        private String taskName;
        private Double importance;
        private String taskCategory;
        private String coverageStrength; // STRONG/MODERATE/WEAK/NONE
        private Integer skillCount;
        private Integer lineCount;
        private List<SkillCoveringTaskDto> skills;
        private List<ResumeLineDto> lines;
    }

    /**
     * Skill that demonstrates a specific O*NET task
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillCoveringTaskDto {
        private String skillName;
        private String category;
        private Boolean isPrimary;
        private Integer lineCount; // how many lines for THIS skill demonstrate the task
    }

    /**
     * Resume line that demonstrates an O*NET task
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumeLineDto {
        private String lineId;
        private Integer sequence;
        private String text;
    }
}
