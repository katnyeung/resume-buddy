package com.resumebuddy.service;

import com.resumebuddy.model.ResumeAnalysisExperience;
import com.resumebuddy.model.dto.NormalizedJobDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Values;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Neo4j Graph Database Service
 * Manages the knowledge graph for job analysis, occupations, and work activities
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class Neo4jGraphService {

    private final Driver neo4jDriver;

    /**
     * Store job experience and occupation mapping in Neo4j graph
     */
    public void storeJobAnalysisInGraph(
            String resumeId,
            String experienceId,
            ResumeAnalysisExperience experience,
            NormalizedJobDto normalized) {

        log.info("Storing job analysis in Neo4j graph for experience: {}", experienceId);

        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                // Create or merge Occupation node
                createOccupationNode(tx, normalized);

                // Create JobExperience node
                createJobExperienceNode(tx, resumeId, experienceId, experience, normalized);

                // Create relationship between JobExperience and Occupation
                createJobToOccupationRelationship(tx, experienceId, normalized.getSocCodes().get(0).getCode());

                return null;
            });

            log.info("Successfully stored job analysis in Neo4j graph");

        } catch (Exception e) {
            log.error("Error storing job analysis in Neo4j graph", e);
            // Don't throw - allow the analysis to continue even if graph storage fails
        }
    }

    /**
     * Create or update Occupation node
     */
    private void createOccupationNode(TransactionContext tx, NormalizedJobDto normalized) {
        if (normalized.getSocCodes() == null || normalized.getSocCodes().isEmpty()) {
            return;
        }

        NormalizedJobDto.SocCodeMapping primarySoc = normalized.getSocCodes().get(0);

        String query = """
            MERGE (o:Occupation {soc_code: $socCode})
            SET o.title = $title,
                o.normalized_title = $normalizedTitle,
                o.confidence = $confidence,
                o.seniority_level = $seniority,
                o.technical_depth = $technicalDepth,
                o.has_leadership = $hasLeadership,
                o.last_updated = datetime()
            RETURN o
            """;

        tx.run(query, Values.parameters(
                "socCode", primarySoc.getCode(),
                "title", primarySoc.getTitle(),
                "normalizedTitle", normalized.getNormalizedTitle(),
                "confidence", primarySoc.getConfidence(),
                "seniority", normalized.getSeniority(),
                "technicalDepth", normalized.getTechnicalDepth(),
                "hasLeadership", normalized.getHasLeadership()
        ));

        log.debug("Created/updated Occupation node: {} ({})", primarySoc.getTitle(), primarySoc.getCode());
    }

    /**
     * Create JobExperience node
     */
    private void createJobExperienceNode(
            TransactionContext tx,
            String resumeId,
            String experienceId,
            ResumeAnalysisExperience experience,
            NormalizedJobDto normalized) {

        String query = """
            CREATE (je:JobExperience {
                id: $id,
                resume_id: $resumeId,
                original_title: $originalTitle,
                normalized_title: $normalizedTitle,
                company: $company,
                start_date: $startDate,
                end_date: $endDate,
                seniority: $seniority,
                description: $description,
                analyzed_at: datetime()
            })
            RETURN je
            """;

        tx.run(query, Values.parameters(
                "id", experienceId,
                "resumeId", resumeId,
                "originalTitle", experience.getJobTitle(),
                "normalizedTitle", normalized.getNormalizedTitle(),
                "company", experience.getCompanyName(),
                "startDate", experience.getStartDate(),
                "endDate", experience.getEndDate(),
                "seniority", normalized.getSeniority(),
                "description", experience.getDescription()
        ));

        log.debug("Created JobExperience node: {} at {}", experience.getJobTitle(), experience.getCompanyName());
    }

    /**
     * Create relationship: (JobExperience)-[:MAPS_TO]->(Occupation)
     */
    private void createJobToOccupationRelationship(TransactionContext tx, String experienceId, String socCode) {
        String query = """
            MATCH (je:JobExperience {id: $experienceId})
            MATCH (o:Occupation {soc_code: $socCode})
            MERGE (je)-[r:MAPS_TO]->(o)
            SET r.created_at = datetime()
            RETURN r
            """;

        tx.run(query, Values.parameters(
                "experienceId", experienceId,
                "socCode", socCode
        ));

        log.debug("Created MAPS_TO relationship from experience {} to occupation {}", experienceId, socCode);
    }

    /**
     * Store work activities from O*NET data
     */
    public void storeWorkActivities(String socCode, String onetData) {
        log.info("Storing work activities for SOC code: {}", socCode);

        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                // Parse O*NET data and create Activity nodes
                // This is a simplified version - you can expand based on O*NET structure
                createActivityNodesFromONetData(tx, socCode, onetData);
                return null;
            });

            log.info("Successfully stored work activities in Neo4j");

        } catch (Exception e) {
            log.error("Error storing work activities in Neo4j", e);
        }
    }

    /**
     * Create Activity nodes from O*NET data
     */
    private void createActivityNodesFromONetData(TransactionContext tx, String socCode, String onetData) {
        // Parse O*NET JSON and extract activities
        // For MVP, we'll create a simple summary node
        String query = """
            MATCH (o:Occupation {soc_code: $socCode})
            MERGE (o)-[:HAS_ONET_DATA]->(d:ONetData {soc_code: $socCode})
            SET d.raw_data = $onetData,
                d.last_updated = datetime()
            RETURN d
            """;

        tx.run(query, Values.parameters(
                "socCode", socCode,
                "onetData", onetData
        ));

        log.debug("Stored O*NET data for occupation: {}", socCode);
    }

    /**
     * Query similar occupations based on SOC code
     */
    public Map<String, Object> findSimilarOccupations(String socCode, int limit) {
        log.info("Finding similar occupations for SOC code: {}", socCode);

        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                String query = """
                    MATCH (o1:Occupation {soc_code: $socCode})
                    MATCH (o2:Occupation)
                    WHERE o1 <> o2
                      AND o1.soc_code STARTS WITH substring(o2.soc_code, 0, 5)
                    RETURN o2.soc_code AS socCode,
                           o2.title AS title,
                           o2.normalized_title AS normalizedTitle,
                           o2.seniority_level AS seniority
                    LIMIT $limit
                    """;

                var result = tx.run(query, Values.parameters(
                        "socCode", socCode,
                        "limit", limit
                ));

                Map<String, Object> similarOccupations = new HashMap<>();
                int count = 0;

                while (result.hasNext()) {
                    var record = result.next();
                    Map<String, Object> occupation = new HashMap<>();
                    occupation.put("socCode", record.get("socCode").asString());
                    occupation.put("title", record.get("title").asString());
                    occupation.put("normalizedTitle", record.get("normalizedTitle").asString(""));
                    occupation.put("seniority", record.get("seniority").asString(""));

                    similarOccupations.put("occupation_" + count++, occupation);
                }

                similarOccupations.put("total", count);
                return similarOccupations;
            });

        } catch (Exception e) {
            log.error("Error finding similar occupations", e);
            return Map.of("total", 0);
        }
    }

    /**
     * Get career progression insights
     */
    public Map<String, Object> getCareerProgression(String resumeId) {
        log.info("Getting career progression for resume: {}", resumeId);

        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                String query = """
                    MATCH (je:JobExperience {resume_id: $resumeId})-[:MAPS_TO]->(o:Occupation)
                    RETURN je.original_title AS title,
                           je.company AS company,
                           je.start_date AS startDate,
                           je.end_date AS endDate,
                           je.seniority AS seniority,
                           o.soc_code AS socCode,
                           o.title AS occupationTitle
                    ORDER BY je.start_date DESC
                    """;

                var result = tx.run(query, Values.parameters("resumeId", resumeId));

                Map<String, Object> progression = new HashMap<>();
                int jobCount = 0;

                while (result.hasNext()) {
                    var record = result.next();
                    Map<String, Object> job = new HashMap<>();
                    job.put("title", record.get("title").asString());
                    job.put("company", record.get("company").asString());
                    job.put("startDate", record.get("startDate").asString(""));
                    job.put("endDate", record.get("endDate").asString(""));
                    job.put("seniority", record.get("seniority").asString(""));
                    job.put("socCode", record.get("socCode").asString());
                    job.put("occupationTitle", record.get("occupationTitle").asString());

                    progression.put("job_" + jobCount++, job);
                }

                progression.put("totalJobs", jobCount);
                return progression;
            });

        } catch (Exception e) {
            log.error("Error getting career progression", e);
            return Map.of("totalJobs", 0);
        }
    }

    /**
     * Test Neo4j connection
     */
    public boolean testConnection() {
        try (Session session = neo4jDriver.session()) {
            var result = session.run("RETURN 'Connection successful' AS message");
            String message = result.single().get("message").asString();
            log.info("Neo4j connection test: {}", message);
            return true;
        } catch (Exception e) {
            log.error("Neo4j connection test failed", e);
            return false;
        }
    }

    /**
     * Get graph statistics
     */
    public Map<String, Object> getGraphStats() {
        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                Map<String, Object> stats = new HashMap<>();

                // Count occupations
                var occupationCount = tx.run("MATCH (o:Occupation) RETURN count(o) AS count")
                        .single().get("count").asLong();
                stats.put("occupations", occupationCount);

                // Count job experiences
                var jobCount = tx.run("MATCH (je:JobExperience) RETURN count(je) AS count")
                        .single().get("count").asLong();
                stats.put("jobExperiences", jobCount);

                // Count relationships
                var relCount = tx.run("MATCH ()-[r:MAPS_TO]->() RETURN count(r) AS count")
                        .single().get("count").asLong();
                stats.put("mappings", relCount);

                return stats;
            });
        } catch (Exception e) {
            log.error("Error getting graph stats", e);
            return Map.of("error", e.getMessage());
        }
    }
}
