package com.resumebuddy.jobsearch.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * Represents a version of the mock job requirements for a job search profile.
 * Supports up to 3 versions per profile for rollback capability.
 */
@Entity
@Table(name = "job_search_profile_version")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobSearchProfileVersion {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "profile_id", length = 36, nullable = false)
    private String profileId;

    /**
     * Version number (1, 2, or 3)
     * Max 3 versions per profile
     */
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    /**
     * User-friendly version name
     * e.g., "Original", "From 5 Applied Jobs", custom name
     */
    @Column(name = "version_name", length = 100)
    private String versionName;

    /**
     * The mock job requirements text for this version
     */
    @Column(name = "generated_job_post", columnDefinition = "TEXT", nullable = false)
    private String generatedJobPost;

    /**
     * Source type: 'RESUME' (from resume experiences) or 'APPLIED_JOBS' (from applied jobs)
     */
    @Column(name = "source_type", length = 20, nullable = false)
    private String sourceType;

    /**
     * JSON array of job listing IDs when source_type = 'APPLIED_JOBS'
     * null when source_type = 'RESUME'
     */
    @Column(name = "source_job_ids", columnDefinition = "TEXT")
    private String sourceJobIds;

    /**
     * Whether this version is currently active (loaded in editor and vectorized)
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
