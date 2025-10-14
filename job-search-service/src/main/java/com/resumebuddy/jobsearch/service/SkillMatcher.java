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
     * Analyze skill gap between candidate skills and job requirements
     */
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
                matchPercentage
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
            return new SkillGap(new ArrayList<>(), new ArrayList<>(), 0.0);
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

    /**
     * Extract skills from job listing requiredSkills JSON string
     */
    public Set<String> extractJobSkillsFromJson(String requiredSkillsJson) {
        return extractSkillsFromJson(requiredSkillsJson);
    }
}
