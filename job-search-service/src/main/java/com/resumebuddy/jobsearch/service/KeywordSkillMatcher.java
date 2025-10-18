package com.resumebuddy.jobsearch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Keyword-Based Skill Matcher
 * Matches skills from Neo4j vocabulary against job descriptions using regex
 * No LLM required - fast and cost-effective
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KeywordSkillMatcher {

    private final Neo4jJobListingService neo4jJobListingService;

    /**
     * Extract skills from job description by matching against Neo4j skill vocabulary
     *
     * @param jobDescription Full job description text
     * @return List of matched skill names
     */
    public List<String> extractSkills(String jobDescription) {
        if (jobDescription == null || jobDescription.isBlank()) {
            return List.of();
        }

        // Get all skills from Neo4j (cached for 1 hour)
        List<String> skillVocabulary = getAllSkillsCached();

        if (skillVocabulary.isEmpty()) {
            log.warn("No skills found in Neo4j vocabulary");
            return List.of();
        }

        // Match skills using case-insensitive word boundary regex
        Set<String> matchedSkills = new HashSet<>();
        String lowerDescription = jobDescription.toLowerCase();

        for (String skill : skillVocabulary) {
            if (matchesSkill(lowerDescription, skill)) {
                matchedSkills.add(skill);
            }
        }

        log.debug("Matched {} skills from vocabulary of {}", matchedSkills.size(), skillVocabulary.size());
        return new ArrayList<>(matchedSkills);
    }

    /**
     * Check if skill is mentioned in description using word boundary matching
     *
     * @param lowerDescription Lowercase job description
     * @param skill Skill name to match
     * @return true if skill is found
     */
    private boolean matchesSkill(String lowerDescription, String skill) {
        String lowerSkill = skill.toLowerCase();

        // Exact word boundary match
        // Example: "Java" matches "Java Developer" but not "JavaScript"
        String regex = "\\b" + Pattern.quote(lowerSkill) + "\\b";
        Pattern pattern = Pattern.compile(regex);

        return pattern.matcher(lowerDescription).find();
    }

    /**
     * Get all skills from Neo4j (cached for 1 hour to reduce Neo4j queries)
     *
     * @return List of all skill names
     */
    @Cacheable(value = "skillVocabulary", unless = "#result == null || #result.isEmpty()")
    public List<String> getAllSkillsCached() {
        log.info("Fetching skill vocabulary from Neo4j");
        List<String> skills = neo4jJobListingService.getAllSkills();
        log.info("Loaded {} skills from Neo4j", skills.size());
        return skills;
    }

    /**
     * Refresh skill vocabulary cache
     * Call this after adding new skills to Neo4j
     */
    public void refreshCache() {
        log.info("Refreshing skill vocabulary cache");
        getAllSkillsCached();
    }
}
