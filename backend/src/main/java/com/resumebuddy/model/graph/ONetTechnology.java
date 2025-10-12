package com.resumebuddy.model.graph;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

/**
 * Neo4j Node: O*NET Technology/Tool
 *
 * Represents specific tools and technologies from O*NET like:
 * - "Java"
 * - "Oracle Database"
 * - "Jenkins"
 * - "Docker"
 *
 * These are concrete technologies, not cognitive abilities.
 *
 * Graph Structure:
 *   (Occupation)-[USES_TECHNOLOGY]->(ONetTechnology)
 *   (ONetTechnology)-[BELONGS_TO_CATEGORY]->(ONetTechnologyCategory)
 *   (Skill)-[RELATED_TO {confidence}]->(ONetTechnology)
 */
@Node("ONetTechnology")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ONetTechnology {

    @Id
    private String id;  // e.g., "onet-tech-java"

    @Property("name")
    private String name;  // e.g., "Java"

    @Property("example")
    private String example;  // Optional: specific example from O*NET

    /**
     * Generate technology ID from name.
     * Example: "Java" → "onet-tech-java"
     */
    public static String generateId(String name) {
        return "onet-tech-" + name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }
}
