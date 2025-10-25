package com.resumebuddy.model.dto;

import com.resumebuddy.model.JobQueueEntry.JobType;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class EnqueueJobResponseDto {
    private String jobId;
    private JobType jobType;
    private String status;
    private BigDecimal estimatedCredits;
    private Integer queuePosition;
    private String message;
}
