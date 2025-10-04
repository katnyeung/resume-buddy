package com.resumebuddy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.model.*;
import com.resumebuddy.model.dto.*;
import com.resumebuddy.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeAnalysisService {

    private final RestTemplate restTemplate;
    private final ResumeRepository resumeRepository;
    private final ResumeLineRepository resumeLineRepository;
    private final ResumeAnalysisRepository resumeAnalysisRepository;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    @Value("${app.openai.api-key}")
    private String openaiApiKey;

    @Value("${app.openai.model:gpt-4}")
    private String openaiModel;

    @Value("${app.openai.base-url:https://api.openai.com/v1}")
    private String openaiBaseUrl;

    private String systemPromptTemplate;
    private String analysisPromptTemplate;

    @PostConstruct
    public void loadPromptTemplates() {
        try {
            // Load system prompt
            ClassPathResource systemPromptResource = new ClassPathResource("prompts/system-prompt.txt");
            systemPromptTemplate = StreamUtils.copyToString(
                systemPromptResource.getInputStream(),
                StandardCharsets.UTF_8
            );
            log.info("Loaded system prompt template");

            // Load analysis prompt
            ClassPathResource analysisPromptResource = new ClassPathResource("prompts/resume-analysis-prompt.txt");
            analysisPromptTemplate = StreamUtils.copyToString(
                analysisPromptResource.getInputStream(),
                StandardCharsets.UTF_8
            );
            log.info("Loaded analysis prompt template");

        } catch (IOException e) {
            log.error("Error loading prompt templates", e);
            // Fallback to hardcoded prompts if files not found
            systemPromptTemplate = "You are a resume analysis expert. You analyze resumes line by line and provide structured analysis in JSON format.";
            analysisPromptTemplate = "Analyze the following resume:\n{resumeLines}";
        }
    }

    // ==================== AI ANALYSIS METHODS ====================

    @Transactional
    public List<LineAnalysisDto> analyzeResume(String resumeId) {
        log.info("Starting AI analysis for resume ID: {}", resumeId);

        // Delete existing analysis FIRST (before LLM call) to avoid conflicts
        resumeAnalysisRepository.findByResumeId(resumeId).ifPresent(existing -> {
            log.info("Deleting existing analysis for resume ID: {}", resumeId);
            resumeAnalysisRepository.delete(existing);
            resumeAnalysisRepository.flush();
        });

        // Get resume entity
        Resume resume = resumeRepository.findById(resumeId)
            .orElseThrow(() -> new RuntimeException("Resume not found with ID: " + resumeId));

        // Get all lines for the resume
        List<ResumeLine> resumeLines = resumeLineRepository.findByResumeIdOrderByLineNumber(resumeId);

        if (resumeLines.isEmpty()) {
            log.warn("No resume lines found for resume ID: {}", resumeId);
            return Collections.emptyList();
        }

        // Build prompt with all resume lines (LLM will skip empty ones)
        String prompt = buildAnalysisPrompt(resumeLines);

        // Call OpenAI API - now returns both lineAnalysis and structuredData
        Map<String, Object> response = callOpenAI(prompt, resumeLines.size());

        if (response.isEmpty()) {
            log.error("OpenAI response is empty for resume ID: {}", resumeId);
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<LineAnalysisDto> analyses = (List<LineAnalysisDto>) response.get("lineAnalysis");
        JsonNode structuredData = (JsonNode) response.get("structuredData");

        // Update resume lines with analysis results
        updateResumeLines(resumeLines, analyses);

        // Create structured analysis from LLM's structured data
        createStructuredAnalysisFromLLM(resume, structuredData);

        // Update resume status to ANALYZED
        resume.setStatus(ResumeStatus.ANALYZED.name());
        resumeRepository.save(resume);

        log.info("Completed AI analysis for resume ID: {}. Analyzed {} lines. Status updated to ANALYZED", resumeId, analyses.size());

        return analyses;
    }

    @Transactional
    protected void createStructuredAnalysisFromLLM(Resume resume, JsonNode structuredData) {
        log.info("Creating structured analysis from LLM data for resume ID: {}", resume.getId());

        // Create new analysis entity (old one already deleted in analyzeResume)
        ResumeAnalysis analysis = new ResumeAnalysis();
        analysis.setResume(resume);

        // Extract contact information
        JsonNode contactNode = structuredData.path("contact");
        if (!contactNode.isMissingNode()) {
            analysis.setName(getTextOrNull(contactNode, "name"));
            analysis.setEmail(getTextOrNull(contactNode, "email"));
            analysis.setPhone(getTextOrNull(contactNode, "phone"));
            analysis.setLinkedinUrl(getTextOrNull(contactNode, "linkedinUrl"));
            analysis.setGithubUrl(getTextOrNull(contactNode, "githubUrl"));
            analysis.setWebsiteUrl(getTextOrNull(contactNode, "websiteUrl"));
        }

        // Extract summary
        analysis.setSummary(getTextOrNull(structuredData, "summary"));

        // Extract and add experiences
        JsonNode experiencesNode = structuredData.path("experiences");
        if (experiencesNode.isArray()) {
            for (JsonNode expNode : experiencesNode) {
                ResumeAnalysisExperience experience = new ResumeAnalysisExperience();
                experience.setAnalysis(analysis);
                experience.setJobTitle(getTextOrNull(expNode, "jobTitle"));
                experience.setCompanyName(getTextOrNull(expNode, "companyName"));
                experience.setStartDate(getTextOrNull(expNode, "startDate"));
                experience.setEndDate(getTextOrNull(expNode, "endDate"));
                experience.setDescription(getTextOrNull(expNode, "description"));
                analysis.getExperiences().add(experience);
            }
        }

        // Extract and add skills
        JsonNode skillsNode = structuredData.path("skills");
        if (skillsNode.isArray()) {
            for (JsonNode skillNode : skillsNode) {
                ResumeAnalysisSkill skill = new ResumeAnalysisSkill();
                skill.setAnalysis(analysis);
                skill.setSkillName(getTextOrNull(skillNode, "skillName"));
                skill.setCategory(getTextOrNull(skillNode, "category"));
                analysis.getSkills().add(skill);
            }
        }

        // Extract and add educations
        JsonNode educationsNode = structuredData.path("educations");
        if (educationsNode.isArray()) {
            for (JsonNode eduNode : educationsNode) {
                ResumeAnalysisEducation education = new ResumeAnalysisEducation();
                education.setAnalysis(analysis);
                education.setDegree(getTextOrNull(eduNode, "degree"));
                education.setInstitution(getTextOrNull(eduNode, "institution"));
                education.setGraduationDate(getTextOrNull(eduNode, "graduationDate"));
                education.setDescription(getTextOrNull(eduNode, "description"));
                analysis.getEducations().add(education);
            }
        }

        // Extract and add certifications
        JsonNode certificationsNode = structuredData.path("certifications");
        if (certificationsNode.isArray()) {
            for (JsonNode certNode : certificationsNode) {
                ResumeAnalysisCertification certification = new ResumeAnalysisCertification();
                certification.setAnalysis(analysis);
                certification.setCertificationName(getTextOrNull(certNode, "certificationName"));
                certification.setIssuingOrganization(getTextOrNull(certNode, "issuingOrganization"));
                certification.setIssueDate(getTextOrNull(certNode, "issueDate"));
                certification.setCredentialId(getTextOrNull(certNode, "credentialId"));
                analysis.getCertifications().add(certification);
            }
        }

        // Extract and add projects
        JsonNode projectsNode = structuredData.path("projects");
        if (projectsNode.isArray()) {
            for (JsonNode projNode : projectsNode) {
                ResumeAnalysisProject project = new ResumeAnalysisProject();
                project.setAnalysis(analysis);
                project.setProjectName(getTextOrNull(projNode, "projectName"));
                project.setDescription(getTextOrNull(projNode, "description"));
                project.setTechnologiesUsed(getTextOrNull(projNode, "technologiesUsed"));
                project.setProjectUrl(getTextOrNull(projNode, "projectUrl"));
                analysis.getProjects().add(project);
            }
        }

        // Save once with all child entities (CASCADE.ALL will save children automatically)
        resumeAnalysisRepository.save(analysis);

        log.info("Structured analysis created from LLM for resume ID: {}", resume.getId());
    }

    private String buildAnalysisPrompt(List<ResumeLine> resumeLines) {
        // Build the resume lines content
        StringBuilder linesBuilder = new StringBuilder();
        for (ResumeLine line : resumeLines) {
            linesBuilder.append(String.format("Line %d: %s\n", line.getLineNumber(), line.getContent()));
        }

        // Replace template variables
        String prompt = analysisPromptTemplate;
        prompt = replaceVariable(prompt, "resumeLines", linesBuilder.toString());
        prompt = replaceVariable(prompt, "lineCount", String.valueOf(resumeLines.size()));

        return prompt;
    }

    private String replaceVariable(String template, String variableName, String value) {
        return template.replace("{" + variableName + "}", value);
    }

    private Map<String, Object> callOpenAI(String prompt, int expectedLineCount) {
        try {
            // Build OpenAI API request
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", openaiModel);
            requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPromptTemplate),
                Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("temperature", 0.3);  // Lower temperature for more consistent results
            requestBody.put("max_tokens", 16000);  // Increased for large resumes with dual output

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Call OpenAI API
            String url = openaiBaseUrl + "/chat/completions";
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseOpenAIResponse(response.getBody());
            } else {
                log.error("OpenAI API returned non-OK status: {}", response.getStatusCode());
                return Collections.emptyMap();
            }

        } catch (Exception e) {
            log.error("Error calling OpenAI API", e);
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> parseOpenAIResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Check if response was truncated due to max_tokens limit
            JsonNode finishReasonNode = root.path("choices").get(0).path("finish_reason");
            String finishReason = finishReasonNode.asText();
            if ("length".equals(finishReason)) {
                log.warn("OpenAI response was truncated due to max_tokens limit. Consider increasing max_tokens.");
            }

            JsonNode contentNode = root.path("choices").get(0).path("message").path("content");
            String content = contentNode.asText();

            log.debug("LLM response content length: {} characters", content.length());

            // Parse the JSON object from the content (now returns object with lineAnalysis and structuredData)
            JsonNode responseObject = objectMapper.readTree(content);

            // Parse lineAnalysis array
            JsonNode lineAnalysisArray = responseObject.path("lineAnalysis");
            List<LineAnalysisDto> analyses = new ArrayList<>();
            for (JsonNode node : lineAnalysisArray) {
                LineAnalysisDto dto = new LineAnalysisDto();
                dto.setLineNumber(node.path("lineNumber").asInt());
                dto.setSectionType(node.path("sectionType").asText(null));
                dto.setGroupId(node.path("groupId").isNull() ? null : node.path("groupId").asInt());
                dto.setGroupType(node.path("groupType").asText(null));
                dto.setAnalysisNotes(node.path("analysisNotes").asText(null));
                analyses.add(dto);
            }

            // Parse structuredData
            JsonNode structuredDataNode = responseObject.path("structuredData");

            Map<String, Object> result = new HashMap<>();
            result.put("lineAnalysis", analyses);
            result.put("structuredData", structuredDataNode);

            log.info("Successfully parsed {} line analyses from LLM response", analyses.size());
            return result;

        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            log.error("JSON parsing error - likely truncated response. Increase max_tokens. Error: {}", e.getMessage());
            return Collections.emptyMap();
        } catch (Exception e) {
            log.error("Error parsing OpenAI response", e);
            return Collections.emptyMap();
        }
    }

    private void updateResumeLines(List<ResumeLine> resumeLines, List<LineAnalysisDto> analyses) {
        LocalDateTime now = LocalDateTime.now();

        // Create a map for quick lookup
        Map<Integer, LineAnalysisDto> analysisMap = new HashMap<>();
        for (LineAnalysisDto analysis : analyses) {
            analysisMap.put(analysis.getLineNumber(), analysis);
        }

        // Update each resume line with its analysis
        for (ResumeLine line : resumeLines) {
            LineAnalysisDto analysis = analysisMap.get(line.getLineNumber());
            if (analysis != null) {
                line.setSectionType(analysis.getSectionType());
                line.setGroupId(analysis.getGroupId());
                line.setGroupType(analysis.getGroupType());
                line.setAnalysisNotes(analysis.getAnalysisNotes());
                line.setAnalyzedAt(now);
            }
        }

        // Save all updated lines
        resumeLineRepository.saveAll(resumeLines);
    }

    public boolean isAnalyzed(String resumeId) {
        List<ResumeLine> lines = resumeLineRepository.findByResumeIdOrderByLineNumber(resumeId);
        return !lines.isEmpty() && lines.stream().anyMatch(line -> line.getAnalyzedAt() != null);
    }

    private String getTextOrNull(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull() || fieldNode.asText().isEmpty()) {
            return null;
        }
        return fieldNode.asText();
    }

    // ==================== RETRIEVAL METHODS ====================

    @Transactional(readOnly = true)
    public ResumeAnalysisDto getStructuredAnalysis(String resumeId) {
        log.info("Retrieving structured analysis for resume ID: {}", resumeId);

        return resumeAnalysisRepository.findByResumeId(resumeId)
            .map(this::convertToDto)
            .orElse(null);
    }

    public boolean analysisExists(String resumeId) {
        return resumeAnalysisRepository.existsByResumeId(resumeId);
    }

    private ResumeAnalysisDto convertToDto(ResumeAnalysis analysis) {
        ResumeAnalysisDto dto = new ResumeAnalysisDto();
        dto.setId(analysis.getId());
        dto.setResumeId(analysis.getResume().getId());
        dto.setName(analysis.getName());
        dto.setEmail(analysis.getEmail());
        dto.setPhone(analysis.getPhone());
        dto.setLinkedinUrl(analysis.getLinkedinUrl());
        dto.setGithubUrl(analysis.getGithubUrl());
        dto.setWebsiteUrl(analysis.getWebsiteUrl());
        dto.setSummary(analysis.getSummary());
        dto.setCreatedAt(analysis.getCreatedAt());
        dto.setUpdatedAt(analysis.getUpdatedAt());

        // Convert experiences - sort by date (most recent first)
        if (analysis.getExperiences() != null) {
            dto.setExperiences(analysis.getExperiences().stream()
                .sorted(Comparator.comparing(
                    (ResumeAnalysisExperience exp) -> parseDate(exp.getStartDate()),
                    Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .map(this::convertExperienceToDto)
                .collect(Collectors.toList()));
        }

        // Convert skills
        if (analysis.getSkills() != null) {
            dto.setSkills(analysis.getSkills().stream()
                .map(this::convertSkillToDto)
                .collect(Collectors.toList()));
        }

        // Convert educations
        if (analysis.getEducations() != null) {
            dto.setEducations(analysis.getEducations().stream()
                .map(this::convertEducationToDto)
                .collect(Collectors.toList()));
        }

        // Convert certifications
        if (analysis.getCertifications() != null) {
            dto.setCertifications(analysis.getCertifications().stream()
                .map(this::convertCertificationToDto)
                .collect(Collectors.toList()));
        }

        // Convert projects
        if (analysis.getProjects() != null) {
            dto.setProjects(analysis.getProjects().stream()
                .map(this::convertProjectToDto)
                .collect(Collectors.toList()));
        }

        return dto;
    }

    private ExperienceDto convertExperienceToDto(ResumeAnalysisExperience experience) {
        ExperienceDto dto = new ExperienceDto();
        dto.setId(experience.getId());
        dto.setJobTitle(experience.getJobTitle());
        dto.setCompanyName(experience.getCompanyName());
        dto.setStartDate(experience.getStartDate());
        dto.setEndDate(experience.getEndDate());
        dto.setDescription(experience.getDescription());
        return dto;
    }

    private SkillDto convertSkillToDto(ResumeAnalysisSkill skill) {
        SkillDto dto = new SkillDto();
        dto.setId(skill.getId());
        dto.setSkillName(skill.getSkillName());
        dto.setCategory(skill.getCategory());
        return dto;
    }

    private EducationDto convertEducationToDto(ResumeAnalysisEducation education) {
        EducationDto dto = new EducationDto();
        dto.setId(education.getId());
        dto.setDegree(education.getDegree());
        dto.setInstitution(education.getInstitution());
        dto.setGraduationDate(education.getGraduationDate());
        dto.setDescription(education.getDescription());
        return dto;
    }

    private CertificationDto convertCertificationToDto(ResumeAnalysisCertification certification) {
        CertificationDto dto = new CertificationDto();
        dto.setId(certification.getId());
        dto.setCertificationName(certification.getCertificationName());
        dto.setIssuingOrganization(certification.getIssuingOrganization());
        dto.setIssueDate(certification.getIssueDate());
        dto.setCredentialId(certification.getCredentialId());
        return dto;
    }

    private ProjectDto convertProjectToDto(ResumeAnalysisProject project) {
        ProjectDto dto = new ProjectDto();
        dto.setId(project.getId());
        dto.setProjectName(project.getProjectName());
        dto.setDescription(project.getDescription());
        dto.setTechnologiesUsed(project.getTechnologiesUsed());
        dto.setProjectUrl(project.getProjectUrl());
        return dto;
    }

    /**
     * Parse various date formats commonly found in resumes
     * Supports: "YYYY-MM-DD", "YYYY-MM", "YYYY", "Month YYYY", "MM/YYYY", etc.
     * Returns a comparable date for sorting
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return LocalDate.MIN; // Null dates go to the end
        }

        String normalized = dateStr.trim();

        try {
            // Try ISO date format: 2021-10-15
            return LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            // Continue to next format
        }

        try {
            // Try year-month format: 2021-10
            YearMonth ym = YearMonth.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM"));
            return ym.atDay(1);
        } catch (DateTimeParseException e) {
            // Continue to next format
        }

        try {
            // Try month/year format: 10/2021
            YearMonth ym = YearMonth.parse(normalized, DateTimeFormatter.ofPattern("MM/yyyy"));
            return ym.atDay(1);
        } catch (DateTimeParseException e) {
            // Continue to next format
        }

        try {
            // Try month year format: "October 2021"
            YearMonth ym = YearMonth.parse(normalized, DateTimeFormatter.ofPattern("MMMM yyyy"));
            return ym.atDay(1);
        } catch (DateTimeParseException e) {
            // Continue to next format
        }

        try {
            // Try just year: 2021
            int year = Integer.parseInt(normalized);
            return LocalDate.of(year, 1, 1);
        } catch (NumberFormatException e) {
            // Continue to next format
        }

        // If all parsing fails, return MIN date
        log.warn("Could not parse date: {}", dateStr);
        return LocalDate.MIN;
    }
}
