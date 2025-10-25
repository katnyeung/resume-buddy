package com.resumebuddy.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_queue")
@Data
public class JobQueueEntry {

    @Id
    @Column(name = "id", length = 255)
    private String id;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStatus status = JobStatus.QUEUED;

    @Column(name = "priority", nullable = false)
    private Integer priority = 5;

    @Column(name = "input_params", nullable = false, columnDefinition = "TEXT")
    private String inputParams;

    @Column(name = "result_data", columnDefinition = "LONGTEXT")
    private String resultData;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 3;

    @Column(name = "estimated_credits", nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedCredits;

    @Column(name = "actual_credits_used", precision = 10, scale = 2)
    private BigDecimal actualCreditsUsed;

    @Column(name = "queued_at", nullable = false, updatable = false)
    private LocalDateTime queuedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @PrePersist
    protected void onCreate() {
        queuedAt = LocalDateTime.now();
    }

    public enum JobType {
        RESUME_ANALYSIS,
        JOB_EXPERIENCE_ANALYSIS,
        JOB_PROFILE_GENERATION
    }

    public enum JobStatus {
        QUEUED,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public boolean canRetry() {
        return retryCount < maxRetries;
    }

    public void incrementRetry() {
        retryCount++;
    }
}
