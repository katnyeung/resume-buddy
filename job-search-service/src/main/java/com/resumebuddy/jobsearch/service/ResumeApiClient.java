package com.resumebuddy.jobsearch.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.jobsearch.dto.ExperienceDto;
import com.resumebuddy.jobsearch.dto.SkillDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
     * Get Authorization header with current user's JWT token
     */
    private HttpHeaders getAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        // Get the JWT token from the current security context
        // This assumes the job-search-service received the same token from the user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() != null) {
            String token = authentication.getCredentials().toString();
            headers.set("Authorization", "Bearer " + token);
        }
        return headers;
    }

    /**
     * Fetch experience details including skills from job analysis
     */
    public ExperienceDto fetchExperience(String resumeId, String experienceId) {
        try {
            log.info("Fetching experience from resume-api: resumeId={}, experienceId={}", resumeId, experienceId);

            // First get experience basic data
            String experienceUrl = String.format("%s/resumes/%s/structured-analysis", resumeApiBaseUrl, resumeId);
            HttpEntity<Void> requestEntity = new HttpEntity<>(getAuthHeaders());
            ResponseEntity<String> responseEntity = restTemplate.exchange(
                experienceUrl,
                HttpMethod.GET,
                requestEntity,
                String.class
            );
            String response = responseEntity.getBody();

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

            HttpEntity<Void> requestEntity = new HttpEntity<>(getAuthHeaders());
            ResponseEntity<String> responseEntity = restTemplate.exchange(
                jobAnalysisUrl,
                HttpMethod.GET,
                requestEntity,
                String.class
            );
            String response = responseEntity.getBody();
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

    /**
     * Fetch all skills from resume's structured analysis
     * @param resumeId Resume ID
     * @return List of skill names
     */
    public List<String> fetchResumeSkills(String resumeId) {
        try {
            log.info("Fetching all skills from resume: {}", resumeId);

            String analysisUrl = String.format("%s/resumes/%s/structured-analysis", resumeApiBaseUrl, resumeId);
            HttpEntity<Void> requestEntity = new HttpEntity<>(getAuthHeaders());
            ResponseEntity<String> responseEntity = restTemplate.exchange(
                analysisUrl,
                HttpMethod.GET,
                requestEntity,
                String.class
            );
            String response = responseEntity.getBody();

            JsonNode root = objectMapper.readTree(response);
            JsonNode skillsNode = root.path("skills");

            List<String> skillNames = new ArrayList<>();
            for (JsonNode skillNode : skillsNode) {
                String skillName = skillNode.path("skillName").asText();
                if (!skillName.isEmpty()) {
                    skillNames.add(skillName);
                }
            }

            log.info("Fetched {} skills from resume {}", skillNames.size(), resumeId);
            return skillNames;

        } catch (Exception e) {
            log.error("Failed to fetch resume skills from resume-api", e);
            return new ArrayList<>();
        }
    }
}
