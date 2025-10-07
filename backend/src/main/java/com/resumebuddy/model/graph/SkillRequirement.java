package com.resumebuddy.model.graph;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;

/**
 * Neo4j Relationship: Job -> Skill (REQUIRES_SKILL)
 *
 * This relationship represents a skill required/used in a job.
 *
 * Properties:
 * - proficiencyLevel: 0-100 (estimated proficiency based on job description)
 * - yearsUsed: How many years this skill was used in this job
 * - isPrimary: Is this a core skill for this role?
 * - mentionedCount: How many times mentioned in description
 *
 * Example:
 *   (Job)-[REQUIRES_SKILL {proficiencyLevel: 85, isPrimary: true}]->(Skill {name: "Java"})
 */
@RelationshipProperties
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillRequirement {

    @Id
    @GeneratedValue
    private Long id;

    @Property("proficiencyLevel")
    private Integer proficiencyLevel;  // 0-100

    @Property("yearsUsed")
    private Double yearsUsed;  // Duration of job experience

    @Property("isPrimary")
    @Builder.Default
    private Boolean isPrimary = false;

    @Property("mentionedCount")
    private Integer mentionedCount;  // How many times mentioned

    @Property("context")
    private String context;  // Where/how it was mentioned

    @TargetNode
    private Skill skill;
}
