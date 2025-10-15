package com.resumebuddy.jobsearch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.jobsearch.dto.ExperienceDto;
import com.resumebuddy.jobsearch.dto.GeneratedJobProfileDto;
import com.resumebuddy.jobsearch.dto.SkillDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Domain Service: Job Post Generator
 * Generates job post text with metadata from multiple resume experiences
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobPostGenerator {

    private final GrokLLMClient grokLLMClient;
    private final ObjectMapper objectMapper;

    /**
     * Generate job post with location, experience level, and suggested job title from multiple experiences
     * @param experiences List of candidate's resume experiences
     */
    public GeneratedJobProfileDto generateJobPost(List<ExperienceDto> experiences) {
        try {
            log.info("Generating job post from {} experiences", experiences.size());

            // Load prompt template
            String promptTemplate = loadPromptTemplate();

            // Aggregate data from all experiences
            String experiencesText = formatExperiences(experiences);
            String aggregatedSkills = formatAggregatedSkills(experiences);
            int totalYearsExp = calculateTotalYears(experiences);

            // Replace placeholders
            String prompt = promptTemplate
                    .replace("{experienceCount}", String.valueOf(experiences.size()))
                    .replace("{totalYearsExperience}", String.valueOf(totalYearsExp))
                    .replace("{experiences}", experiencesText)
                    .replace("{aggregatedSkills}", aggregatedSkills);

            // Call LLM
            String llmResponse = grokLLMClient.callLLM(prompt);

            // Parse JSON response
            GeneratedJobProfileDto result = parseJobProfileResponse(llmResponse);

            log.info("Successfully generated job profile - suggested title: '{}', location: {}, level: {}, requirements length: {}",
                    result.getSuggestedJobTitle(), result.getLocation(), result.getExperienceLevel(), result.getJobPost().length());
            return result;

        } catch (Exception e) {
            log.error("Failed to generate job post", e);
            throw new RuntimeException("Failed to generate job post", e);
        }
    }

    /**
     * Parse LLM response to extract location, experience level, requirements, and skill proficiencies
     */
    private GeneratedJobProfileDto parseJobProfileResponse(String llmResponse) {
        try {
            // Clean response - remove markdown code blocks if present
            String cleanedResponse = llmResponse.trim();
            if (cleanedResponse.startsWith("```json")) {
                cleanedResponse = cleanedResponse.substring(7);
            }
            if (cleanedResponse.startsWith("```")) {
                cleanedResponse = cleanedResponse.substring(3);
            }
            if (cleanedResponse.endsWith("```")) {
                cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
            }
            cleanedResponse = cleanedResponse.trim();

            // Parse JSON
            JsonNode jsonNode = objectMapper.readTree(cleanedResponse);

            String suggestedTitle = jsonNode.has("suggestedJobTitle") ? jsonNode.get("suggestedJobTitle").asText() : "";
            String location = jsonNode.has("location") ? jsonNode.get("location").asText() : "Remote";
            String experienceLevel = jsonNode.has("experienceLevel") ? jsonNode.get("experienceLevel").asText() : "Mid Level";
            String requirements = jsonNode.has("requirements") ? jsonNode.get("requirements").asText() : "";

            // Parse skill proficiencies map
            Map<String, Integer> skillProficiencies = new java.util.HashMap<>();
            if (jsonNode.has("skillProficiencies") && jsonNode.get("skillProficiencies").isObject()) {
                JsonNode profNode = jsonNode.get("skillProficiencies");
                profNode.fields().forEachRemaining(entry -> {
                    String skillName = entry.getKey();
                    int score = entry.getValue().asInt(50); // Default 50 if invalid
                    skillProficiencies.put(skillName, score);
                });
                log.info("Parsed {} skill proficiencies from LLM", skillProficiencies.size());
            }

            GeneratedJobProfileDto result = new GeneratedJobProfileDto();
            result.setJobPost(requirements);
            result.setSuggestedJobTitle(suggestedTitle);
            result.setLocation(location);
            result.setExperienceLevel(experienceLevel);
            result.setSkillProficiencies(skillProficiencies);

            return result;

        } catch (Exception e) {
            log.error("Failed to parse LLM JSON response, using fallback", e);
            // Fallback: treat entire response as job post
            GeneratedJobProfileDto fallback = new GeneratedJobProfileDto();
            fallback.setJobPost(llmResponse);
            fallback.setLocation("Remote");
            fallback.setExperienceLevel("Mid Level");
            fallback.setSkillProficiencies(new java.util.HashMap<>());
            return fallback;
        }
    }

    /**
     * Format experiences for prompt
     */
    private String formatExperiences(List<ExperienceDto> experiences) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < experiences.size(); i++) {
            ExperienceDto exp = experiences.get(i);
            sb.append(String.format("\n### Experience %d:\n", i + 1));
            sb.append(String.format("**Title**: %s\n", exp.getJobTitle()));
            sb.append(String.format("**Company**: %s\n", exp.getCompanyName()));
            sb.append(String.format("**Duration**: %s to %s\n", exp.getStartDate(), exp.getEndDate()));
            sb.append(String.format("**Description**:\n%s\n", exp.getDescription()));
        }
        return sb.toString();
    }

    /**
     * Aggregate and format skills from all experiences
     */
    private String formatAggregatedSkills(List<ExperienceDto> experiences) {
        Set<String> allSkills = experiences.stream()
                .flatMap(exp -> exp.getExtractedSkills().stream())
                .map(SkillDto::getName)
                .collect(Collectors.toSet());

        return String.join(", ", allSkills);
    }

    /**
     * Calculate total years of experience
     */
    private int calculateTotalYears(List<ExperienceDto> experiences) {
        // Simple calculation - can be enhanced to handle overlapping dates
        return experiences.size() * 2; // Rough estimate: 2 years per role
    }

    /**
     * Load prompt template from resources
     */
    private String loadPromptTemplate() throws IOException {
        ClassPathResource resource = new ClassPathResource("prompts/job-search-profile-generation-prompt.txt");
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
