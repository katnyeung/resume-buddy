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
        log.info("Parsing description lines and mapping to O*NET activities/tasks and skills");
        List<Map<String, Object>> lineMappings = analyzeDescriptionLines(
            experienceId, experience.getDescription(), primarySocCode,
            normalized.getNormalizedTitle(), experience.getJobTitle(), skills
        );

        // Step 6: Evaluate job with LLM (use primary occupation data)
        log.info("Evaluating job quality with LLM...");
        Map<String, Object> evaluation = evaluateJobWithLLM(experience, normalized, allOccupationData.get(primarySocCode));

        // Step 7: Build complete analysis result DTO
        JobAnalysisResultDto result = buildAnalysisResultDto(
            resume, experience, normalized, skills, lineMappings, evaluation
        );

        // Step 8: Save analysis to database as JSON
        JobAnalysis analysis = new JobAnalysis();
        analysis.setResumeId(resumeId);
        analysis.setExperienceId(experienceId);
        try {
            analysis.setAnalysisResult(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            log.error("Error serializing job analysis result to JSON", e);
            throw new RuntimeException("Failed to save job analysis", e);
        }
        jobAnalysisRepository.save(analysis);

        // Step 9: Update experience to mark as analyzed
        experience.setIsAnalyzed(true);
        experience.setAnalyzedAt(java.time.LocalDateTime.now());
        experienceRepository.save(experience);

        log.info("Job analysis completed successfully for experience {}", experienceId);
        return result;
    }

    /**
     * Get existing job analysis with deep graph enrichment
     */
    @Transactional(readOnly = true)
    public JobAnalysisResultDto getJobAnalysis(String resumeId, String experienceId) {
        JobAnalysis analysis = jobAnalysisRepository.findFirstByResumeIdAndExperienceIdOrderByCreatedAtDesc(resumeId, experienceId)
                .orElseThrow(() -> new RuntimeException("Job analysis not found"));

        try {
            JobAnalysisResultDto dto = objectMapper.readValue(analysis.getAnalysisResult(), JobAnalysisResultDto.class);

            // Set the analysis ID in the DTO
            dto.setId(analysis.getId());

            // NEW: Enrich with deep graph analysis (Phase 6)
            enrichWithDeepGraphAnalysis(dto, experienceId);

            return dto;
        } catch (Exception e) {
            log.error("Error parsing job analysis JSON", e);
            throw new RuntimeException("Failed to parse job analysis", e);
        }
    }

    /**
     * Get existing job analysis by analysis ID with deep graph enrichment
     */
    @Transactional(readOnly = true)
    public JobAnalysisResultDto getJobAnalysisById(String analysisId) {
        JobAnalysis analysis = jobAnalysisRepository.findById(analysisId)
                .orElseThrow(() -> new RuntimeException("Job analysis not found with ID: " + analysisId));

        try {
            JobAnalysisResultDto dto = objectMapper.readValue(analysis.getAnalysisResult(), JobAnalysisResultDto.class);

            // Set the analysis ID in the DTO
            dto.setId(analysis.getId());

            // Enrich with deep graph analysis (Phase 6)
            enrichWithDeepGraphAnalysis(dto, dto.getExperienceId());

            return dto;
        } catch (Exception e) {
            log.error("Error parsing job analysis JSON for ID: {}", analysisId, e);
            throw new RuntimeException("Failed to parse job analysis", e);
        }
    }

    /**
     * Enrich analysis DTO with real-time Neo4j graph insights (Phase 6)
     * Computes:
     * 1. Skill demonstration evidence
     * 2. Description line value scores
     * 3. Missing skill-task opportunities
     * 4. Missing high-priority tasks/activities
     */
    private void enrichWithDeepGraphAnalysis(JobAnalysisResultDto dto, String experienceId) {
        try {
            log.info("Enriching analysis with deep graph insights for experience: {}", experienceId);

            // 1. Skill demonstration analysis
            List<Map<String, Object>> skillDemo = neo4jGraphService.getSkillDemonstrationAnalysis(experienceId);
            log.info("Retrieved {} skill demonstration records from Neo4j", skillDemo != null ? skillDemo.size() : 0);
            if (skillDemo != null && !skillDemo.isEmpty()) {
                dto.setSkillDemonstrationAnalysis(convertToSkillDemonstrationDtos(skillDemo));
                log.info("Added {} skill demonstration analyses to DTO", skillDemo.size());
            } else {
                log.warn("No skill demonstration data found for experience: {}", experienceId);
            }

            // 2. Description line value scores
            List<Map<String, Object>> lineValues = neo4jGraphService.getDescriptionLineValueScores(experienceId);
            if (lineValues != null && !lineValues.isEmpty()) {
                dto.setDescriptionLineValues(convertToDescriptionLineValueDtos(lineValues));
                log.debug("Added {} description line value scores", lineValues.size());
            }

            // 3. Missing skill-task opportunities
            List<Map<String, Object>> opportunities = neo4jGraphService.getMissingSkillTaskOpportunities(experienceId, 60.0);
            if (opportunities != null && !opportunities.isEmpty()) {
                dto.setMissingOpportunities(convertToMissingOpportunityDtos(opportunities));
                log.debug("Added {} missing opportunities", opportunities.size());
            }

            // 4. Missing high-priority tasks/activities
            Map<String, List<Map<String, Object>>> missing = neo4jGraphService.getMissingTasksAndActivities(experienceId, 5, 60.0);
            if (missing != null) {
                List<Map<String, Object>> tasks = missing.get("tasks");
                List<Map<String, Object>> activities = missing.get("activities");

                if (tasks != null && !tasks.isEmpty()) {
                    dto.setMissingTasks(convertToMissingTaskDtos(tasks));
                    log.debug("Added {} missing tasks", tasks.size());
                }

                if (activities != null && !activities.isEmpty()) {
                    dto.setMissingActivities(convertToMissingActivityDtos(activities));
                    log.debug("Added {} missing activities", activities.size());
                }
            }

            // 5. Missing skills by category (max 3 per category)
            List<Map<String, Object>> missingSkills = neo4jGraphService.getMissingSkillsByCategory(experienceId, 3);
            if (missingSkills != null && !missingSkills.isEmpty()) {
                dto.setMissingSkillsByCategory(convertToMissingSkillByCategoryDtos(missingSkills));
                log.debug("Added {} missing skills grouped by category", missingSkills.size());
            }

            log.info("Successfully enriched analysis with deep graph insights");

        } catch (Exception e) {
            log.error("Error enriching with deep graph analysis: {}", e.getMessage());
            // Don't fail - just skip enrichment if graph queries fail
        }
    }

    /**
     * Convert skill demonstration maps to DTOs
     */
    private List<JobAnalysisResultDto.SkillDemonstrationDto> convertToSkillDemonstrationDtos(List<Map<String, Object>> skillMaps) {
        List<JobAnalysisResultDto.SkillDemonstrationDto> dtos = new ArrayList<>();
        for (Map<String, Object> map : skillMaps) {
            JobAnalysisResultDto.SkillDemonstrationDto dto = new JobAnalysisResultDto.SkillDemonstrationDto();
            dto.setSkillName((String) map.get("skillName"));
            dto.setCategory((String) map.get("category"));
            dto.setTasksLinked(((Number) map.get("tasksLinked")).intValue());
            dto.setLinesShowcasing(((Number) map.get("linesShowcasing")).intValue());
            dto.setExampleLines((List<String>) map.get("exampleLines"));
            dto.setTaskNames((List<String>) map.get("taskNames"));  // Add this line!
            dto.setIsPrimary((Boolean) map.get("isPrimary"));
            dto.setEvidenceStrength((String) map.get("evidenceStrength"));
            dtos.add(dto);
        }
        return dtos;
    }

    /**
     * Convert description line value maps to DTOs
     */
    private List<JobAnalysisResultDto.DescriptionLineValueDto> convertToDescriptionLineValueDtos(List<Map<String, Object>> lineMaps) {
        List<JobAnalysisResultDto.DescriptionLineValueDto> dtos = new ArrayList<>();
        for (Map<String, Object> map : lineMaps) {
            JobAnalysisResultDto.DescriptionLineValueDto dto = new JobAnalysisResultDto.DescriptionLineValueDto();
            dto.setSequence(((Number) map.get("sequence")).intValue());
            dto.setText((String) map.get("text"));
            dto.setSkillsShowcased((List<String>) map.get("skillsShowcased"));
            dto.setSkillCount(((Number) map.get("skillCount")).intValue());
            dto.setImpactLevel((String) map.get("impactLevel"));
            dto.setValueRating((String) map.get("valueRating"));
            dtos.add(dto);
        }
        return dtos;
    }

    /**
     * Convert missing opportunity maps to DTOs
     */
    private List<JobAnalysisResultDto.MissingSkillTaskOpportunityDto> convertToMissingOpportunityDtos(List<Map<String, Object>> oppMaps) {
        List<JobAnalysisResultDto.MissingSkillTaskOpportunityDto> dtos = new ArrayList<>();
        for (Map<String, Object> map : oppMaps) {
            JobAnalysisResultDto.MissingSkillTaskOpportunityDto dto = new JobAnalysisResultDto.MissingSkillTaskOpportunityDto();
            dto.setSkillName((String) map.get("skillName"));
            dto.setTaskId((String) map.get("taskId"));
            dto.setTaskName((String) map.get("taskName"));
            dto.setImportance(((Number) map.get("importance")).doubleValue());
            dto.setTaskCategory((String) map.get("taskCategory"));
            dto.setOccupationTitle((String) map.get("occupationTitle"));
            dtos.add(dto);
        }
        return dtos;
    }

    /**
     * Convert missing task maps to DTOs
     */
    private List<JobAnalysisResultDto.MissingTaskDto> convertToMissingTaskDtos(List<Map<String, Object>> taskMaps) {
        List<JobAnalysisResultDto.MissingTaskDto> dtos = new ArrayList<>();
        for (Map<String, Object> map : taskMaps) {
            JobAnalysisResultDto.MissingTaskDto dto = new JobAnalysisResultDto.MissingTaskDto();
            dto.setTaskId((String) map.get("taskId"));
            dto.setTaskName((String) map.get("taskName"));
            dto.setImportance(((Number) map.get("importance")).doubleValue());
            dto.setCategory((String) map.get("category"));
            dtos.add(dto);
        }
        return dtos;
    }

    /**
     * Convert missing activity maps to DTOs
     */
    private List<JobAnalysisResultDto.MissingActivityDto> convertToMissingActivityDtos(List<Map<String, Object>> activityMaps) {
        List<JobAnalysisResultDto.MissingActivityDto> dtos = new ArrayList<>();
        for (Map<String, Object> map : activityMaps) {
            JobAnalysisResultDto.MissingActivityDto dto = new JobAnalysisResultDto.MissingActivityDto();
            dto.setActivityId((String) map.get("activityId"));
            dto.setActivityName((String) map.get("activityName"));
            dto.setImportance(((Number) map.get("importance")).doubleValue());
            dtos.add(dto);
        }
        return dtos;
    }

    /**
     * Convert missing skill by category maps to DTOs
     */
    private List<JobAnalysisResultDto.MissingSkillByCategoryDto> convertToMissingSkillByCategoryDtos(List<Map<String, Object>> skillMaps) {
        List<JobAnalysisResultDto.MissingSkillByCategoryDto> dtos = new ArrayList<>();
        for (Map<String, Object> map : skillMaps) {
            JobAnalysisResultDto.MissingSkillByCategoryDto dto = new JobAnalysisResultDto.MissingSkillByCategoryDto();
            dto.setSkillName((String) map.get("skillName"));
            dto.setCategory((String) map.get("category"));
            dto.setRequiredByTasks(((Number) map.get("requiredByTasks")).intValue());
            dto.setAvgImportance(((Number) map.get("avgImportance")).doubleValue());
            dto.setTaskNames((List<String>) map.get("taskNames"));
            dtos.add(dto);
        }
        return dtos;
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
     * Also maps skills to O*NET tasks
     * Returns the line mappings for storage in MySQL
     */
    private List<Map<String, Object>> analyzeDescriptionLines(String experienceId, String description, String socCode,
                                                               String occupationTitle, String jobTitle,
                                                               List<Map<String, Object>> skills) {
        if (description == null || description.trim().isEmpty()) {
            log.warn("No description to analyze for experience {}", experienceId);
            return new ArrayList<>();
        }

        // Step 1: Parse description into lines
        List<String> descriptionLines = neo4jGraphService.parseDescriptionIntoLines(experienceId, description);
        if (descriptionLines.isEmpty()) {
            log.warn("No description lines parsed for experience {}", experienceId);
            return new ArrayList<>();
        }

        // Step 2: Fetch O*NET activities and tasks for this occupation
        Map<String, List<String>> onetData = neo4jGraphService.fetchONetActivitiesAndTasks(socCode);
        List<String> activities = onetData.get("activities");
        List<String> tasks = onetData.get("tasks");

        if (activities.isEmpty() && tasks.isEmpty()) {
            log.warn("No O*NET activities/tasks found for SOC {}. Skipping line mapping.", socCode);
            return new ArrayList<>();
        }

        // Step 3: Call LLM to map description lines to O*NET activities/tasks AND skills to tasks
        try {
            String prompt = loadPromptTemplate("prompts/description-activity-mapping-prompt.txt");
            prompt = prompt.replace("{jobTitle}", jobTitle)
                    .replace("{occupationTitle}", occupationTitle)
                    .replace("{socCode}", socCode)
                    .replace("{extractedSkills}", formatSkillsForPrompt(skills))
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

            // Store skill-to-task mappings in Neo4j
            if (mappingDto.getSkillToTaskMappings() != null && !mappingDto.getSkillToTaskMappings().isEmpty()) {
                neo4jGraphService.storeSkillToTaskMappings(experienceId, mappingDto.getSkillToTaskMappings());
                log.info("Successfully mapped {} skills to O*NET tasks", mappingDto.getSkillToTaskMappings().size());
            }

            return lineMappings;

        } catch (Exception e) {
            log.error("Error mapping description lines to O*NET activities/tasks", e);
            return new ArrayList<>();  // Return empty list on error
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

    /**
     * Build complete JobAnalysisResultDto from all analysis components
     */
    private JobAnalysisResultDto buildAnalysisResultDto(
            Resume resume,
            ResumeAnalysisExperience experience,
            NormalizedJobDto normalized,
            List<Map<String, Object>> skills,
            List<Map<String, Object>> lineMappings,
            Map<String, Object> evaluation) {

        JobAnalysisResultDto dto = new JobAnalysisResultDto();
        dto.setResumeId(resume.getId());
        dto.setExperienceId(experience.getId());

        // Experience details
        dto.setJobTitle(experience.getJobTitle());
        dto.setCompanyName(experience.getCompanyName());
        dto.setStartDate(experience.getStartDate());
        dto.setEndDate(experience.getEndDate());

        // Normalization data
        dto.setNormalizedTitle(normalized.getNormalizedTitle());
        dto.setPrimarySocCode(normalized.getSocCodes().get(0).getCode());
        dto.setSeniorityLevel(normalized.getSeniority());
        dto.setTechnicalDepth(normalized.getTechnicalDepth() != null ?
                BigDecimal.valueOf(normalized.getTechnicalDepth()) : null);
        dto.setHasLeadership(normalized.getHasLeadership());
        dto.setLeadershipScope(normalized.getLeadershipScope());

        // Scores
        dto.setImpactScore(BigDecimal.valueOf(
                evaluation.get("impactScore") != null ? ((Number) evaluation.get("impactScore")).doubleValue() : 0.0));
        dto.setTechnicalDepthScore(BigDecimal.valueOf(
                evaluation.get("technicalDepthScore") != null ? ((Number) evaluation.get("technicalDepthScore")).doubleValue() : 0.0));
        dto.setLeadershipScore(BigDecimal.valueOf(
                evaluation.get("leadershipScore") != null ? ((Number) evaluation.get("leadershipScore")).doubleValue() : 0.0));
        dto.setOverallScore(BigDecimal.valueOf(
                evaluation.get("overallScore") != null ? ((Number) evaluation.get("overallScore")).doubleValue() : 0.0));

        // Analysis text
        dto.setRecruiterSummary((String) evaluation.get("recruiterSummary"));

        // Lists
        dto.setKeyStrengths((List<String>) evaluation.get("keyStrengths"));
        dto.setImprovementAreas((List<String>) evaluation.get("improvementAreas"));
        dto.setJobFamilies(normalized.getJobFamilies());
        dto.setKeyResponsibilities(normalized.getKeyResponsibilities());

        // Industry Classification
        dto.setPrimaryIndustry(normalized.getPrimaryIndustry());
        dto.setIndustryVertical(normalized.getIndustryVertical());
        dto.setIndustrySectors(normalized.getIndustrySectors());

        // SOC codes
        List<JobAnalysisResultDto.SocCodeDto> socCodeDtos = new ArrayList<>();
        for (NormalizedJobDto.SocCodeMapping socMapping : normalized.getSocCodes()) {
            JobAnalysisResultDto.SocCodeDto socDto = new JobAnalysisResultDto.SocCodeDto();
            socDto.setCode(socMapping.getCode());
            socDto.setTitle(socMapping.getTitle());
            socDto.setConfidence(socMapping.getConfidence());
            socCodeDtos.add(socDto);
        }
        dto.setSocCodes(socCodeDtos);

        // Skills
        List<JobAnalysisResultDto.SkillDetailDto> skillDtos = new ArrayList<>();
        for (Map<String, Object> skill : skills) {
            JobAnalysisResultDto.SkillDetailDto skillDto = new JobAnalysisResultDto.SkillDetailDto();
            skillDto.setName((String) skill.get("name"));
            skillDto.setCategory((String) skill.get("category"));
            skillDto.setSubcategory((String) skill.get("subcategory"));
            skillDto.setProficiencyLevel(((Number) skill.get("proficiencyLevel")).intValue());
            skillDto.setIsTechnical((Boolean) skill.get("isTechnical"));
            skillDto.setIsPrimary((Boolean) skill.get("isPrimary"));
            skillDto.setMentionedCount(((Number) skill.get("mentionedCount")).intValue());
            skillDtos.add(skillDto);
        }
        dto.setExtractedSkills(skillDtos);

        // Line mappings
        List<JobAnalysisResultDto.LineMappingDto> lineMappingDtos = new ArrayList<>();
        for (Map<String, Object> lineMapping : lineMappings) {
            JobAnalysisResultDto.LineMappingDto lineMappingDto = new JobAnalysisResultDto.LineMappingDto();
            lineMappingDto.setSequence(((Number) lineMapping.get("sequence")).intValue());
            lineMappingDto.setText((String) lineMapping.get("text"));
            lineMappingDto.setImpactMetrics((String) lineMapping.get("impactMetrics"));
            lineMappingDto.setHasQuantifiableImpact((Boolean) lineMapping.get("hasQuantifiableImpact"));
            lineMappingDto.setImpactLevel((String) lineMapping.get("impactLevel"));
            lineMappingDto.setScope((String) lineMapping.get("scope"));

            // Activities
            List<Map<String, Object>> activities = (List<Map<String, Object>>) lineMapping.get("activities");
            if (activities != null) {
                List<JobAnalysisResultDto.LineMappingDto.ActivityMappingDto> activityDtos = new ArrayList<>();
                for (Map<String, Object> activity : activities) {
                    JobAnalysisResultDto.LineMappingDto.ActivityMappingDto activityDto =
                            new JobAnalysisResultDto.LineMappingDto.ActivityMappingDto();
                    activityDto.setActivityName((String) activity.get("activityName"));
                    activityDto.setActivityId((String) activity.get("activityId"));
                    activityDto.setConfidence(((Number) activity.get("confidence")).doubleValue());
                    activityDto.setReasoning((String) activity.get("reasoning"));
                    activityDtos.add(activityDto);
                }
                lineMappingDto.setActivities(activityDtos);
            }

            // Tasks
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) lineMapping.get("tasks");
            if (tasks != null) {
                List<JobAnalysisResultDto.LineMappingDto.TaskMappingDto> taskDtos = new ArrayList<>();
                for (Map<String, Object> task : tasks) {
                    JobAnalysisResultDto.LineMappingDto.TaskMappingDto taskDto =
                            new JobAnalysisResultDto.LineMappingDto.TaskMappingDto();
                    taskDto.setTaskName((String) task.get("taskName"));
                    taskDto.setTaskId((String) task.get("taskId"));
                    taskDto.setConfidence(((Number) task.get("confidence")).doubleValue());
                    taskDto.setReasoning((String) task.get("reasoning"));
                    taskDtos.add(taskDto);
                }
                lineMappingDto.setTasks(taskDtos);
            }

            // Recruiter Insights
            Map<String, Object> recruiterInsights = (Map<String, Object>) lineMapping.get("recruiterInsights");
            if (recruiterInsights != null) {
                JobAnalysisResultDto.LineMappingDto.RecruiterInsightsDto insightsDto =
                        new JobAnalysisResultDto.LineMappingDto.RecruiterInsightsDto();

                // Strong signals
                List<Map<String, Object>> strongSignals = (List<Map<String, Object>>) recruiterInsights.get("strongSignals");
                if (strongSignals != null) {
                    List<JobAnalysisResultDto.LineMappingDto.RecruiterInsightsDto.SignalDto> signalDtos = new ArrayList<>();
                    for (Map<String, Object> signal : strongSignals) {
                        JobAnalysisResultDto.LineMappingDto.RecruiterInsightsDto.SignalDto signalDto =
                                new JobAnalysisResultDto.LineMappingDto.RecruiterInsightsDto.SignalDto();
                        signalDto.setCategory((String) signal.get("category"));
                        signalDto.setInsight((String) signal.get("insight"));
                        signalDto.setWeight((String) signal.get("weight"));
                        signalDtos.add(signalDto);
                    }
                    insightsDto.setStrongSignals(signalDtos);
                }

                insightsDto.setPotentialQuestions((List<String>) recruiterInsights.get("potentialQuestions"));
                insightsDto.setRecruiterAppealScore(
                        recruiterInsights.get("recruiterAppealScore") != null ?
                                ((Number) recruiterInsights.get("recruiterAppealScore")).intValue() : null
                );
                insightsDto.setAppealReasoning((String) recruiterInsights.get("appealReasoning"));
                insightsDto.setBestFitRoles((List<String>) recruiterInsights.get("bestFitRoles"));
                insightsDto.setRedFlags((List<String>) recruiterInsights.get("redFlags"));

                lineMappingDto.setRecruiterInsights(insightsDto);
            }

            lineMappingDtos.add(lineMappingDto);
        }
        dto.setDescriptionLineMappings(lineMappingDtos);

        dto.setCreatedAt(java.time.LocalDateTime.now());
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

        // Industry Classification
        normalized.setPrimaryIndustry(jobNorm.getPrimaryIndustry());
        normalized.setIndustryVertical(jobNorm.getIndustryVertical());
        normalized.setIndustrySectors(jobNorm.getIndustrySectors());

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

            // Convert recruiterInsights
            if (lineDto.getRecruiterInsights() != null) {
                Map<String, Object> recruiterInsights = new HashMap<>();
                com.resumebuddy.model.dto.DescriptionLineMappingDto.RecruiterInsightsDto insights = lineDto.getRecruiterInsights();

                // Strong signals
                if (insights.getStrongSignals() != null) {
                    List<Map<String, Object>> signals = new ArrayList<>();
                    for (com.resumebuddy.model.dto.DescriptionLineMappingDto.SignalDto signal : insights.getStrongSignals()) {
                        Map<String, Object> signalMap = new HashMap<>();
                        signalMap.put("category", signal.getCategory());
                        signalMap.put("insight", signal.getInsight());
                        signalMap.put("weight", signal.getWeight());
                        signals.add(signalMap);
                    }
                    recruiterInsights.put("strongSignals", signals);
                }

                recruiterInsights.put("potentialQuestions", insights.getPotentialQuestions());
                recruiterInsights.put("recruiterAppealScore", insights.getRecruiterAppealScore());
                recruiterInsights.put("appealReasoning", insights.getAppealReasoning());
                recruiterInsights.put("bestFitRoles", insights.getBestFitRoles());
                recruiterInsights.put("redFlags", insights.getRedFlags());

                lineMapping.put("recruiterInsights", recruiterInsights);
            }

            lineMappings.add(lineMapping);
        }
        return lineMappings;
    }

    /**
     * Format skills for LLM prompt
     */
    private String formatSkillsForPrompt(List<Map<String, Object>> skills) {
        return skills.stream()
            .map(s -> (String) s.get("name"))
            .collect(java.util.stream.Collectors.joining(", "));
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
