package com.resumebuddy.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TailoredResumeResponseDto {

    private String id; // Saved tailored resume ID (for history)
    private String resumeId;

    // The tailored resume in markdown format
    private String tailoredContent;

    // Cover letter
    private String coverLetter;

    // Structured sections for docx generation
    private String contactInfo;
    private String summary;
    private List<ExperienceSection> experiences;
    private List<ProjectSection> projects;
    private List<String> skills;
    private List<EducationSection> educations;
    private List<String> certifications;

    // Analysis results
    private List<String> highlightedSkills;
    private List<String> matchedKeywords;
    private int relevanceScore; // 0-100
    private String tailoringNotes;
    private String suggestedFilename; // e.g., "John_Doe_Senior_Engineer_Acme"

    // Cost tracking
    private int creditsUsed;

    // Metadata for history display
    private LocalDateTime createdAt;
    private String jobDescriptionPreview; // First 100 chars for history list

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExperienceSection {
        private String jobTitle;
        private String company;
        private String dates;
        private List<String> bulletPoints;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EducationSection {
        private String degree;
        private String institution;
        private String graduationDate;
        private String gpa;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProjectSection {
        private String projectName;
        private String description;
        private List<String> technologies;
        private String url;
    }
}
