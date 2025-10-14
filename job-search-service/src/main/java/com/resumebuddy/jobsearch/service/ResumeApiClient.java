package com.resumebuddy.jobsearch.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.jobsearch.dto.ExperienceDto;
import com.resumebuddy.jobsearch.dto.SkillDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Infrastructure Service: Resume API Client
 * Fetches experience and skill data from resume-api service
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ResumeApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.resume-api.base-url:http://localhost:8080/api}")
    private String resumeApiBaseUrl;

    /**
     * Fetch experience details including skills from job analysis
     */
    public ExperienceDto fetchExperience(String resumeId, String experienceId) {
        try {
            log.info("Fetching experience from resume-api: resumeId={}, experienceId={}", resumeId, experienceId);

            // First get experience basic data
            String experienceUrl = String.format("%s/resumes/%s/structured-analysis", resumeApiBaseUrl, resumeId);
            String response = restTemplate.getForObject(experienceUrl, String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode experiences = root.path("experiences");

            // Find matching experience
            for (JsonNode exp : experiences) {
                if (experienceId.equals(exp.path("id").asText())) {
                    ExperienceDto dto = new ExperienceDto();
                    dto.setId(exp.path("id").asText());
                    dto.setJobTitle(exp.path("jobTitle").asText());
                    dto.setCompanyName(exp.path("companyName").asText());
                    dto.setStartDate(exp.path("startDate").asText());
                    dto.setEndDate(exp.path("endDate").asText());
                    dto.setDescription(exp.path("description").asText());

                    // Try to fetch job analysis for skills
                    try {
                        List<SkillDto> skills = fetchSkillsFromJobAnalysis(resumeId, experienceId);
                        dto.setExtractedSkills(skills);
                    } catch (Exception e) {
                        log.warn("Could not fetch skills from job analysis: {}", e.getMessage());
                        dto.setExtractedSkills(new ArrayList<>());
                    }

                    log.info("Successfully fetched experience: {}", dto.getJobTitle());
                    return dto;
                }
            }

            throw new RuntimeException("Experience not found: " + experienceId);

        } catch (Exception e) {
            log.error("Failed to fetch experience from resume-api", e);
            throw new RuntimeException("Failed to fetch experience", e);
        }
    }

    /**
     * Fetch skills from job analysis
     */
    private List<SkillDto> fetchSkillsFromJobAnalysis(String resumeId, String experienceId) {
        try {
            String jobAnalysisUrl = String.format("%s/resumes/%s/experiences/%s/analysis",
                    resumeApiBaseUrl, resumeId, experienceId);

            String response = restTemplate.getForObject(jobAnalysisUrl, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode skillsNode = root.path("extractedSkills");

            List<SkillDto> skills = new ArrayList<>();
            for (JsonNode skillNode : skillsNode) {
                SkillDto skill = new SkillDto();
                skill.setName(skillNode.path("name").asText());
                skill.setCategory(skillNode.path("category").asText());
                skill.setSubcategory(skillNode.path("subcategory").asText());
                skill.setTechnical(skillNode.path("isTechnical").asBoolean());
                skill.setPrimary(skillNode.path("isPrimary").asBoolean());
                skills.add(skill);
            }

            log.info("Fetched {} skills from job analysis", skills.size());
            return skills;

        } catch (Exception e) {
            log.warn("Could not fetch skills from job analysis", e);
            return new ArrayList<>();
        }
    }

    /**
     * Fetch multiple experiences
     */
    public List<ExperienceDto> fetchExperiences(String resumeId, List<String> experienceIds) {
        List<ExperienceDto> experiences = new ArrayList<>();
        for (String experienceId : experienceIds) {
            experiences.add(fetchExperience(resumeId, experienceId));
        }
        return experiences;
    }
}
