package com.resumebuddy.service;

import com.resumebuddy.model.*;
import com.resumebuddy.model.dto.*;
import com.resumebuddy.repository.ResumeAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        // Convert experiences
        if (analysis.getExperiences() != null) {
            dto.setExperiences(analysis.getExperiences().stream()
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
}
