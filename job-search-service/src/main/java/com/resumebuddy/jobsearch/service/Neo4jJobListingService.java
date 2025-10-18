package com.resumebuddy.jobsearch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Neo4j Job Listing Service for Job Search Service
 * Indexes external job listings into Neo4j graph
 * Part of Market Insights feature
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class Neo4jJobListingService {

    private final Driver neo4jDriver;

    @Value("${spring.neo4j.database:neo4j}")
    private String database;

    /**
     * Index job listing with extracted skills in Neo4j
     * Creates JobListing node and links to Skill nodes via REQUIRES_SKILL relationship
     *
     * @param jobId External job listing ID (from job_listing table)
     * @param jobTitle Job title
     * @param skills List of skill names extracted by LLM
     */
    public void indexJobWithSkills(String jobId, String jobTitle, List<String> skills) {
        log.info("Indexing job listing in Neo4j: {} - {} with {} skills", jobId, jobTitle, skills.size());

        SessionConfig sessionConfig = SessionConfig.builder()
                .withDatabase(database)
                .build();

        try (Session session = neo4jDriver.session(sessionConfig)) {
            session.executeWrite(tx -> {
                // Cypher query to create/update JobListing and link to skills
                String cypher = """
                    // Create or update JobListing node
                    MERGE (job:JobListing {id: $jobId})
                    SET job.title = $jobTitle,
                        job.lastUpdated = datetime()

                    // Link to skill nodes (create skills if they don't exist)
                    WITH job
                    UNWIND $skills AS skillName
                    MERGE (skill:Skill {name: skillName})
                    MERGE (job)-[r:REQUIRES_SKILL]->(skill)
                    ON CREATE SET r.mentionCount = 1,
                                  r.firstSeenAt = datetime()
                    ON MATCH SET r.mentionCount = r.mentionCount + 1,
                                 r.lastSeenAt = datetime()

                    RETURN job.id as jobId, COUNT(skill) as skillCount
                    """;

                Map<String, Object> params = Map.of(
                        "jobId", jobId,
                        "jobTitle", jobTitle,
                        "skills", skills
                );

                var result = tx.run(cypher, params);

                if (result.hasNext()) {
                    var record = result.next();
                    log.debug("Indexed job {} with {} skills in Neo4j",
                            record.get("jobId").asString(),
                            record.get("skillCount").asInt());
                }

                return null;
            });

            log.info("Successfully indexed job listing {} in Neo4j", jobId);

        } catch (Exception e) {
            log.error("Failed to index job listing {} in Neo4j", jobId, e);
            throw new RuntimeException("Failed to index job listing in Neo4j", e);
        }
    }

    /**
     * Get job listings that require a specific skill
     *
     * @param skillName Name of the skill
     * @return Count of job listings requiring this skill
     */
    public long getJobCountForSkill(String skillName) {
        SessionConfig sessionConfig = SessionConfig.builder()
                .withDatabase(database)
                .build();

        try (Session session = neo4jDriver.session(sessionConfig)) {
            return session.executeRead(tx -> {
                String cypher = """
                    MATCH (job:JobListing)-[:REQUIRES_SKILL]->(skill:Skill {name: $skillName})
                    RETURN COUNT(job) as count
                    """;

                var result = tx.run(cypher, Map.of("skillName", skillName));

                if (result.hasNext()) {
                    return result.next().get("count").asLong();
                }

                return 0L;
            });
        } catch (Exception e) {
            log.error("Failed to get job count for skill: {}", skillName, e);
            return 0L;
        }
    }

    /**
     * Get all skill names from Neo4j
     *
     * @return List of all skill names
     */
    public List<String> getAllSkills() {
        SessionConfig sessionConfig = SessionConfig.builder()
                .withDatabase(database)
                .build();

        try (Session session = neo4jDriver.session(sessionConfig)) {
            return session.executeRead(tx -> {
                String cypher = """
                    MATCH (skill:Skill)
                    RETURN skill.name as skillName
                    ORDER BY skillName
                    """;

                var result = tx.run(cypher);

                return result.stream()
                        .map(record -> record.get("skillName").asString())
                        .toList();
            });
        } catch (Exception e) {
            log.error("Failed to get all skills", e);
            return List.of();
        }
    }

    /**
     * Get top N most in-demand skills based on job listing count
     *
     * @param topN Number of top skills to return
     * @return Map of skill name to job count
     */
    public Map<String, Long> getTopInDemandSkills(int topN) {
        SessionConfig sessionConfig = SessionConfig.builder()
                .withDatabase(database)
                .build();

        try (Session session = neo4jDriver.session(sessionConfig)) {
            return session.executeRead(tx -> {
                String cypher = """
                    MATCH (job:JobListing)-[:REQUIRES_SKILL]->(skill:Skill)
                    WITH skill, COUNT(DISTINCT job) as jobCount
                    ORDER BY jobCount DESC
                    LIMIT $topN
                    RETURN skill.name as skillName, jobCount
                    """;

                var result = tx.run(cypher, Map.of("topN", topN));

                return result.stream()
                        .collect(java.util.stream.Collectors.toMap(
                                record -> record.get("skillName").asString(),
                                record -> record.get("jobCount").asLong()
                        ));
            });
        } catch (Exception e) {
            log.error("Failed to get top in-demand skills", e);
            return Map.of();
        }
    }
}
