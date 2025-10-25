package com.resumebuddy.jobsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Skill Train response DTO
 * Returns user's skills and related market skills with job counts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillTrainDto {
    /**
     * Skills the user already has (from resume analysis)
     */
    private List<String> userSkills;

    /**
     * Top in-demand skills from job market (may include user skills + new skills)
     */
    private Map<String, Long> marketSkills;

    /**
     * Job count for each skill individually
     * Key: skill name, Value: number of jobs requiring this skill
     */
    private Map<String, Long> jobCountBySkill;
}
