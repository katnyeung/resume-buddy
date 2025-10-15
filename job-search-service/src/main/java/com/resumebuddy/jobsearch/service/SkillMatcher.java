package com.resumebuddy.jobsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.jobsearch.domain.SkillGap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Domain Service: Skill Matcher
 * Analyzes skill gaps between candidate and job requirements
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SkillMatcher {

    private final ObjectMapper objectMapper;

    /**
     * Analyze skill gap with proficiency weighting
     * Matched skills with higher proficiency scores contribute more to the final score
     *
     * @param skillProficiencies Map of skill name → proficiency score (0-100)
     * @param jobDescription Job description text to search
     * @return SkillGap with weighted score
     */
    public SkillGap analyzeSkillGapWithProficiency(
            java.util.Map<String, Integer> skillProficiencies,
            String jobDescription) {

        log.debug("Analyzing skill gap with proficiency: {} skills with proficiency scores",
                skillProficiencies.size());

        if (jobDescription == null || jobDescription.isEmpty()) {
            log.warn("Job description is empty, cannot match skills");
            return new SkillGap(new ArrayList<>(), new ArrayList<>(skillProficiencies.keySet()), 0.0, 0.0);
        }

        if (skillProficiencies == null || skillProficiencies.isEmpty()) {
            log.warn("Candidate has no skills to match");
            return new SkillGap(new ArrayList<>(), new ArrayList<>(), 100.0, 0.0);
        }

        Set<String> matchedSkills = new HashSet<>();
        Set<String> missingSkills = new HashSet<>();
        double matchedProficiencySum = 0.0;
        double totalProficiencySum = 0.0;

        // Search for each skill and weight by proficiency
        for (java.util.Map.Entry<String, Integer> entry : skillProficiencies.entrySet()) {
            String skill = entry.getKey();
            int proficiency = entry.getValue();

            if (skill == null || skill.trim().isEmpty()) {
                continue;
            }

            totalProficiencySum += proficiency;

            boolean found = searchSkillInText(skill, jobDescription);
            if (found) {
                matchedSkills.add(skill);
                matchedProficiencySum += proficiency;
            } else {
                missingSkills.add(skill);
            }
        }

        // Calculate simple match percentage (count-based)
        double matchPercentage = skillProficiencies.isEmpty() ? 100.0 :
                (matchedSkills.size() * 100.0) / skillProficiencies.size();

        // Calculate weighted score (proficiency-weighted)
        double weightedScore = totalProficiencySum == 0 ? 0.0 :
                (matchedProficiencySum / totalProficiencySum) * 100.0;

        SkillGap skillGap = new SkillGap(
                new ArrayList<>(matchedSkills),
                new ArrayList<>(missingSkills),
                matchPercentage,
                weightedScore
        );

        log.info("Skill gap analysis with proficiency: {}/{} skills matched ({}%), weighted score: {}%",
                matchedSkills.size(), skillProficiencies.size(),
                String.format("%.1f", matchPercentage),
                String.format("%.1f", weightedScore));

        return skillGap;
    }

    /**
     * Search for a skill in text using regex (case-insensitive, word boundary)
     */
    private boolean searchSkillInText(String skill, String text) {
        try {
            // Escape special regex characters in skill name
            String escapedSkill = Pattern.quote(skill);

            // Create pattern with word boundaries for exact match
            // \b ensures we match "React" but not "Reactionary"
            Pattern pattern = Pattern.compile("\\b" + escapedSkill + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            return matcher.find();

        } catch (Exception e) {
            log.error("Failed to search skill '{}' in text", skill, e);
            return false;
        }
    }

    /**
     * Analyze skill gap between candidate skills and job requirements (DEPRECATED)
     * Use analyzeSkillGapFromDescription() instead for better accuracy
     */
    @Deprecated
    public SkillGap analyzeSkillGap(Set<String> candidateSkills, Set<String> jobRequiredSkills) {
        log.debug("Analyzing skill gap: candidate has {}, job requires {}",
                candidateSkills.size(), jobRequiredSkills.size());

        // Normalize all skills to lowercase for comparison
        Set<String> normalizedCandidateSkills = normalizeSkills(candidateSkills);
        Set<String> normalizedJobSkills = normalizeSkills(jobRequiredSkills);

        // Find matched skills
        Set<String> matchedSkills = new HashSet<>(normalizedCandidateSkills);
        matchedSkills.retainAll(normalizedJobSkills);

        // Find missing skills
        Set<String> missingSkills = new HashSet<>(normalizedJobSkills);
        missingSkills.removeAll(normalizedCandidateSkills);

        // Calculate match percentage
        double matchPercentage = normalizedJobSkills.isEmpty() ? 100.0 :
                (matchedSkills.size() * 100.0) / normalizedJobSkills.size();

        SkillGap skillGap = new SkillGap(
                new ArrayList<>(matchedSkills),
                new ArrayList<>(missingSkills),
                matchPercentage,
                0.0 // No weighted score in deprecated method
        );

        log.info("Skill gap analysis: {}/{} skills matched ({}%)",
                matchedSkills.size(), normalizedJobSkills.size(), String.format("%.1f", matchPercentage));

        return skillGap;
    }

    /**
     * Convert SkillGap to JSON string
     */
    public String skillGapToJson(SkillGap skillGap) {
        try {
            return objectMapper.writeValueAsString(skillGap);
        } catch (Exception e) {
            log.error("Failed to convert SkillGap to JSON", e);
            return "{}";
        }
    }

    /**
     * Parse JSON to SkillGap
     */
    public SkillGap jsonToSkillGap(String json) {
        try {
            return objectMapper.readValue(json, SkillGap.class);
        } catch (Exception e) {
            log.error("Failed to parse SkillGap JSON", e);
            return new SkillGap(new ArrayList<>(), new ArrayList<>(), 0.0, 0.0);
        }
    }

    /**
     * Normalize skills (lowercase, trim)
     */
    private Set<String> normalizeSkills(Set<String> skills) {
        Set<String> normalized = new HashSet<>();
        for (String skill : skills) {
            normalized.add(skill.toLowerCase().trim());
        }
        return normalized;
    }

    /**
     * Extract skills from aggregatedSkills JSON string
     */
    public Set<String> extractSkillsFromJson(String aggregatedSkillsJson) {
        try {
            List<String> skillsList = objectMapper.readValue(aggregatedSkillsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            return new HashSet<>(skillsList);
        } catch (Exception e) {
            log.error("Failed to extract skills from JSON", e);
            return new HashSet<>();
        }
    }

}
