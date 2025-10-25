package com.resumebuddy.jobsearch.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * Domain Entity: Job Search Profile Skill
 * Represents individual skills associated with a job search profile
 */
@Entity
@Table(name = "job_search_profile_skill")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobSearchProfileSkill {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "profile_id", length = 36, nullable = false)
    private String profileId;

    @Column(name = "skill_name", length = 255, nullable = false)
    private String skillName;

    @Column(name = "skill_category", length = 100)
    private String skillCategory; // e.g., "Technical", "Soft Skill", "Language"

    @Column(name = "proficiency_score")
    private Integer proficiencyScore; // 0-100: skill proficiency level (affects matching score)

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
