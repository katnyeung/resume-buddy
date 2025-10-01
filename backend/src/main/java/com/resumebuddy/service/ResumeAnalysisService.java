package com.resumebuddy.service;

import com.resumebuddy.model.*;
import com.resumebuddy.model.dto.*;
import com.resumebuddy.repository.ResumeAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeAnalysisService {

    private final ResumeAnalysisRepository resumeAnalysisRepository;

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
