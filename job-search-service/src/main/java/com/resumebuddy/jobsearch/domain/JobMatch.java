package com.resumebuddy.jobsearch.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity: Job Match
 * Represents a matching result between a job search profile and a job listing
 */
@Entity
@Table(name = "job_match")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobMatch {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "profile_id", length = 36, nullable = false)
    private String profileId;

    @Column(name = "listing_id", length = 36, nullable = false)
    private String listingId;

    /**
     * Cosine similarity score (0.0 to 1.0)
     * Higher = better match
     */
    @Column(name = "similarity_score", precision = 5, scale = 4, nullable = false)
    private BigDecimal similarityScore;

    /**
     * Skill gap analysis stored as JSON
     * Format: {
     *   "matchedSkills": ["Java", "Spring Boot"],
     *   "missingSkills": ["AWS", "Kubernetes"],
     *   "matchPercentage": 75.5
     * }
     */
    @Column(name = "skill_gaps", columnDefinition = "JSON")
    private String skillGaps;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * User bookmarked this job match
     * Saved matches won't be deleted during force refresh
     */
    @Column(name = "is_saved", nullable = false)
    private Boolean isSaved = false;

    /**
     * User applied to this job
     */
    @Column(name = "is_applied", nullable = false)
    private Boolean isApplied = false;

    /**
     * User marked as not interested / expired
     */
    @Column(name = "is_redflag", nullable = false)
    private Boolean isRedflag = false;

    /**
     * Multi-purpose timestamp: when user took action (saved/applied/redflagged)
     * Used for retention policy enforcement (7/14/20 day retention)
     */
    @Column(name = "flagged_at")
    private LocalDateTime flaggedAt;

    // Convenience methods for domain logic
    public boolean isStrongMatch() {
        return similarityScore.compareTo(new BigDecimal("0.85")) >= 0;
    }

    public boolean isGoodMatch() {
        return similarityScore.compareTo(new BigDecimal("0.70")) >= 0;
    }
}
