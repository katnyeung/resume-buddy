package com.resumebuddy.jobsearch.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * Entity: Job Listing
 * Represents an external job posting from job boards
 */
@Entity
@Table(name = "job_listing")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobListing {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "source", length = 100)
    private String source; // e.g., "LinkedIn", "Indeed", "RemoteOK"

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "company", length = 255)
    private String company;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "salary_range", length = 100)
    private String salaryRange;

    /**
     * Redis key for the vector embedding of this job listing
     * Format: "listing:vector:{id}"
     */
    @Column(name = "redis_vector_key", length = 100, nullable = false)
    private String redisVectorKey;

    /**
     * Required skills extracted from job description (JSON array)
     */
    @Column(name = "required_skills", columnDefinition = "JSON")
    private String requiredSkills;

    @CreationTimestamp
    @Column(name = "fetched_at", nullable = false, updatable = false)
    private LocalDateTime fetchedAt;
}
