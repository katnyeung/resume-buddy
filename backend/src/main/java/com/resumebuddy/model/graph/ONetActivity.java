package com.resumebuddy.model.graph;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

/**
 * Neo4j Node: O*NET Detailed Work Activity (DWA)
 *
 * Represents work activities from O*NET like:
 * - "Monitor computer system performance to ensure proper operation"
 * - "Analyze project data to determine specifications or requirements"
 * - "Develop testing routines or procedures"
 *
 * Graph Structure:
 *   (Occupation)-[REQUIRES_ACTIVITY {importance}]->(ONetActivity)
 *   (JobExperience)-[PERFORMS_ACTIVITY {confidence}]->(ONetActivity)
 */
@Node("ONetActivity")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ONetActivity {

    @Id
    private String id;  // e.g., "onet-activity-monitor-performance"

    @Property("name")
    private String name;  // Activity description

    @Property("onetId")
    private String onetId;  // O*NET activity ID if available

    @Property("category")
    private String category;  // e.g., "System Administration", "Software Development"

    @Property("importance")
    private Double importance;  // 0-100 scale from O*NET

    /**
     * Generate activity ID from name.
     * Example: "Monitor computer system performance" → "onet-activity-monitor-computer-system-performance"
     */
    public static String generateId(String name) {
        return "onet-activity-" + name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }
}
