package com.resumebuddy.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_analysis")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobAnalysis {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Resume resume;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "experience_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ResumeAnalysisExperience experience;

    // Normalization Results
    @Column(name = "normalized_title", length = 255)
    private String normalizedTitle;

    @Column(name = "primary_soc_code", length = 20)
    private String primarySocCode;

    @Column(name = "seniority_level", length = 50)
    private String seniorityLevel;

    // Scores (0-10 scale)
    @Column(name = "impact_score", precision = 3, scale = 2)
    private BigDecimal impactScore;

    @Column(name = "technical_depth_score", precision = 3, scale = 2)
    private BigDecimal technicalDepthScore;

    @Column(name = "leadership_score", precision = 3, scale = 2)
    private BigDecimal leadershipScore;

    @Column(name = "overall_score", precision = 3, scale = 2)
    private BigDecimal overallScore;

    // Analysis Results
    @Column(name = "recruiter_summary", columnDefinition = "TEXT")
    private String recruiterSummary;

    // JSON columns for complex data (stored as TEXT for MariaDB compatibility)
    @Column(name = "work_activities", columnDefinition = "TEXT")
    private String workActivities;  // JSON array of activities

    @Column(name = "key_strengths", columnDefinition = "TEXT")
    private String keyStrengths;  // JSON array of strengths

    @Column(name = "improvement_areas", columnDefinition = "TEXT")
    private String improvementAreas;  // JSON array of improvements

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
