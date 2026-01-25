package com.resumebuddy.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tailored_resumes", indexes = {
    @Index(name = "idx_tailored_user_id", columnList = "user_id"),
    @Index(name = "idx_tailored_resume_id", columnList = "resume_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TailoredResume {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "resume_id", length = 36, nullable = false)
    private String resumeId;

    @Column(name = "job_description", columnDefinition = "TEXT", nullable = false)
    private String jobDescription;

    @Column(name = "guidelines", length = 500)
    private String guidelines;

    // LLM Results
    @Column(name = "tailored_content", columnDefinition = "TEXT", nullable = false)
    private String tailoredContent;

    @Column(name = "cover_letter", columnDefinition = "TEXT")
    private String coverLetter;

    @Column(name = "contact_info", columnDefinition = "TEXT")
    private String contactInfo;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "experiences_json", columnDefinition = "TEXT")
    private String experiencesJson; // JSON array of experience sections

    @Column(name = "skills_json", columnDefinition = "TEXT")
    private String skillsJson; // JSON array of skills

    @Column(name = "educations_json", columnDefinition = "TEXT")
    private String educationsJson; // JSON array of education sections

    @Column(name = "projects_json", columnDefinition = "TEXT")
    private String projectsJson; // JSON array of project sections

    @Column(name = "certifications_json", columnDefinition = "TEXT")
    private String certificationsJson; // JSON array of certifications

    @Column(name = "highlighted_skills", columnDefinition = "TEXT")
    private String highlightedSkills; // JSON array

    @Column(name = "matched_keywords", columnDefinition = "TEXT")
    private String matchedKeywords; // JSON array

    @Column(name = "relevance_score")
    private Integer relevanceScore;

    @Column(name = "tailoring_notes", columnDefinition = "TEXT")
    private String tailoringNotes;

    @Column(name = "suggested_filename")
    private String suggestedFilename;

    // Metadata
    @Column(name = "credits_used", nullable = false)
    private Integer creditsUsed;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
