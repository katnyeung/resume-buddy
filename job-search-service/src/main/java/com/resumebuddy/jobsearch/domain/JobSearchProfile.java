package com.resumebuddy.jobsearch.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Aggregate Root: Job Search Profile
 * Represents a user's generated job search profile based on selected resume experiences
 */
@Entity
@Table(name = "job_search_profile")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobSearchProfile {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId; // Foreign key to users table in resume-buddy-api

    @Column(name = "resume_id", length = 36, nullable = false)
    private String resumeId;

    /**
     * List of experience IDs from resume-api that were used to generate this profile
     * Stored as JSON array
     */
    @Column(name = "source_experience_ids", columnDefinition = "JSON", nullable = false)
    private String sourceExperienceIds;

    /**
     * LLM-generated job post text that represents the candidate's profile
     */
    @Column(name = "generated_job_post", columnDefinition = "TEXT", nullable = false)
    private String generatedJobPost;

    /**
     * Desired job title (e.g., "Java Developer", "Senior Software Engineer", "Data Scientist")
     * User-specified title used for job search and LLM context
     */
    @Column(name = "desired_job_title", length = 255)
    private String desiredJobTitle;

    /**
     * Target location for job search (e.g., "San Francisco, CA", "Remote", "New York")
     * User can specify where they want to search for jobs
     */
    @Column(name = "location", length = 255)
    private String location;

    /**
     * Target experience level (e.g., "Entry Level", "Mid Level", "Senior", "Lead/Principal")
     * User can specify the seniority level they're targeting
     */
    @Column(name = "experience_level", length = 50)
    private String experienceLevel;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Last visit date - tracks when user visited job-search page or clicked "Refresh Results"
     * Used to determine active profiles for LLM keyword generation (filter by last 7 days)
     */
    @Column(name = "last_visit_date")
    private LocalDateTime lastVisitDate;

    /**
     * Comma-separated deal-breaker keywords (e.g., "SC Clearance,PhD required,blockchain")
     * Jobs containing these keywords will be highlighted in red in the UI
     * Does NOT filter out jobs - only highlights for user awareness
     */
    @Column(name = "excluded_keywords", columnDefinition = "TEXT")
    private String excludedKeywords;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
