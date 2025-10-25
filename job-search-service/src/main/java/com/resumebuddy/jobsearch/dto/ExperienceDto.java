package com.resumebuddy.jobsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO: Experience from resume-api
 */
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
    private List<SkillDto> extractedSkills;
}
