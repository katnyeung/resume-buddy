package com.resumebuddy.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeAnalysisDto {
    private String id;
    private String resumeId;
    private String name;
    private String email;
    private String phone;
    private String linkedinUrl;
    private String githubUrl;
    private String websiteUrl;
    private String summary;
    private List<ExperienceDto> experiences;
    private List<SkillDto> skills;
    private List<EducationDto> educations;
    private List<CertificationDto> certifications;
    private List<ProjectDto> projects;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
