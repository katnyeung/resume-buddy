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

    @Value("${app.onet.data-refresh-days:30}")
    private int onetDataRefreshDays;

    /**
     * Main orchestration method for job analysis
     * Steps:
     * 1. Get experience from DB
     * 2. Comprehensive LLM analysis (normalize job + extract skills) - MERGED
     * 3. Fetch O*NET data for all mapped occupations
     * 4. Store in Neo4j graph (occupations, skills)
     * 5. Parse description lines and map to O*NET activities/tasks - NEW
     * 6. Evaluate job quality with LLM
     * 7. Save to job_analysis table
     */
    @Transactional
    public JobAnalysisResultDto analyzeJob(String resumeId, String experienceId) {
        log.info("Starting job analysis for resume {} experience {}", resumeId, experienceId);

        // Step 1: Get experience
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new RuntimeException("Resume not found: " + resumeId));

        ResumeAnalysisExperience experience = experienceRepository.findById(experienceId)
                .orElseThrow(() -> new RuntimeException("Experience not found: " + experienceId));

        // Step 2: Comprehensive LLM analysis (MERGED: normalization + skill extraction)
        log.info("Running comprehensive job analysis with LLM (normalization + skills)...");
        ComprehensiveAnalysisResult comprehensiveResult = analyzeJobComprehensive(experience);
        NormalizedJobDto normalized = comprehensiveResult.getNormalized();
        List<Map<String, Object>> skills = comprehensiveResult.getSkills();

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
        neo4jGraphService.storeJobAnalysisInGraphWithSkills(resumeId, experienceId, experience, normalized, skills);

        // Store O*NET data only if occupation is stale (not updated recently)
        for (Map.Entry<String, String> entry : allOccupationData.entrySet()) {
            String socCode = entry.getKey();
            if (neo4jGraphService.isOccupationDataStale(socCode, onetDataRefreshDays)) {
                log.info("Refreshing O*NET data for SOC {} (stale or missing)", socCode);
                neo4jGraphService.storeWorkActivities(socCode, entry.getValue());
            } else {
                log.info("Skipping O*NET refresh for SOC {} - updated within {} days",
                        socCode, onetDataRefreshDays);
            }
        }

        // Create MAPS_TO relationships for all occupations
        neo4jGraphService.createMultiOccupationMappings(experienceId, normalized.getSocCodes());

        // Step 4.5: Map job skills to O*NET soft skills and technologies
        log.info("Mapping job skills to O*NET taxonomy using graph data");
        Map<String, List<String>> onetTaxonomy = neo4jGraphService.fetchONetTaxonomyFromGraph(primarySocCode);

        if (onetTaxonomy.get("skills").isEmpty() && onetTaxonomy.get("technologies").isEmpty()) {
            log.warn("O*NET taxonomy not found in graph for SOC {}, using LLM fallback", primarySocCode);
            neo4jGraphService.mapSkillsToONet(experienceId, primarySocCode, skills, aggregatedOnetData);
        } else {
            neo4jGraphService.createSkillMappingsFromGraph(experienceId, primarySocCode, skills, onetTaxonomy);
        }

        // Step 5: Parse description lines and map to O*NET activities/tasks (NEW)
        log.info("Parsing description lines and mapping to O*NET activities/tasks");
        analyzeDescriptionLines(experienceId, experience.getDescription(), primarySocCode,
                                normalized.getNormalizedTitle(), experience.getJobTitle());

        // Step 6: Evaluate job with LLM (use primary occupation data)
        log.info("Evaluating job quality with LLM...");
        Map<String, Object> evaluation = evaluateJobWithLLM(experience, normalized, allOccupationData.get(primarySocCode));

        // Step 7: Save to database
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
     * Comprehensive job analysis combining normalization and skill extraction
     * MERGED LLM call to reduce API calls and cost
     */
    private ComprehensiveAnalysisResult analyzeJobComprehensive(ResumeAnalysisExperience experience) {
        try {
            String prompt = loadPromptTemplate("prompts/comprehensive-job-analysis-prompt.txt");
            prompt = prompt.replace("{jobTitle}", experience.getJobTitle() != null ? experience.getJobTitle() : "")
                    .replace("{company}", experience.getCompanyName() != null ? experience.getCompanyName() : "")
                    .replace("{startDate}", experience.getStartDate() != null ? experience.getStartDate() : "")
                    .replace("{endDate}", experience.getEndDate() != null ? experience.getEndDate() : "")
                    .replace("{description}", experience.getDescription() != null ? experience.getDescription() : "");

            String llmResponse = callLLM(prompt);

            // Parse the comprehensive response
            com.resumebuddy.model.dto.ComprehensiveJobAnalysisDto comprehensiveDto =
                objectMapper.readValue(llmResponse, com.resumebuddy.model.dto.ComprehensiveJobAnalysisDto.class);

            // Convert to internal format
            NormalizedJobDto normalized = convertToNormalizedJobDto(comprehensiveDto.getJobNormalization());
            List<Map<String, Object>> skills = convertToSkillsList(comprehensiveDto.getSkillExtraction().getSkills());

            return new ComprehensiveAnalysisResult(normalized, skills);

        } catch (Exception e) {
            log.error("Error in comprehensive job analysis with LLM", e);
            throw new RuntimeException("Failed to analyze job comprehensively", e);
        }
    }

    /**
     * Parse description lines and map to O*NET activities/tasks
     */
    private void analyzeDescriptionLines(String experienceId, String description, String socCode,
                                         String occupationTitle, String jobTitle) {
        if (description == null || description.trim().isEmpty()) {
            log.warn("No description to analyze for experience {}", experienceId);
            return;
        }

        // Step 1: Parse description into lines
        List<String> descriptionLines = neo4jGraphService.parseDescriptionIntoLines(experienceId, description);
        if (descriptionLines.isEmpty()) {
            log.warn("No description lines parsed for experience {}", experienceId);
            return;
        }

        // Step 2: Fetch O*NET activities and tasks for this occupation
        Map<String, List<String>> onetData = neo4jGraphService.fetchONetActivitiesAndTasks(socCode);
        List<String> activities = onetData.get("activities");
        List<String> tasks = onetData.get("tasks");

        if (activities.isEmpty() && tasks.isEmpty()) {
            log.warn("No O*NET activities/tasks found for SOC {}. Skipping line mapping.", socCode);
            return;
        }

        // Step 3: Call LLM to map description lines to O*NET activities/tasks
        try {
            String prompt = loadPromptTemplate("prompts/description-activity-mapping-prompt.txt");
            prompt = prompt.replace("{jobTitle}", jobTitle)
                    .replace("{occupationTitle}", occupationTitle)
                    .replace("{socCode}", socCode)
                    .replace("{descriptionLines}", formatDescriptionLinesForPrompt(descriptionLines))
                    .replace("{onetActivities}", formatONetDataForPrompt(activities))
                    .replace("{onetTasks}", formatONetDataForPrompt(tasks));

            String llmResponse = callLLM(prompt);

            // Parse response
            com.resumebuddy.model.dto.DescriptionLineMappingDto mappingDto =
                objectMapper.readValue(llmResponse, com.resumebuddy.model.dto.DescriptionLineMappingDto.class);

            // Convert to internal format and store in graph
            List<Map<String, Object>> lineMappings = convertToLineMappings(mappingDto.getLineMappings());
            neo4jGraphService.storeDescriptionLineMappings(experienceId, socCode, lineMappings);

            log.info("Successfully mapped {} description lines to O*NET activities/tasks", lineMappings.size());

        } catch (Exception e) {
            log.error("Error mapping description lines to O*NET activities/tasks", e);
            // Don't throw - allow job analysis to continue
        }
    }

    /**
     * Call LLM to normalize job title and map to O*NET SOC codes
     * @deprecated Use analyzeJobComprehensive instead - merges normalization + skill extraction
     */
    @Deprecated
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

    // ==================== Conversion Helper Methods ====================

    /**
     * Convert ComprehensiveJobAnalysisDto.JobNormalizationDto to NormalizedJobDto
     */
    private NormalizedJobDto convertToNormalizedJobDto(
            com.resumebuddy.model.dto.ComprehensiveJobAnalysisDto.JobNormalizationDto jobNorm) {

        NormalizedJobDto normalized = new NormalizedJobDto();
        normalized.setNormalizedTitle(jobNorm.getNormalizedTitle());
        normalized.setSeniority(jobNorm.getSeniority());
        normalized.setJobFamilies(jobNorm.getJobFamilies());
        normalized.setKeyResponsibilities(jobNorm.getKeyResponsibilities());
        normalized.setTechnicalDepth(jobNorm.getTechnicalDepth());
        normalized.setHasLeadership(jobNorm.getHasLeadership());
        normalized.setLeadershipScope(jobNorm.getLeadershipScope());

        // Convert SOC codes
        List<NormalizedJobDto.SocCodeMapping> socCodes = new ArrayList<>();
        for (com.resumebuddy.model.dto.ComprehensiveJobAnalysisDto.SocCodeMappingDto socDto : jobNorm.getSocCodes()) {
            NormalizedJobDto.SocCodeMapping mapping = new NormalizedJobDto.SocCodeMapping();
            mapping.setCode(socDto.getCode());
            mapping.setTitle(socDto.getTitle());
            mapping.setConfidence(socDto.getConfidence());
            socCodes.add(mapping);
        }
        normalized.setSocCodes(socCodes);

        return normalized;
    }

    /**
     * Convert SkillDto list to Map<String, Object> list for Neo4j
     */
    private List<Map<String, Object>> convertToSkillsList(
            List<com.resumebuddy.model.dto.ComprehensiveJobAnalysisDto.SkillDto> skillDtos) {

        List<Map<String, Object>> skills = new ArrayList<>();
        for (com.resumebuddy.model.dto.ComprehensiveJobAnalysisDto.SkillDto skillDto : skillDtos) {
            Map<String, Object> skill = new HashMap<>();
            skill.put("name", skillDto.getName());
            skill.put("category", skillDto.getCategory());
            skill.put("subcategory", skillDto.getSubcategory());
            skill.put("proficiencyLevel", skillDto.getProficiencyLevel());
            skill.put("isTechnical", skillDto.getIsTechnical());
            skill.put("isPrimary", skillDto.getIsPrimary());
            skill.put("mentionedCount", skillDto.getMentionedCount());
            skills.add(skill);
        }
        return skills;
    }

    /**
     * Convert LineMappingDto list to Map<String, Object> list for Neo4j
     */
    private List<Map<String, Object>> convertToLineMappings(
            List<com.resumebuddy.model.dto.DescriptionLineMappingDto.LineMappingDto> lineDtos) {

        List<Map<String, Object>> lineMappings = new ArrayList<>();
        for (com.resumebuddy.model.dto.DescriptionLineMappingDto.LineMappingDto lineDto : lineDtos) {
            Map<String, Object> lineMapping = new HashMap<>();
            lineMapping.put("sequence", lineDto.getSequence());
            lineMapping.put("text", lineDto.getText());
            lineMapping.put("impactMetrics", lineDto.getImpactMetrics());
            lineMapping.put("hasQuantifiableImpact", lineDto.getHasQuantifiableImpact());
            lineMapping.put("impactLevel", lineDto.getImpactLevel());
            lineMapping.put("scope", lineDto.getScope());

            // Convert activities
            List<Map<String, Object>> activities = new ArrayList<>();
            if (lineDto.getActivities() != null) {
                for (com.resumebuddy.model.dto.DescriptionLineMappingDto.ActivityMappingDto actDto : lineDto.getActivities()) {
                    Map<String, Object> activity = new HashMap<>();
                    activity.put("activityName", actDto.getActivityName());
                    activity.put("activityId", actDto.getActivityId());
                    activity.put("confidence", actDto.getConfidence());
                    activity.put("reasoning", actDto.getReasoning());
                    activities.add(activity);
                }
            }
            lineMapping.put("activities", activities);

            // Convert tasks
            List<Map<String, Object>> tasks = new ArrayList<>();
            if (lineDto.getTasks() != null) {
                for (com.resumebuddy.model.dto.DescriptionLineMappingDto.TaskMappingDto taskDto : lineDto.getTasks()) {
                    Map<String, Object> task = new HashMap<>();
                    task.put("taskName", taskDto.getTaskName());
                    task.put("taskId", taskDto.getTaskId());
                    task.put("confidence", taskDto.getConfidence());
                    task.put("reasoning", taskDto.getReasoning());
                    tasks.add(task);
                }
            }
            lineMapping.put("tasks", tasks);

            lineMappings.add(lineMapping);
        }
        return lineMappings;
    }

    /**
     * Format description lines for LLM prompt
     */
    private String formatDescriptionLinesForPrompt(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(String.format("%d. %s\n", i + 1, lines.get(i)));
        }
        return sb.toString();
    }

    /**
     * Format O*NET activities/tasks for LLM prompt
     */
    private String formatONetDataForPrompt(List<String> data) {
        StringBuilder sb = new StringBuilder();
        for (String item : data) {
            String[] parts = item.split("\\|");
            if (parts.length == 2) {
                sb.append(String.format("- ID: %s | Name: %s\n", parts[0], parts[1]));
            }
        }
        return sb.toString();
    }

    // ==================== Inner Classes ====================

    /**
     * Result of comprehensive analysis (normalization + skills)
     */
    private static class ComprehensiveAnalysisResult {
        private final NormalizedJobDto normalized;
        private final List<Map<String, Object>> skills;

        public ComprehensiveAnalysisResult(NormalizedJobDto normalized, List<Map<String, Object>> skills) {
            this.normalized = normalized;
            this.skills = skills;
        }

        public NormalizedJobDto getNormalized() {
            return normalized;
        }

        public List<Map<String, Object>> getSkills() {
            return skills;
        }
    }
}
