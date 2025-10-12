package com.resumebuddy.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceDto {
    private String id;
    private String jobTitle;
    private String companyName;
    private String startDate;
    private String endDate;
    private String description;
    private Boolean isAnalyzed;
    private LocalDateTime analyzedAt;
    private String analysisId;  // Latest job analysis ID
}
