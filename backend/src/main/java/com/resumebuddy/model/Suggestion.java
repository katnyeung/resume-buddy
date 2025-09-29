package com.resumebuddy.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "suggestions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Suggestion {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
        name = "UUID",
        strategy = "org.hibernate.id.UUIDGenerator"
    )
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    @JsonBackReference
    private Resume resume;

    // Removed block reference as we're not using ResumeBlock anymore
    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "suggestion_type", length = 50)
    private String suggestionType; // IMPROVEMENT, GRAMMAR, ATS, ENHANCEMENT

    @Column(name = "original_text", columnDefinition = "LONGTEXT")
    private String originalText;

    @Column(name = "suggested_text", columnDefinition = "LONGTEXT")
    private String suggestedText;

    @Column(name = "reasoning", columnDefinition = "LONGTEXT")
    private String reasoning;

    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence;

    @Column(name = "applied")
    private Boolean applied = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}