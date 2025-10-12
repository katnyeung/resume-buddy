package com.resumebuddy.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ATSReportDto {
    private Integer score;
    private String summary;
    private List<String> strengths;
    private List<String> improvements;
    private String contentBalance; // "sparse", "balanced", "verbose"
    private String readability; // "poor", "fair", "good", "excellent"
    private String sectionOrganization; // "poor", "fair", "good", "excellent"
}
