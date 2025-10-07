package com.resumebuddy.model.graph;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

/**
 * Neo4j Node: O*NET Task
 *
 * Represents specific tasks from O*NET occupations, like:
 * - "Write, analyze, review, and rewrite programs, using workflow chart and diagram"
 * - "Correct errors by making appropriate changes and rechecking the program"
 * - "Perform or direct revision, repair, or expansion of existing programs"
 *
 * These are much more specific than "Detailed Work Activities" and better represent
 * what programmers actually do day-to-day.
 *
 * Graph Structure:
 *   (Occupation)-[REQUIRES_TASK {importance, category}]->(ONetTask)
 *   (JobExperience)-[PERFORMS_TASK {confidence}]->(ONetTask)
 */
@Node("ONetTask")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ONetTask {

    @Id
    private String id;  // e.g., "onet-task-write-analyze-review-programs"

    @Property("name")
    private String name;  // Task description/statement

    @Property("onetId")
    private String onetId;  // O*NET task ID if available

    @Property("category")
    private String category;  // "Core" or "Supplemental" from O*NET

    @Property("importance")
    private Double importance;  // 0-100 scale from O*NET

    /**
     * Generate task ID from name.
     * Example: "Write, analyze, review programs" → "onet-task-write-analyze-review-programs"
     */
    public static String generateId(String name) {
        // Take first 100 chars to avoid extremely long IDs
        String truncated = name.length() > 100 ? name.substring(0, 100) : name;
        return "onet-task-" + truncated.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("-+$", ""); // Remove trailing dashes
    }
}
