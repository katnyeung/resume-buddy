package com.resumebuddy.model.graph;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

/**
 * Neo4j Node: Represents an O*NET Occupation.
 *
 * Examples:
 * - SOC Code: "15-1252.00"
 * - Title: "Software Developers"
 * - Description: "Research, design, and develop computer and network software..."
 *
 * Data Source: O*NET API or mock data
 */
@Node("Occupation")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Occupation {

    @Id
    private String code;  // SOC code is the unique identifier (e.g., "15-1252.00")

    @Property("title")
    private String title;

    @Property("description")
    private String description;

    @Property("category")
    private String category;  // e.g., "Computer and Mathematical Occupations"

    @Property("source")
    private String source;  // "ONET" or "MOCK" (for testing)
}
