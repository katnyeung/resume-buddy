package com.resumebuddy.jobsearch.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * Entity: Crawl Activity Log
 * Tracks all job crawling operations for audit and user visibility
 */
@Entity
@Table(name = "crawl_activity_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrawlActivityLog {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "source", length = 50, nullable = false)
    private String source; // ADZUNA, REED, JSEARCH

    @Column(name = "keywords", length = 255)
    private String keywords;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "total_fetched", nullable = false)
    private Integer totalFetched = 0;

    @Column(name = "total_saved", nullable = false)
    private Integer totalSaved = 0;

    @Column(name = "duplicates_updated", nullable = false)
    private Integer duplicatesUpdated = 0;

    @Column(name = "duplicates_skipped", nullable = false)
    private Integer duplicatesSkipped = 0;

    @Column(name = "failed_to_process", nullable = false)
    private Integer failedToProcess = 0;

    @Column(name = "status", length = 20, nullable = false)
    private String status; // SUCCESS, FAILED

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @CreationTimestamp
    @Column(name = "crawled_at", nullable = false, updatable = false)
    private LocalDateTime crawledAt;
}
