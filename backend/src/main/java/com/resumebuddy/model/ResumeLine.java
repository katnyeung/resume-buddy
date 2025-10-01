package com.resumebuddy.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "resume_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeLine {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Resume resume;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    // AI Analysis fields (populated after analysis)
    @Column(name = "section_type", length = 50)
    private String sectionType;  // CONTACT, EXPERIENCE, EDUCATION, SKILLS, etc.

    @Column(name = "group_id")
    private Integer groupId;  // Groups related lines (e.g., same job entry)

    @Column(name = "group_type", length = 50)
    private String groupType;  // JOB, PROJECT, EDUCATION_ITEM, SKILL_CATEGORY, etc.

    @Column(name = "analysis_notes", columnDefinition = "TEXT")
    private String analysisNotes;  // AI findings and notes for this line

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;  // When this line was last analyzed

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}