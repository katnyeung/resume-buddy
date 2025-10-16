package com.resumebuddy.jobsearch.domain.service;

import com.resumebuddy.jobsearch.domain.JobSearchProfile;
import com.resumebuddy.jobsearch.domain.JobSearchProfileSkill;
import com.resumebuddy.jobsearch.repository.JobListingRepository;
import com.resumebuddy.jobsearch.repository.JobSearchProfileRepository;
import com.resumebuddy.jobsearch.repository.JobSearchProfileSkillRepository;
import com.resumebuddy.jobsearch.service.GrokLLMClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Domain Service: Keyword Generation
 * Uses LLM to generate optimal job search keywords based on database analysis
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KeywordGenerationService {

    private final JobListingRepository jobListingRepository;
    private final JobSearchProfileRepository profileRepository;
    private final JobSearchProfileSkillRepository profileSkillRepository;
    private final GrokLLMClient grokClient;

    /**
     * Generate search keywords using LLM based on active user profiles
     * LLM generates COMPLETE job titles with appropriate seniority levels
     *
     * @param count Number of complete job title keywords to generate
     * @return List of keyword/exclude pairs for crawling (no expansion needed)
     */
    public List<KeywordPair> generateSearchKeywords(int count) {
        try {
            log.info("Generating {} complete job title keywords using LLM based on active user profiles", count);

            // 1. Analyze current database state (active profiles, desired titles, top skills)
            Map<String, Object> dbStats = analyzeDatabaseState();

            // 2. Build prompt for Grok
            String prompt = buildKeywordGenerationPrompt(dbStats, count);

            // 3. Call Grok LLM
            String response = grokClient.callLLM(prompt);

            // 4. Parse response into keyword pairs (already complete job titles)
            List<KeywordPair> keywords = parseKeywordResponse(response);

            log.info("Generated {} complete job title keywords", keywords.size());
            return keywords;

        } catch (Exception e) {
            log.error("Failed to generate keywords using LLM, falling back to defaults", e);
            return getDefaultKeywords();
        }
    }

    /**
     * Analyze database to understand what users actually want
     * Focuses on ACTIVE profiles (visited in last 7 days) and their desired job titles + high-proficiency skills
     */
    private Map<String, Object> analyzeDatabaseState() {
        Map<String, Object> stats = new HashMap<>();

        // 1. Find active profiles (visited in last 7 days)
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<JobSearchProfile> activeProfiles = profileRepository.findByLastVisitDateAfter(sevenDaysAgo);

        stats.put("activeProfileCount", activeProfiles.size());
        log.debug("Found {} active profiles (last 7 days)", activeProfiles.size());

        if (activeProfiles.isEmpty()) {
            log.warn("No active profiles found in last 7 days - using fallback defaults");
            stats.put("desiredJobTitles", new ArrayList<>());
            stats.put("topSkillsByProficiency", new ArrayList<>());
            stats.put("profileLocations", new ArrayList<>());
            return stats;
        }

        // 2. Group profiles by desired_job_title and count
        Map<String, Long> jobTitleCounts = activeProfiles.stream()
                .filter(p -> p.getDesiredJobTitle() != null && !p.getDesiredJobTitle().isEmpty())
                .collect(Collectors.groupingBy(JobSearchProfile::getDesiredJobTitle, Collectors.counting()));

        List<String> desiredJobTitles = jobTitleCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> String.format("\"%s\" (%d users)", e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        stats.put("desiredJobTitles", desiredJobTitles);
        log.debug("Desired job titles: {}", desiredJobTitles);

        // 3. Get all skills from active profiles
        List<String> activeProfileIds = activeProfiles.stream()
                .map(JobSearchProfile::getId)
                .collect(Collectors.toList());

        List<JobSearchProfileSkill> allSkills = profileSkillRepository.findByProfileIdIn(activeProfileIds);
        log.debug("Found {} total skills across {} active profiles", allSkills.size(), activeProfileIds.size());

        // 4. Calculate top 5 skills by HIGHEST proficiency score (not frequency)
        Map<String, Integer> skillMaxProficiency = new HashMap<>();
        for (JobSearchProfileSkill skill : allSkills) {
            if (skill.getProficiencyScore() != null) {
                skillMaxProficiency.merge(
                        skill.getSkillName(),
                        skill.getProficiencyScore(),
                        Math::max  // Keep highest proficiency score
                );
            }
        }

        List<String> topSkillsByProficiency = skillMaxProficiency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> String.format("\"%s\" (proficiency: %d)", e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        stats.put("topSkillsByProficiency", topSkillsByProficiency);
        log.debug("Top 5 skills by proficiency: {}", topSkillsByProficiency);

        // 5. Get profile locations for LLM analysis
        List<String> profileLocations = activeProfiles.stream()
                .filter(p -> p.getLocation() != null && !p.getLocation().isEmpty())
                .map(JobSearchProfile::getLocation)
                .distinct()
                .collect(Collectors.toList());
        stats.put("profileLocations", profileLocations);
        log.debug("Profile locations: {}", profileLocations);

        return stats;
    }

    /**
     * Build LLM prompt for keyword generation from template file
     * Now uses active profiles, desired job titles, and top skills by proficiency
     */
    private String buildKeywordGenerationPrompt(Map<String, Object> dbStats, int count) {
        try {
            // Load prompt template from resources/prompts/
            ClassPathResource resource = new ClassPathResource("prompts/keyword-generation-prompt.txt");
            String template = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // Extract values from dbStats
            int activeProfileCount = (Integer) dbStats.get("activeProfileCount");
            @SuppressWarnings("unchecked")
            List<String> desiredJobTitles = (List<String>) dbStats.get("desiredJobTitles");
            @SuppressWarnings("unchecked")
            List<String> topSkillsByProficiency = (List<String>) dbStats.get("topSkillsByProficiency");
            @SuppressWarnings("unchecked")
            List<String> profileLocations = (List<String>) dbStats.get("profileLocations");

            // Build context strings
            String jobTitlesContext = desiredJobTitles.isEmpty() ?
                    "No desired job titles specified yet" :
                    String.join(", ", desiredJobTitles);

            String skillsContext = topSkillsByProficiency.isEmpty() ?
                    "No skills specified yet" :
                    String.join(", ", topSkillsByProficiency);

            String locationContext = profileLocations.isEmpty() ?
                    "No locations specified yet" :
                    String.join(", ", profileLocations.subList(0, Math.min(5, profileLocations.size())));

            // Replace placeholders in template
            return template
                    .replace("{activeProfileCount}", String.valueOf(activeProfileCount))
                    .replace("{desiredJobTitles}", jobTitlesContext)
                    .replace("{topSkillsByProficiency}", skillsContext)
                    .replace("{locationContext}", locationContext)
                    .replace("{count}", String.valueOf(count));

        } catch (IOException e) {
            log.error("Failed to load keyword generation prompt template", e);
            throw new RuntimeException("Failed to load prompt template", e);
        }
    }

    /**
     * Parse LLM response into keyword pairs with location data
     */
    private List<KeywordPair> parseKeywordResponse(String response) {
        List<KeywordPair> keywords = new ArrayList<>();

        try {
            // Extract JSON array from response (handle markdown code blocks)
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "").trim();
            }

            // Enhanced pattern to capture location fields
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "\"keyword\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*" +
                    "\"exclude\"\\s*:\\s*\\[([^\\]]*)\\]\\s*,\\s*" +
                    "\"targetCountryCode\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*" +
                    "\"targetCityRegion\"\\s*:\\s*\"([^\"]+)\""
            );
            java.util.regex.Matcher matcher = pattern.matcher(json);

            while (matcher.find()) {
                String keyword = matcher.group(1);
                String excludeStr = matcher.group(2);
                String countryCode = matcher.group(3);
                String cityRegion = matcher.group(4);

                List<String> excludeList = new ArrayList<>();
                if (!excludeStr.trim().isEmpty()) {
                    String[] parts = excludeStr.split(",");
                    for (String part : parts) {
                        String cleaned = part.trim().replaceAll("\"", "");
                        if (!cleaned.isEmpty()) {
                            excludeList.add(cleaned);
                        }
                    }
                }

                keywords.add(new KeywordPair(keyword, excludeList, countryCode, cityRegion));
            }

            log.info("Parsed {} keyword pairs with location data from LLM response", keywords.size());

        } catch (Exception e) {
            log.error("Failed to parse LLM response", e);
        }

        return keywords.isEmpty() ? getDefaultKeywords() : keywords;
    }

    /**
     * Fallback COMPLETE keywords if LLM fails
     * Using empty exclude lists to maximize results
     */
    private List<KeywordPair> getDefaultKeywords() {
        List<KeywordPair> defaults = new ArrayList<>();
        defaults.add(new KeywordPair("Software Engineer", List.of()));
        defaults.add(new KeywordPair("Senior Java Developer", List.of()));
        defaults.add(new KeywordPair("Python Engineer", List.of()));
        defaults.add(new KeywordPair("Data Engineer", List.of()));
        defaults.add(new KeywordPair("DevOps Engineer", List.of()));
        defaults.add(new KeywordPair("Full Stack Developer", List.of()));
        defaults.add(new KeywordPair("Senior React Developer", List.of()));
        defaults.add(new KeywordPair("Cloud Solutions Architect", List.of()));
        defaults.add(new KeywordPair("Backend Developer", List.of()));
        defaults.add(new KeywordPair("Frontend Developer", List.of()));
        return defaults;
    }

    /**
     * Keyword pair with exclude filters and location data
     */
    public static class KeywordPair {
        private final String keyword;
        private final List<String> exclude;
        private final String targetCountryCode; // e.g., "gb", "us"
        private final String targetCityRegion;  // e.g., "London", "New York"

        public KeywordPair(String keyword, List<String> exclude) {
            this(keyword, exclude, "gb", "London"); // Default to UK
        }

        public KeywordPair(String keyword, List<String> exclude, String targetCountryCode, String targetCityRegion) {
            this.keyword = keyword;
            this.exclude = exclude;
            this.targetCountryCode = targetCountryCode;
            this.targetCityRegion = targetCityRegion;
        }

        public String getKeyword() {
            return keyword;
        }

        public List<String> getExclude() {
            return exclude;
        }

        public String getTargetCountryCode() {
            return targetCountryCode;
        }

        public String getTargetCityRegion() {
            return targetCityRegion;
        }
    }
}
