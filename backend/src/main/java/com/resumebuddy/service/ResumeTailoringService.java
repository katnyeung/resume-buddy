package com.resumebuddy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.model.ResumeAnalysis;
import com.resumebuddy.model.ResumeAnalysisCertification;
import com.resumebuddy.model.ResumeAnalysisEducation;
import com.resumebuddy.model.ResumeAnalysisExperience;
import com.resumebuddy.model.ResumeAnalysisProject;
import com.resumebuddy.model.ResumeAnalysisSkill;
import com.resumebuddy.model.TailoredResume;
import com.resumebuddy.model.dto.TailorResumeRequestDto;
import com.resumebuddy.model.dto.TailoredResumeResponseDto;
import com.resumebuddy.repository.ResumeAnalysisCertificationRepository;
import com.resumebuddy.repository.ResumeAnalysisEducationRepository;
import com.resumebuddy.repository.ResumeAnalysisExperienceRepository;
import com.resumebuddy.repository.ResumeAnalysisProjectRepository;
import com.resumebuddy.repository.ResumeAnalysisRepository;
import com.resumebuddy.repository.ResumeAnalysisSkillRepository;
import com.resumebuddy.repository.TailoredResumeRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResumeTailoringService {

    private final ResumeAnalysisRepository resumeAnalysisRepository;
    private final ResumeAnalysisExperienceRepository experienceRepository;
    private final ResumeAnalysisSkillRepository skillRepository;
    private final ResumeAnalysisEducationRepository educationRepository;
    private final ResumeAnalysisProjectRepository projectRepository;
    private final ResumeAnalysisCertificationRepository certificationRepository;
    private final TailoredResumeRepository tailoredResumeRepository;
    private final UserCreditService userCreditService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.openai.api-key}")
    private String apiKey;

    @Value("${app.openai.base-url}")
    private String baseUrl;

    @Value("${app.openai.model}")
    private String model;

    @Value("${app.token-costs.resume-tailoring:30}")
    private int tailoringCost;

    /**
     * Tailor a resume for a specific job description
     */
    @Transactional
    public TailoredResumeResponseDto tailorResume(String resumeId, String userId, TailorResumeRequestDto request) {
        log.info("Starting resume tailoring for resume {} by user {}", resumeId, userId);

        // Check credits
        if (!userCreditService.hasEnoughCredits(userId, BigDecimal.valueOf(tailoringCost))) {
            throw new UserCreditService.InsufficientCreditsException(
                String.format("Insufficient credits. Required: %d", tailoringCost));
        }

        // Load resume analysis data
        ResumeAnalysis analysis = resumeAnalysisRepository.findByResumeId(resumeId)
            .orElseThrow(() -> new RuntimeException("Resume analysis not found. Please analyze the resume first."));

        // Load related data
        List<ResumeAnalysisExperience> experiences = experienceRepository.findByAnalysisId(analysis.getId());
        List<ResumeAnalysisSkill> skills = skillRepository.findByAnalysisId(analysis.getId());
        List<ResumeAnalysisEducation> educations = educationRepository.findByAnalysisId(analysis.getId());
        List<ResumeAnalysisProject> projects = projectRepository.findByAnalysisId(analysis.getId());
        List<ResumeAnalysisCertification> certifications = certificationRepository.findByAnalysisId(analysis.getId());

        // Build prompt
        String prompt = buildTailoringPrompt(analysis, experiences, skills, educations, projects, certifications, request);

        // Call LLM
        String llmResponse = callLLM(prompt);

        // Parse response
        TailoredResumeResponseDto response = parseLLMResponse(llmResponse, resumeId);

        // Deduct credits
        userCreditService.deductCredits(
            userId,
            BigDecimal.valueOf(tailoringCost),
            "tailor-" + resumeId,
            "Resume tailoring for job application"
        );

        response.setCreditsUsed(tailoringCost);

        // Save to database for future retrieval
        TailoredResume savedResult = saveTailoredResume(userId, resumeId, request, response);
        response.setId(savedResult.getId());
        response.setCreatedAt(savedResult.getCreatedAt());

        log.info("Resume tailoring completed for resume {}. Credits used: {}. Saved as {}",
            resumeId, tailoringCost, savedResult.getId());

        return response;
    }

    /**
     * Get the cost of resume tailoring (for display on frontend)
     */
    public int getTailoringCost() {
        return tailoringCost;
    }

    /**
     * Build the LLM prompt with resume data
     */
    private String buildTailoringPrompt(
            ResumeAnalysis analysis,
            List<ResumeAnalysisExperience> experiences,
            List<ResumeAnalysisSkill> skills,
            List<ResumeAnalysisEducation> educations,
            List<ResumeAnalysisProject> projects,
            List<ResumeAnalysisCertification> certifications,
            TailorResumeRequestDto request) {

        try {
            String template = loadPromptTemplate("prompts/5-resume-tailoring.txt");

            // Build contact info
            StringBuilder contactInfo = new StringBuilder();
            if (analysis.getName() != null) contactInfo.append(analysis.getName()).append("\n");
            if (analysis.getEmail() != null) contactInfo.append(analysis.getEmail());
            if (analysis.getPhone() != null) contactInfo.append(" | ").append(analysis.getPhone());
            if (analysis.getLinkedinUrl() != null) contactInfo.append("\n").append(analysis.getLinkedinUrl());
            if (analysis.getGithubUrl() != null) contactInfo.append(" | ").append(analysis.getGithubUrl());

            // Build experiences
            StringBuilder experiencesStr = new StringBuilder();
            for (ResumeAnalysisExperience exp : experiences) {
                experiencesStr.append("**").append(exp.getJobTitle()).append("** at ")
                    .append(exp.getCompanyName()).append("\n");
                experiencesStr.append(exp.getStartDate()).append(" - ")
                    .append(exp.getEndDate() != null ? exp.getEndDate() : "Present").append("\n");
                if (exp.getDescription() != null) {
                    experiencesStr.append(exp.getDescription()).append("\n");
                }
                experiencesStr.append("\n");
            }

            // Build projects
            StringBuilder projectsStr = new StringBuilder();
            for (ResumeAnalysisProject proj : projects) {
                projectsStr.append("**").append(proj.getProjectName()).append("**\n");
                if (proj.getDescription() != null) {
                    projectsStr.append(proj.getDescription()).append("\n");
                }
                if (proj.getTechnologiesUsed() != null) {
                    projectsStr.append("Technologies: ").append(proj.getTechnologiesUsed()).append("\n");
                }
                if (proj.getProjectUrl() != null) {
                    projectsStr.append("URL: ").append(proj.getProjectUrl()).append("\n");
                }
                projectsStr.append("\n");
            }

            // Build skills
            String skillsStr = skills.stream()
                .map(ResumeAnalysisSkill::getSkillName)
                .collect(Collectors.joining(", "));

            // Build education
            StringBuilder educationStr = new StringBuilder();
            for (ResumeAnalysisEducation edu : educations) {
                educationStr.append("**").append(edu.getDegree()).append("**");
                if (edu.getDescription() != null && !edu.getDescription().isEmpty()) {
                    educationStr.append(" - ").append(edu.getDescription());
                }
                educationStr.append("\n");
                educationStr.append(edu.getInstitution());
                if (edu.getGraduationDate() != null) {
                    educationStr.append(", ").append(edu.getGraduationDate());
                }
                educationStr.append("\n\n");
            }

            // Build certifications
            String certsStr = certifications.stream()
                .map(c -> c.getCertificationName() + (c.getIssuingOrganization() != null ? " - " + c.getIssuingOrganization() : ""))
                .collect(Collectors.joining("\n"));

            // Replace placeholders
            return template
                .replace("{contactInfo}", contactInfo.toString())
                .replace("{summary}", analysis.getSummary() != null ? analysis.getSummary() : "")
                .replace("{experiences}", experiencesStr.toString())
                .replace("{projects}", projectsStr.toString())
                .replace("{skills}", skillsStr)
                .replace("{education}", educationStr.toString())
                .replace("{certifications}", certsStr)
                .replace("{jobDescription}", request.getJobDescription())
                .replace("{guidelines}", request.getGuidelines() != null ? request.getGuidelines() : "None specified");

        } catch (IOException e) {
            log.error("Error loading prompt template", e);
            throw new RuntimeException("Failed to load prompt template", e);
        }
    }

    /**
     * Call the LLM API
     */
    private String callLLM(String prompt) {
        try {
            Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(
                    Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.4,
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

    /**
     * Parse the LLM response into DTO
     */
    private TailoredResumeResponseDto parseLLMResponse(String llmResponse, String resumeId) {
        try {
            JsonNode root = objectMapper.readTree(llmResponse);

            TailoredResumeResponseDto.TailoredResumeResponseDtoBuilder builder = TailoredResumeResponseDto.builder()
                .resumeId(resumeId)
                .tailoredContent(root.path("tailoredContent").asText())
                .coverLetter(root.path("coverLetter").asText(""))
                .contactInfo(root.path("contactInfo").asText())
                .summary(root.path("summary").asText())
                .relevanceScore(root.path("relevanceScore").asInt(0));

            // Parse experiences
            List<TailoredResumeResponseDto.ExperienceSection> experiences = new ArrayList<>();
            JsonNode expNode = root.path("experiences");
            if (expNode.isArray()) {
                for (JsonNode exp : expNode) {
                    List<String> bullets = new ArrayList<>();
                    JsonNode bulletsNode = exp.path("bulletPoints");
                    if (bulletsNode.isArray()) {
                        for (JsonNode b : bulletsNode) {
                            bullets.add(b.asText());
                        }
                    }
                    experiences.add(TailoredResumeResponseDto.ExperienceSection.builder()
                        .jobTitle(exp.path("jobTitle").asText())
                        .company(exp.path("company").asText())
                        .dates(exp.path("dates").asText())
                        .bulletPoints(bullets)
                        .build());
                }
            }
            builder.experiences(experiences);

            // Parse skills
            List<String> skills = new ArrayList<>();
            JsonNode skillsNode = root.path("skills");
            if (skillsNode.isArray()) {
                for (JsonNode s : skillsNode) {
                    skills.add(s.asText());
                }
            }
            builder.skills(skills);

            // Parse projects
            List<TailoredResumeResponseDto.ProjectSection> projects = new ArrayList<>();
            JsonNode projNode = root.path("projects");
            if (projNode.isArray()) {
                for (JsonNode proj : projNode) {
                    List<String> techs = new ArrayList<>();
                    JsonNode techsNode = proj.path("technologies");
                    if (techsNode.isArray()) {
                        for (JsonNode t : techsNode) {
                            techs.add(t.asText());
                        }
                    }
                    projects.add(TailoredResumeResponseDto.ProjectSection.builder()
                        .projectName(proj.path("projectName").asText())
                        .description(proj.path("description").asText())
                        .technologies(techs)
                        .url(proj.path("url").asText())
                        .build());
                }
            }
            builder.projects(projects);

            // Parse educations
            List<TailoredResumeResponseDto.EducationSection> educations = new ArrayList<>();
            JsonNode eduNode = root.path("educations");
            if (eduNode.isArray()) {
                for (JsonNode edu : eduNode) {
                    educations.add(TailoredResumeResponseDto.EducationSection.builder()
                        .degree(edu.path("degree").asText())
                        .institution(edu.path("institution").asText())
                        .graduationDate(edu.path("graduationDate").asText())
                        .gpa(edu.path("gpa").asText())
                        .build());
                }
            }
            builder.educations(educations);

            // Parse certifications
            List<String> certs = new ArrayList<>();
            JsonNode certsNode = root.path("certifications");
            if (certsNode.isArray()) {
                for (JsonNode c : certsNode) {
                    certs.add(c.asText());
                }
            }
            builder.certifications(certs);

            // Parse highlighted skills
            List<String> highlightedSkills = new ArrayList<>();
            JsonNode highlightedNode = root.path("highlightedSkills");
            if (highlightedNode.isArray()) {
                for (JsonNode h : highlightedNode) {
                    highlightedSkills.add(h.asText());
                }
            }
            builder.highlightedSkills(highlightedSkills);

            // Parse matched keywords
            List<String> matchedKeywords = new ArrayList<>();
            JsonNode keywordsNode = root.path("matchedKeywords");
            if (keywordsNode.isArray()) {
                for (JsonNode k : keywordsNode) {
                    matchedKeywords.add(k.asText());
                }
            }
            builder.matchedKeywords(matchedKeywords);

            // Parse tailoring notes and suggested filename
            builder.tailoringNotes(root.path("tailoringNotes").asText(""));
            builder.suggestedFilename(root.path("suggestedFilename").asText(""));

            return builder.build();

        } catch (Exception e) {
            log.error("Error parsing LLM response", e);
            throw new RuntimeException("Failed to parse tailored resume response", e);
        }
    }

    /**
     * Load prompt template from resources
     */
    private String loadPromptTemplate(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Save tailored resume to database for future retrieval
     */
    private TailoredResume saveTailoredResume(String userId, String resumeId,
            TailorResumeRequestDto request, TailoredResumeResponseDto response) {
        try {
            TailoredResume entity = TailoredResume.builder()
                .userId(userId)
                .resumeId(resumeId)
                .jobDescription(request.getJobDescription())
                .guidelines(request.getGuidelines())
                .tailoredContent(response.getTailoredContent())
                .coverLetter(response.getCoverLetter())
                .contactInfo(response.getContactInfo())
                .summary(response.getSummary())
                .experiencesJson(objectMapper.writeValueAsString(response.getExperiences()))
                .projectsJson(objectMapper.writeValueAsString(response.getProjects()))
                .skillsJson(objectMapper.writeValueAsString(response.getSkills()))
                .educationsJson(objectMapper.writeValueAsString(response.getEducations()))
                .certificationsJson(objectMapper.writeValueAsString(response.getCertifications()))
                .highlightedSkills(objectMapper.writeValueAsString(response.getHighlightedSkills()))
                .matchedKeywords(objectMapper.writeValueAsString(response.getMatchedKeywords()))
                .relevanceScore(response.getRelevanceScore())
                .tailoringNotes(response.getTailoringNotes())
                .suggestedFilename(response.getSuggestedFilename())
                .creditsUsed(response.getCreditsUsed())
                .build();

            return tailoredResumeRepository.save(entity);
        } catch (Exception e) {
            log.error("Failed to save tailored resume", e);
            // Don't fail the whole operation if save fails
            return TailoredResume.builder().id("unsaved").build();
        }
    }

    /**
     * Get history of tailored resumes for a specific resume
     */
    public List<TailoredResumeResponseDto> getTailoredHistory(String resumeId, String userId) {
        List<TailoredResume> history = tailoredResumeRepository
            .findByResumeIdAndUserIdOrderByCreatedAtDesc(resumeId, userId);

        return history.stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    /**
     * Get all tailored resumes for a user
     */
    public List<TailoredResumeResponseDto> getAllUserTailoredResumes(String userId) {
        List<TailoredResume> history = tailoredResumeRepository
            .findByUserIdOrderByCreatedAtDesc(userId);

        return history.stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    /**
     * Get a specific saved tailored resume (no credits)
     */
    public TailoredResumeResponseDto getSavedTailoredResume(String tailoredId, String userId) {
        TailoredResume saved = tailoredResumeRepository.findByIdAndUserId(tailoredId, userId)
            .orElseThrow(() -> new RuntimeException("Tailored resume not found"));

        return convertToDto(saved);
    }

    /**
     * Convert entity to DTO
     */
    private TailoredResumeResponseDto convertToDto(TailoredResume entity) {
        try {
            TailoredResumeResponseDto.TailoredResumeResponseDtoBuilder builder = TailoredResumeResponseDto.builder()
                .id(entity.getId())
                .resumeId(entity.getResumeId())
                .tailoredContent(entity.getTailoredContent())
                .coverLetter(entity.getCoverLetter())
                .contactInfo(entity.getContactInfo())
                .summary(entity.getSummary())
                .relevanceScore(entity.getRelevanceScore() != null ? entity.getRelevanceScore() : 0)
                .tailoringNotes(entity.getTailoringNotes())
                .suggestedFilename(entity.getSuggestedFilename())
                .creditsUsed(entity.getCreditsUsed() != null ? entity.getCreditsUsed() : 0)
                .createdAt(entity.getCreatedAt());

            // Parse JSON arrays
            if (entity.getExperiencesJson() != null) {
                builder.experiences(objectMapper.readValue(entity.getExperiencesJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class,
                        TailoredResumeResponseDto.ExperienceSection.class)));
            }
            if (entity.getProjectsJson() != null) {
                builder.projects(objectMapper.readValue(entity.getProjectsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class,
                        TailoredResumeResponseDto.ProjectSection.class)));
            }
            if (entity.getSkillsJson() != null) {
                builder.skills(objectMapper.readValue(entity.getSkillsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
            }
            if (entity.getEducationsJson() != null) {
                builder.educations(objectMapper.readValue(entity.getEducationsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class,
                        TailoredResumeResponseDto.EducationSection.class)));
            }
            if (entity.getCertificationsJson() != null) {
                builder.certifications(objectMapper.readValue(entity.getCertificationsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
            }
            if (entity.getHighlightedSkills() != null) {
                builder.highlightedSkills(objectMapper.readValue(entity.getHighlightedSkills(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
            }
            if (entity.getMatchedKeywords() != null) {
                builder.matchedKeywords(objectMapper.readValue(entity.getMatchedKeywords(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
            }

            // Add job description preview for history list
            String jd = entity.getJobDescription();
            if (jd != null && jd.length() > 100) {
                builder.jobDescriptionPreview(jd.substring(0, 100) + "...");
            } else {
                builder.jobDescriptionPreview(jd);
            }

            return builder.build();
        } catch (Exception e) {
            log.error("Error converting tailored resume to DTO", e);
            throw new RuntimeException("Failed to convert tailored resume", e);
        }
    }
}
