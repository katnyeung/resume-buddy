package com.resumebuddy.model.graph;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

/**
 * Neo4j Node: O*NET Soft/Cognitive Skill
 *
 * Represents generic competencies from O*NET like:
 * - "Programming"
 * - "Critical Thinking"
 * - "Systems Analysis"
 * - "Complex Problem Solving"
 *
 * These are NOT specific technologies, but cognitive abilities.
 *
 * Graph Structure:
 *   (Occupation)-[REQUIRES_SKILL {importance}]->(ONetSkill)
 *   (Skill)-[DEMONSTRATES {confidence}]->(ONetSkill)
 */
@Node("ONetSkill")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ONetSkill {

    @Id
    private String id;  // e.g., "onet-skill-programming"

    @Property("name")
    private String name;  // e.g., "Programming"

    @Property("description")
    private String description;  // O*NET description

    @Property("onetId")
    private String onetId;  // O*NET element ID (e.g., "2.B.3.e")

    @Property("type")
    private String type;  // "cognitive", "technical", "social"

    @Property("importance")
    private Double importance;  // 0-100 scale from O*NET

    @Property("level")
    private Double level;  // 0-100 scale from O*NET

    /**
     * Generate skill ID from name.
     * Example: "Programming" → "onet-skill-programming"
     */
    public static String generateId(String name) {
        return "onet-skill-" + name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }
}
