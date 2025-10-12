package com.resumebuddy.model.graph;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

/**
 * Neo4j Node: Description Line
 *
 * Represents individual achievement or responsibility line from a job experience description.
 * Each line is analyzed and mapped to O*NET activities and tasks for granular activity tracking.
 *
 * Example:
 * - "Developed cloud-native microservices achieving 99.95% uptime"
 * - "Led team of 5 engineers in migrating legacy systems to GCP"
 * - "Provided critical business support through troubleshooting and optimization"
 *
 * Graph Structure:
 *   (JobExperience)-[HAS_DESCRIPTION_LINE {sequence}]->(DescriptionLine)
 *   (DescriptionLine)-[DEMONSTRATES_ACTIVITY {confidence}]->(ONetActivity)
 *   (DescriptionLine)-[DEMONSTRATES_TASK {confidence}]->(ONetTask)
 */
@Node("DescriptionLine")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DescriptionLine {

    @Id
    private String id;  // e.g., "desc-line-exp001-1"

    @Property("experience_id")
    private String experienceId;  // Reference to JobExperience

    @Property("sequence")
    private Integer sequence;  // Line number in description (1, 2, 3, ...)

    @Property("text")
    private String text;  // The actual description line/bullet point

    @Property("impact_metrics")
    private String impactMetrics;  // Extracted metrics (e.g., "99.95% uptime, 10K requests/sec")

    @Property("has_quantifiable_impact")
    private Boolean hasQuantifiableImpact;  // Whether line contains measurable achievements

    @Property("impact_level")
    private String impactLevel;  // Low/Medium/High/Critical

    @Property("scope")
    private String scope;  // Individual/Team/Department/Company/Industry

    @Property("parsed_at")
    private String parsedAt;  // When this line was parsed and analyzed

    /**
     * Generate description line ID
     * Example: "desc-line-exp123-1" for first line of experience exp123
     */
    public static String generateId(String experienceId, int sequence) {
        return "desc-line-" + experienceId + "-" + sequence;
    }
}
