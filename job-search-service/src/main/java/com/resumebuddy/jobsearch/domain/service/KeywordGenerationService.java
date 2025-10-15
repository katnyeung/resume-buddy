package com.resumebuddy.jobsearch.domain.service;

import com.resumebuddy.jobsearch.repository.JobListingRepository;
import com.resumebuddy.jobsearch.repository.JobSearchProfileRepository;
import com.resumebuddy.jobsearch.service.GrokLLMClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final GrokLLMClient grokClient;

    /**
     * Generate search keywords using LLM based on current database state
     * Returns expanded keywords with experience level variations
     *
     * @param count Number of base keyword groups to generate (will be expanded to 3x)
     * @return List of keyword/exclude pairs for crawling (3x the count due to level variations)
     */
    public List<KeywordPair> generateSearchKeywords(int count) {
        try {
            log.info("Generating {} base keyword groups using LLM (will expand with level variations)", count);

            // 1. Analyze current database state
            Map<String, Object> dbStats = analyzeDatabaseState();

            // 2. Build prompt for Grok
            String prompt = buildKeywordGenerationPrompt(dbStats, count);

            // 3. Call Grok LLM
            String response = grokClient.callLLM(prompt);

            // 4. Parse response into keyword pairs
            List<KeywordPair> baseKeywords = parseKeywordResponse(response);

            // 5. Expand each keyword with experience level variations
            List<KeywordPair> expandedKeywords = expandWithExperienceLevels(baseKeywords);

            log.info("Generated {} base keywords, expanded to {} total keywords with level variations",
                    baseKeywords.size(), expandedKeywords.size());
            return expandedKeywords;

        } catch (Exception e) {
            log.error("Failed to generate keywords using LLM, falling back to defaults", e);
            return expandWithExperienceLevels(getDefaultKeywords());
        }
    }

    /**
     * Analyze database to understand what we have and what we need
     */
    private Map<String, Object> analyzeDatabaseState() {
        Map<String, Object> stats = new HashMap<>();

        // Total job listings
        long totalJobs = jobListingRepository.count();
        stats.put("totalJobs", totalJobs);

        // Total user profiles
        long totalProfiles = profileRepository.count();
        stats.put("totalProfiles", totalProfiles);

        // Get profile locations for LLM analysis
        List<String> profileLocations = new ArrayList<>();
        profileRepository.findAll().forEach(profile -> {
            if (profile.getLocation() != null && !profile.getLocation().isEmpty()) {
                profileLocations.add(profile.getLocation());
            }
        });
        stats.put("profileLocations", profileLocations);

        stats.put("hasJobs", totalJobs > 0);

        log.debug("Database stats: {} jobs, {} profiles, {} locations", totalJobs, totalProfiles, profileLocations.size());
        return stats;
    }

    /**
     * Build LLM prompt for keyword generation from template file
     */
    private String buildKeywordGenerationPrompt(Map<String, Object> dbStats, int count) {
        try {
            // Load prompt template from resources/prompts/
            ClassPathResource resource = new ClassPathResource("prompts/keyword-generation-prompt.txt");
            String template = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // Extract values from dbStats
            long totalJobs = (Long) dbStats.get("totalJobs");
            long totalProfiles = (Long) dbStats.get("totalProfiles");
            @SuppressWarnings("unchecked")
            List<String> profileLocations = (List<String>) dbStats.get("profileLocations");

            // Build location context
            String locationContext = "";
            if (!profileLocations.isEmpty()) {
                locationContext = String.format("- User profile locations: %s",
                        String.join(", ", profileLocations.subList(0, Math.min(5, profileLocations.size()))));
            }

            // Replace placeholders in template
            return template
                    .replace("{totalJobs}", String.valueOf(totalJobs))
                    .replace("{totalProfiles}", String.valueOf(totalProfiles))
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
     * Expand base keywords with experience level variations
     * Each base keyword generates 3 variations: base, senior, lead
     *
     * Example: "software engineer" → ["software engineer", "senior software engineer", "lead software engineer"]
     */
    private List<KeywordPair> expandWithExperienceLevels(List<KeywordPair> baseKeywords) {
        List<KeywordPair> expanded = new ArrayList<>();

        for (KeywordPair base : baseKeywords) {
            String keyword = base.getKeyword().toLowerCase().trim();

            // Skip if already has level prefix
            if (keyword.startsWith("senior ") || keyword.startsWith("lead ") ||
                keyword.startsWith("principal ") || keyword.startsWith("junior ")) {
                expanded.add(base);
                continue;
            }

            // 1. Base level (mid-level/general)
            expanded.add(new KeywordPair(keyword, base.getExclude(), base.getTargetCountryCode(), base.getTargetCityRegion()));

            // 2. Senior level
            expanded.add(new KeywordPair("senior " + keyword, base.getExclude(), base.getTargetCountryCode(), base.getTargetCityRegion()));

            // 3. Lead level
            expanded.add(new KeywordPair("lead " + keyword, base.getExclude(), base.getTargetCountryCode(), base.getTargetCityRegion()));
        }

        log.info("Expanded {} base keywords to {} total keywords", baseKeywords.size(), expanded.size());
        return expanded;
    }

    /**
     * Fallback BASE keywords if LLM fails (will be expanded to 30 total)
     * Using empty exclude lists to maximize results
     */
    private List<KeywordPair> getDefaultKeywords() {
        List<KeywordPair> defaults = new ArrayList<>();
        defaults.add(new KeywordPair("software engineer", List.of()));
        defaults.add(new KeywordPair("java developer", List.of()));
        defaults.add(new KeywordPair("python developer", List.of()));
        defaults.add(new KeywordPair("data engineer", List.of()));
        defaults.add(new KeywordPair("devops engineer", List.of()));
        defaults.add(new KeywordPair("full stack developer", List.of()));
        defaults.add(new KeywordPair("react developer", List.of()));
        defaults.add(new KeywordPair("cloud engineer", List.of()));
        defaults.add(new KeywordPair("backend developer", List.of()));
        defaults.add(new KeywordPair("frontend developer", List.of()));
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
