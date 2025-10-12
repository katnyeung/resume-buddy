package com.resumebuddy.model.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO for skill extraction from LLM.
 *
 * Represents a skill extracted from a job description.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillExtractionDto {

    private String name;              // Normalized skill name (e.g., "Java", "Spring Boot")
    private String category;          // "Programming Language", "Framework", etc.
    private String subcategory;       // "Backend", "Frontend", etc.
    private Integer proficiencyLevel; // 0-100 estimated proficiency
    private Boolean isTechnical;      // true for technical skills
    private Boolean isPrimary;        // true if this is a core skill for the role
    private Integer mentionedCount;   // How many times mentioned
    private String context;           // Where/how it was mentioned
}
