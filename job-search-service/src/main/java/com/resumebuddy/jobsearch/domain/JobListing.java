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

    /**
     * Content hash for duplicate detection (SHA-256 of company + title + location)
     * Used to identify duplicate jobs even if URL changes
     */
    @Column(name = "content_hash", length = 64, unique = true)
    private String contentHash;

    @Column(name = "salary_range", length = 100)
    private String salaryRange;

    @Column(name = "contract_type", length = 50)
    private String contractType; // e.g., "permanent", "contract", "temporary", "full_time", "part_time"

    @Column(name = "posted_date")
    private LocalDateTime postedDate;

    @Column(name = "expires_date")
    private LocalDateTime expiresDate;

    /**
     * Complete raw API response data (preserves all source fields)
     */
    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData;

    @CreationTimestamp
    @Column(name = "fetched_at", nullable = false, updatable = false)
    private LocalDateTime fetchedAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt; // Track when we last saw this job (for updates)

    /**
     * Timestamp when skill extraction was completed
     * Used to track which jobs have been analyzed for market insights
     */
    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    /**
     * Cached extracted skills from LLM analysis
     * JSON array format: ["Java", "Spring Boot", "Docker"]
     * Used for quick skill lookups without re-parsing description
     */
    @Column(name = "extracted_skills", columnDefinition = "TEXT")
    private String extractedSkills;
}
