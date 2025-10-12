package com.resumebuddy.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NormalizedJobDto {

    private String normalizedTitle;
    private List<SocCodeMapping> socCodes;
    private String seniority;  // Entry, Mid, Senior, Lead, Principal
    private List<String> jobFamilies;
    private List<String> keyResponsibilities;
    private Double technicalDepth;  // 1-10
    private Boolean hasLeadership;
    private String leadershipScope;  // team, department, organization

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SocCodeMapping {
        private String code;
        private String title;
        private Double confidence;  // 0-1
    }
}
