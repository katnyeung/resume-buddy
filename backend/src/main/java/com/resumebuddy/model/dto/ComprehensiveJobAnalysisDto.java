package com.resumebuddy.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * DTO for comprehensive job analysis response from LLM
 * Combines job normalization and skill extraction in a single response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComprehensiveJobAnalysisDto {

    private JobNormalizationDto jobNormalization;
    private SkillExtractionDto skillExtraction;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobNormalizationDto {
        private String normalizedTitle;
        private List<SocCodeMappingDto> socCodes;
        private String seniority;
        private List<String> jobFamilies;
        private List<String> keyResponsibilities;
        private Double technicalDepth;
        private Boolean hasLeadership;
        private String leadershipScope;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SocCodeMappingDto {
        private String code;
        private String title;
        private Double confidence;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillExtractionDto {
        private List<SkillDto> skills;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillDto {
        private String name;
        private String category;
        private String subcategory;
        private Integer proficiencyLevel;
        private Boolean isTechnical;
        private Boolean isPrimary;
        private Integer mentionedCount;
    }
}
