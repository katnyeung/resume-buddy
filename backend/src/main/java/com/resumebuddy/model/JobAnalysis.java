package com.resumebuddy.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_analysis")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobAnalysis {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "resume_id", nullable = false, length = 36)
    private String resumeId;

    @Column(name = "experience_id", nullable = false, length = 36)
    private String experienceId;

    // Store the entire analysis result as JSON
    @Column(name = "analysis_result", columnDefinition = "LONGTEXT")
    private String analysisResult;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
