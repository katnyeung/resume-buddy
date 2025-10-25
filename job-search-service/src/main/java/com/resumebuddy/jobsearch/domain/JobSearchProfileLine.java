package com.resumebuddy.jobsearch.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Entity: Job Search Profile Line
 * Stores individual lines from the LLM-generated mock job post with their vector embeddings
 * Master-detail relationship: job_search_profile (1) -> job_search_profile_line (many)
 */
@Entity
@Table(name = "job_search_profile_line")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobSearchProfileLine {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "profile_id", length = 36, nullable = false)
    private String profileId;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    /**
     * One bullet point/requirement from the Grok-generated mock job post
     * Example: "5+ years of backend development in financial services"
     */
    @Column(name = "line_content", columnDefinition = "TEXT", nullable = false)
    private String lineContent;

    /**
     * Redis key for the vector embedding of this specific line
     * Format: "profile:line:{id}"
     */
    @Column(name = "redis_vector_key", length = 100)
    private String redisVectorKey;
}
