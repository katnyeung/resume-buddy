package com.resumebuddy.jobsearch.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Value Object: Skill Gap
 * Represents the difference between candidate skills and job requirements
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillGap {

    private List<String> matchedSkills;
    private List<String> missingSkills;
    private double matchPercentage;

    public boolean hasSignificantGaps() {
        return matchPercentage < 70.0;
    }

    public int getTotalSkillsRequired() {
        return matchedSkills.size() + missingSkills.size();
    }
}
