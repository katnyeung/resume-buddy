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
 * Neo4j Relationship: Skill -> ONetTask (RELATES_TO_TASK)
 *
 * Represents that a job skill is required for performing a specific O*NET task.
 *
 * Example:
 *   (Skill {name: "Java"})-[RELATES_TO_TASK {confidence: 0.95}]->(ONetTask {name: "Write, analyze Java programs"})
 *   (Skill {name: "Testing"})-[RELATES_TO_TASK {confidence: 0.92}]->(ONetTask {name: "Develop software testing"})
 *
 * This allows us to:
 * 1. Understand which skills are needed for specific tasks
 * 2. Map candidate skills to actual work activities
 * 3. Identify skill gaps for specific task requirements
 * 4. Build task-based skill profiles
 */
@RelationshipProperties
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RelatesToTaskRelationship {

    @Id
    @GeneratedValue
    private Long id;

    @Property("confidence")
    private Double confidence;  // 0.0-1.0 (how strongly this skill relates to the task)

    @Property("reasoning")
    private String reasoning;  // LLM explanation of why this skill relates to this task

    @Property("mappedBy")
    @Builder.Default
    private String mappedBy = "LLM";  // "LLM" or "MANUAL"

    @Property("mappedAt")
    private LocalDateTime mappedAt;

    @TargetNode
    private ONetTask onetTask;
}
