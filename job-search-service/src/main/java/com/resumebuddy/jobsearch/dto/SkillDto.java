package com.resumebuddy.jobsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO: Skill from resume-api
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillDto {
    private String name;
    private String category;
    private String subcategory;
    private boolean isTechnical;
    private boolean isPrimary;
}
