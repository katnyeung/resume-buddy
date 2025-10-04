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
}
