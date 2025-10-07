package com.resumebuddy.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.model.JobAnalysis;
import com.resumebuddy.model.Resume;
import com.resumebuddy.model.ResumeAnalysisExperience;
import com.resumebuddy.model.dto.JobAnalysisResultDto;
import com.resumebuddy.model.dto.NormalizedJobDto;
import com.resumebuddy.repository.JobAnalysisRepository;
import com.resumebuddy.repository.ResumeAnalysisExperienceRepository;
import com.resumebuddy.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobAnalysisService {

    private final JobAnalysisRepository jobAnalysisRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeAnalysisExperienceRepository experienceRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ONetIntegrationService onetIntegrationService;
    private final Neo4jGraphService neo4jGraphService;

    @Value("${app.openai.api-key}")
    private String apiKey;

    @Value("${app.openai.base-url}")
    private String baseUrl;

    @Value("${app.openai.model}")
    private String model;

    /**
     * Main orchestration method for job analysis
     * Steps:
     * 1. Get experience from DB
     * 2. Normalize job title with LLM → SOC codes
     * 3. Call O*NET API for occupation details (placeholder - you implement)
     * 4. Store in Neo4j graph (placeholder - you implement)
     * 5. Evaluate job with LLM
     * 6. Save to job_analysis table
     */
    @Transactional
    public JobAnalysisResultDto analyzeJob(String resumeId, String experienceId) {
        log.info("Starting job analysis for resume {} experience {}", resumeId, experienceId);

        // Step 1: Get experience
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new RuntimeException("Resume not found: " + resumeId));

        ResumeAnalysisExperience experience = experienceRepository.findById(experienceId)
                .orElseThrow(() -> new RuntimeException("Experience not found: " + experienceId));

        // Step 2: Normalize job with LLM
        log.info("Normalizing job title with LLM...");
        NormalizedJobDto normalized = normalizeJobWithLLM(experience);

        // Step 3: Fetch O*NET data for ALL mapped occupations (multi-occupation mapping)
        log.info("Fetching O*NET data for {} occupations", normalized.getSocCodes().size());
        Map<String, String> allOccupationData = new HashMap<>();
        for (NormalizedJobDto.SocCodeMapping socMapping : normalized.getSocCodes()) {
            String socCode = socMapping.getCode();
            log.info("Fetching O*NET data for SOC: {} ({})", socCode, socMapping.getTitle());
            String onetData = fetchONetData(socCode);
            allOccupationData.put(socCode, onetData);
        }

        // Aggregate O*NET data from all occupations
        String aggregatedOnetData = aggregateONetData(allOccupationData);
        String primarySocCode = normalized.getSocCodes().get(0).getCode();

        // Step 4: Update Neo4j graph with multi-occupation support
        log.info("Storing job analysis in Neo4j graph database");
        neo4jGraphService.storeJobAnalysisInGraph(resumeId, experienceId, experience, normalized);

        // Store O*NET data for ALL occupations
        for (Map.Entry<String, String> entry : allOccupationData.entrySet()) {
            neo4jGraphService.storeWorkActivities(entry.getKey(), entry.getValue());
        }

        // Create MAPS_TO relationships for all occupations
        neo4jGraphService.createMultiOccupationMappings(experienceId, normalized.getSocCodes());

        // Step 4.5: Map job skills to O*NET soft skills and technologies (using aggregated data)
        log.info("Mapping job skills to O*NET skills and technologies from {} occupations", allOccupationData.size());
        neo4jGraphService.mapSkillsToONet(
                experienceId,
                primarySocCode,
                neo4jGraphService.getLastExtractedSkills(),
                aggregatedOnetData
        );

        // Step 5: Evaluate job with LLM (use primary occupation data)
        log.info("Evaluating job quality with LLM...");
        Map<String, Object> evaluation = evaluateJobWithLLM(experience, normalized, allOccupationData.get(primarySocCode));

        // Step 6: Save to database
        JobAnalysis analysis = createJobAnalysisEntity(resume, experience, normalized, evaluation);
        JobAnalysis saved = jobAnalysisRepository.save(analysis);

        log.info("Job analysis completed successfully for experience {}", experienceId);
        return mapToDto(saved);
    }

    /**
     * Get existing job analysis
     */
    @Transactional(readOnly = true)
    public JobAnalysisResultDto getJobAnalysis(String resumeId, String experienceId) {
        JobAnalysis analysis = jobAnalysisRepository.findByResumeIdAndExperienceId(resumeId, experienceId)
                .orElseThrow(() -> new RuntimeException("Job analysis not found"));
        return mapToDto(analysis);
    }

    /**
     * Check if job analysis exists
     */
    @Transactional(readOnly = true)
    public boolean analysisExists(String resumeId, String experienceId) {
        return jobAnalysisRepository.existsByResumeIdAndExperienceId(resumeId, experienceId);
    }

    /**
     * Delete job analysis
     */
    @Transactional
    public void deleteJobAnalysis(String resumeId, String experienceId) {
        jobAnalysisRepository.deleteByResumeIdAndExperienceId(resumeId, experienceId);
    }

    // ==================== LLM Methods ====================

    /**
     * Call LLM to normalize job title and map to O*NET SOC codes
     */
    private NormalizedJobDto normalizeJobWithLLM(ResumeAnalysisExperience experience) {
        try {
            String prompt = loadPromptTemplate("prompts/job-normalization-prompt.txt");
            prompt = prompt.replace("{jobTitle}", experience.getJobTitle() != null ? experience.getJobTitle() : "")
                    .replace("{company}", experience.getCompanyName() != null ? experience.getCompanyName() : "")
                    .replace("{startDate}", experience.getStartDate() != null ? experience.getStartDate() : "")
                    .replace("{endDate}", experience.getEndDate() != null ? experience.getEndDate() : "")
                    .replace("{description}", experience.getDescription() != null ? experience.getDescription() : "");

            String llmResponse = callLLM(prompt);
            return objectMapper.readValue(llmResponse, NormalizedJobDto.class);

        } catch (Exception e) {
            log.error("Error normalizing job with LLM", e);
            throw new RuntimeException("Failed to normalize job", e);
        }
    }

    /**
     * Call LLM to evaluate job quality and provide recruiter insights
     */
    private Map<String, Object> evaluateJobWithLLM(
            ResumeAnalysisExperience experience,
            NormalizedJobDto normalized,
            String onetData) {
        try {
            String prompt = loadPromptTemplate("prompts/recruiter-evaluation-prompt.txt");
            prompt = prompt.replace("{jobTitle}", experience.getJobTitle() != null ? experience.getJobTitle() : "")
                    .replace("{normalizedTitle}", normalized.getNormalizedTitle())
                    .replace("{company}", experience.getCompanyName() != null ? experience.getCompanyName() : "")
                    .replace("{startDate}", experience.getStartDate() != null ? experience.getStartDate() : "")
                    .replace("{endDate}", experience.getEndDate() != null ? experience.getEndDate() : "")
                    .replace("{seniorityLevel}", normalized.getSeniority())
                    .replace("{socCode}", normalized.getSocCodes().get(0).getCode())
                    .replace("{description}", experience.getDescription() != null ? experience.getDescription() : "")
                    .replace("{onetData}", onetData);

            String llmResponse = callLLM(prompt);
            return objectMapper.readValue(llmResponse, new TypeReference<Map<String, Object>>() {});

        } catch (Exception e) {
            log.error("Error evaluating job with LLM", e);
            throw new RuntimeException("Failed to evaluate job", e);
        }
    }

    /**
     * Generic LLM call method
     */
    private String callLLM(String prompt) {
        try {
            Map<String, Object> request = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0.3,
                    "response_format", Map.of("type", "json_object")
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/chat/completions",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText();

        } catch (Exception e) {
            log.error("Error calling LLM API", e);
            throw new RuntimeException("LLM API call failed", e);
        }
    }

    // ==================== O*NET Methods ====================

    /**
     * Aggregate O*NET data from multiple occupations
     * Combines skills, technologies, activities, and knowledge from all mapped occupations
     */
    private String aggregateONetData(Map<String, String> allOccupationData) {
        try {
            Map<String, Object> aggregated = new HashMap<>();
            Set<Map<String, Object>> allSkills = new HashSet<>();
            Set<Map<String, Object>> allTechnologies = new HashSet<>();
            Set<Map<String, Object>> allActivities = new HashSet<>();
            Set<Map<String, Object>> allKnowledge = new HashSet<>();

            // Aggregate from all occupations
            for (String onetDataJson : allOccupationData.values()) {
                JsonNode onetData = objectMapper.readTree(onetDataJson);

                // Aggregate skills
                JsonNode skills = onetData.path("skills");
                if (skills.isArray()) {
                    skills.forEach(skill -> {
                        Map<String, Object> skillMap = new HashMap<>();
                        skillMap.put("name", skill.path("name").asText());
                        skillMap.put("level", skill.path("level").asDouble(0));
                        allSkills.add(skillMap);
                    });
                }

                // Aggregate technology_skills
                JsonNode techs = onetData.path("technology_skills");
                if (techs.isArray()) {
                    techs.forEach(tech -> {
                        Map<String, Object> techMap = new HashMap<>();
                        techMap.put("name", tech.path("name").asText());
                        techMap.put("category", tech.path("category").asText(""));
                        allTechnologies.add(techMap);
                    });
                }

                // Aggregate detailed_work_activities
                JsonNode activities = onetData.path("detailed_work_activities");
                if (activities.isArray()) {
                    activities.forEach(activity -> {
                        Map<String, Object> activityMap = new HashMap<>();
                        activityMap.put("name", activity.path("name").asText());
                        activityMap.put("importance", activity.path("importance").asDouble(0));
                        allActivities.add(activityMap);
                    });
                }

                // Aggregate knowledge
                JsonNode knowledge = onetData.path("knowledge");
                if (knowledge.isArray()) {
                    knowledge.forEach(k -> {
                        Map<String, Object> knowledgeMap = new HashMap<>();
                        knowledgeMap.put("name", k.path("name").asText());
                        knowledgeMap.put("level", k.path("level").asDouble(0));
                        allKnowledge.add(knowledgeMap);
                    });
                }
            }

            // Build aggregated result
            aggregated.put("skills", new ArrayList<>(allSkills));
            aggregated.put("technology_skills", new ArrayList<>(allTechnologies));
            aggregated.put("detailed_work_activities", new ArrayList<>(allActivities));
            aggregated.put("knowledge", new ArrayList<>(allKnowledge));
            aggregated.put("code", "AGGREGATED");
            aggregated.put("title", "Aggregated from " + allOccupationData.size() + " occupations");
            aggregated.put("description", "Combined O*NET data from multiple occupations");

            log.info("Aggregated O*NET data: {} skills, {} technologies, {} activities",
                    allSkills.size(), allTechnologies.size(), allActivities.size());

            return objectMapper.writeValueAsString(aggregated);

        } catch (Exception e) {
            log.error("Error aggregating O*NET data", e);
            return "{}";
        }
    }

    /**
     * Fetch O*NET occupation data from real O*NET Web Services API
     */
    private String fetchONetData(String socCode) {
        log.info("Fetching O*NET data for SOC code: {}", socCode);

        if (!onetIntegrationService.isConfigured()) {
            log.warn("O*NET service not configured, check username/password in application.yml");
            throw new RuntimeException("O*NET service not configured");
        }

        return onetIntegrationService.getOccupationDetails(socCode);
    }

    // ==================== Helper Methods ====================

    private String loadPromptTemplate(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private JobAnalysis createJobAnalysisEntity(
            Resume resume,
            ResumeAnalysisExperience experience,
            NormalizedJobDto normalized,
            Map<String, Object> evaluation) {

        JobAnalysis analysis = new JobAnalysis();
        analysis.setResume(resume);
        analysis.setExperience(experience);

        // Normalization data
        analysis.setNormalizedTitle(normalized.getNormalizedTitle());
        analysis.setPrimarySocCode(normalized.getSocCodes().get(0).getCode());
        analysis.setSeniorityLevel(normalized.getSeniority());

        // Scores (with null-safe handling)
        analysis.setImpactScore(BigDecimal.valueOf(
            evaluation.get("impactScore") != null ? (Double) evaluation.get("impactScore") : 0.0));
        analysis.setTechnicalDepthScore(BigDecimal.valueOf(
            evaluation.get("technicalDepthScore") != null ? (Double) evaluation.get("technicalDepthScore") : 0.0));
        analysis.setLeadershipScore(BigDecimal.valueOf(
            evaluation.get("leadershipScore") != null ? (Double) evaluation.get("leadershipScore") : 0.0));
        analysis.setOverallScore(BigDecimal.valueOf(
            evaluation.get("overallScore") != null ? (Double) evaluation.get("overallScore") : 0.0));

        // Analysis text
        analysis.setRecruiterSummary((String) evaluation.get("recruiterSummary"));

        // JSON fields
        try {
            analysis.setKeyStrengths(objectMapper.writeValueAsString(evaluation.get("keyStrengths")));
            analysis.setImprovementAreas(objectMapper.writeValueAsString(evaluation.get("improvementAreas")));
        } catch (Exception e) {
            log.error("Error serializing evaluation data", e);
        }

        return analysis;
    }

    private JobAnalysisResultDto mapToDto(JobAnalysis analysis) {
        JobAnalysisResultDto dto = new JobAnalysisResultDto();
        dto.setId(analysis.getId());
        dto.setResumeId(analysis.getResume().getId());
        dto.setExperienceId(analysis.getExperience().getId());

        dto.setNormalizedTitle(analysis.getNormalizedTitle());
        dto.setPrimarySocCode(analysis.getPrimarySocCode());
        dto.setSeniorityLevel(analysis.getSeniorityLevel());

        dto.setImpactScore(analysis.getImpactScore());
        dto.setTechnicalDepthScore(analysis.getTechnicalDepthScore());
        dto.setLeadershipScore(analysis.getLeadershipScore());
        dto.setOverallScore(analysis.getOverallScore());

        dto.setRecruiterSummary(analysis.getRecruiterSummary());

        // Parse JSON fields
        try {
            if (analysis.getKeyStrengths() != null) {
                dto.setKeyStrengths(objectMapper.readValue(analysis.getKeyStrengths(), new TypeReference<List<String>>() {}));
            }
            if (analysis.getImprovementAreas() != null) {
                dto.setImprovementAreas(objectMapper.readValue(analysis.getImprovementAreas(), new TypeReference<List<String>>() {}));
            }
        } catch (Exception e) {
            log.error("Error parsing JSON fields", e);
        }

        dto.setCreatedAt(analysis.getCreatedAt());
        return dto;
    }
}
