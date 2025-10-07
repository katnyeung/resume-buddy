package com.resumebuddy.model.graph;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

/**
 * Neo4j Node: Represents a skill from job description.
 *
 * Skills are extracted from job descriptions and linked to jobs.
 * Skills are normalized (e.g., "React.js" → "React", "Java 8" → "Java")
 *
 * Examples:
 * - name: "Java", category: "Programming Language"
 * - name: "Spring Boot", category: "Framework"
 * - name: "AWS", category: "Cloud Platform"
 *
 * Graph Structure:
 *   (JobExperience)-[REQUIRES_SKILL]->(Skill)
 *   (Skill)-[DEMONSTRATES]->(ONetSkill)
 *   (Skill)-[RELATED_TO]->(ONetTechnology)
 */
@Node("Skill")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Skill {

    @Id
    private String id;  // Generated from normalized name (e.g., "skill-java")

    @Property("name")
    private String name;  // Normalized skill name

    @Property("category")
    private String category;  // "Programming Language", "Framework", "Cloud Platform", etc.

    @Property("subcategory")
    private String subcategory;  // Optional: "Backend", "Frontend", etc.

    @Property("isTechnical")
    private Boolean isTechnical;  // true for technical skills, false for soft skills

    /**
     * Relationships to O*NET soft/cognitive skills
     * Shows which O*NET competencies this skill demonstrates
     */
    @Relationship(type = "DEMONSTRATES", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    private Set<DemonstratesRelationship> demonstratesSkills = new HashSet<>();

    /**
     * Relationships to O*NET technologies
     * Shows which O*NET technologies this skill is related to
     */
    @Relationship(type = "RELATED_TO", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    private Set<RelatedToRelationship> relatedTechnologies = new HashSet<>();

    /**
     * Generate skill ID from normalized name.
     * Example: "Java" → "skill-java"
     */
    public static String generateId(String name) {
        return "skill-" + name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }
}
