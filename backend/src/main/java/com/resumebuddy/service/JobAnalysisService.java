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
import java.util.List;
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

        // Step 3: Fetch O*NET data
        log.info("Fetching O*NET occupation data for SOC code: {}", normalized.getSocCodes().get(0).getCode());
        String onetData = fetchONetData(normalized.getSocCodes().get(0).getCode());

        // Step 4: Update Neo4j graph
        log.info("Storing job analysis in Neo4j graph database");
        neo4jGraphService.storeJobAnalysisInGraph(resumeId, experienceId, experience, normalized);
        neo4jGraphService.storeWorkActivities(normalized.getSocCodes().get(0).getCode(), onetData);

        // Step 5: Evaluate job with LLM
        log.info("Evaluating job quality with LLM...");
        Map<String, Object> evaluation = evaluateJobWithLLM(experience, normalized, onetData);

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

        // Scores
        analysis.setImpactScore(BigDecimal.valueOf((Double) evaluation.get("impactScore")));
        analysis.setTechnicalDepthScore(BigDecimal.valueOf((Double) evaluation.get("technicalDepthScore")));
        analysis.setLeadershipScore(BigDecimal.valueOf((Double) evaluation.get("leadershipScore")));
        analysis.setOverallScore(BigDecimal.valueOf((Double) evaluation.get("overallScore")));

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
