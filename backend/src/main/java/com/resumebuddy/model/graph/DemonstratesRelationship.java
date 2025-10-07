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

import java.time.LocalDateTime;

/**
 * Neo4j Relationship: Skill -> ONetSkill (DEMONSTRATES)
 *
 * Represents that a technical skill demonstrates a soft/cognitive skill.
 *
 * Example:
 *   (Skill {name: "Java"})-[DEMONSTRATES {confidence: 0.95}]->(ONetSkill {name: "Programming"})
 *   (Skill {name: "Kubernetes"})-[DEMONSTRATES {confidence: 0.80}]->(ONetSkill {name: "Systems Analysis"})
 *
 * This allows us to:
 * 1. Build a soft skill profile for candidates
 * 2. Identify which technical skills demonstrate which competencies
 * 3. Compare candidate's soft skills to occupation requirements
 */
@RelationshipProperties
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemonstratesRelationship {

    @Id
    @GeneratedValue
    private Long id;

    @Property("confidence")
    private Double confidence;  // 0.0-1.0 (how strongly this skill demonstrates the competency)

    @Property("mappedBy")
    @Builder.Default
    private String mappedBy = "LLM";  // "LLM" or "MANUAL"

    @Property("mappedAt")
    private LocalDateTime mappedAt;

    @TargetNode
    private ONetSkill onetSkill;
}
