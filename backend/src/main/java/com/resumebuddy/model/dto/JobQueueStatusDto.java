package com.resumebuddy.model.dto;

import com.resumebuddy.model.JobQueueEntry.JobStatus;
import com.resumebuddy.model.JobQueueEntry.JobType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class JobQueueStatusDto {
    private String jobId;
    private JobType jobType;
    private JobStatus status;
    private Integer queuePosition;
    private BigDecimal estimatedCredits;
    private BigDecimal actualCreditsUsed;
    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private Integer retryCount;
    private Integer maxRetries;
    private Object result;  // Only populated when status = COMPLETED
}
