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
 * Neo4j Relationship: Skill -> ONetTechnology (RELATED_TO)
 *
 * Represents that a job skill is related to an O*NET technology.
 *
 * Example:
 *   (Skill {name: "Spring Boot"})-[RELATED_TO {confidence: 0.90}]->(ONetTechnology {name: "Java"})
 *   (Skill {name: "PostgreSQL"})-[RELATED_TO {confidence: 0.95}]->(ONetTechnology {name: "Oracle Database"})
 *
 * This allows us to:
 * 1. Match similar/related technologies
 * 2. Identify technology gaps (candidate uses Spring Boot but O*NET lists Java)
 * 3. Technology skill mapping
 */
@RelationshipProperties
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RelatedToRelationship {

    @Id
    @GeneratedValue
    private Long id;

    @Property("confidence")
    private Double confidence;  // 0.0-1.0 (how related the technologies are)

    @Property("relationship")
    @Builder.Default
    private String relationship = "similar";  // "similar", "subset", "uses", "alternative"

    @Property("mappedBy")
    @Builder.Default
    private String mappedBy = "LLM";

    @Property("mappedAt")
    private LocalDateTime mappedAt;

    @TargetNode
    private ONetTechnology onetTechnology;
}
