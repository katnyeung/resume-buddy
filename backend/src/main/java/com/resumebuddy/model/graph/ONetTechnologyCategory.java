package com.resumebuddy.model.graph;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

/**
 * Neo4j Node: O*NET Technology Category
 *
 * Represents technology categories from O*NET like:
 * - "Web platform development software"
 * - "Cloud-based management software"
 * - "Data base management system software"
 * - "Development environment software"
 * - "Object or component oriented development software"
 *
 * Graph Structure:
 *   (ONetTechnology)-[:BELONGS_TO_CATEGORY]->(ONetTechnologyCategory)
 *
 * Benefits:
 * - Group related technologies together
 * - Analyze skill coverage by category
 * - Find candidates with diverse technology category knowledge
 * - Identify skill gaps at category level
 */
@Node("ONetTechnologyCategory")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ONetTechnologyCategory {

    @Id
    private String id;  // e.g., "onet-tech-cat-web-platform-development-software"

    @Property("name")
    private String name;  // e.g., "Web platform development software"

    /**
     * Generate category ID from name.
     * Example: "Web platform development software" → "onet-tech-cat-web-platform-development-software"
     */
    public static String generateId(String name) {
        return "onet-tech-cat-" + name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }
}
