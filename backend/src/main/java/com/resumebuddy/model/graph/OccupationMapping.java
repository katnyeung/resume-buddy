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
 * Neo4j Relationship: Job -> Occupation (MAPS_TO)
 *
 * This relationship represents how well a job matches an O*NET occupation.
 *
 * Properties:
 * - confidence: 0.0-1.0 (how confident we are in this mapping)
 * - isPrimary: true if this is the main occupation for this job
 * - reasoning: Why the LLM chose this occupation
 * - mappedAt: When the mapping was created
 *
 * Example:
 *   (Job {title: "Java Developer"})-[MAPS_TO {confidence: 0.92, isPrimary: true}]->(Occupation {code: "15-1252.00"})
 */
@RelationshipProperties
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OccupationMapping {

    @Id
    @GeneratedValue
    private Long id;

    @Property("confidence")
    private Double confidence;  // 0.0-1.0

    @Property("isPrimary")
    @Builder.Default
    private Boolean isPrimary = true;  // In step 1, always true (single occupation)

    @Property("reasoning")
    private String reasoning;  // LLM explanation

    @Property("mappedAt")
    private LocalDateTime mappedAt;

    @TargetNode
    private Occupation occupation;
}
