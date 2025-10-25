package com.resumebuddy.jobsearch.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Entity: Job Listing Line
 * Stores individual lines parsed from job descriptions with their vector embeddings
 * Master-detail relationship: job_listing (1) -> job_listing_line (many)
 */
@Entity
@Table(name = "job_listing_line")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobListingLine {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "listing_id", length = 36, nullable = false)
    private String listingId;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    /**
     * One parsed line from the job description
     * Example: "Extensive experience working with JAVA"
     */
    @Column(name = "line_content", columnDefinition = "TEXT", nullable = false)
    private String lineContent;

    /**
     * Redis key for the vector embedding of this specific line
     * Format: "listing:line:{id}"
     */
    @Column(name = "redis_vector_key", length = 100)
    private String redisVectorKey;
}
