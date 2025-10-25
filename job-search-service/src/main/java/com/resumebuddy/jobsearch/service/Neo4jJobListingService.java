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

    /**
     * Get jobs by skills (AND logic - must have ALL skills)
     * Returns job IDs and date distribution for skill drill-down feature
     *
     * @param skillNames List of skill names (AND logic)
     * @return Map with jobIds and dateDistribution
     */
    public Map<String, Object> getJobsBySkills(List<String> skillNames) {
        log.info("Finding jobs requiring ALL skills: {}", skillNames);

        SessionConfig sessionConfig = SessionConfig.builder()
                .withDatabase(database)
                .build();

        try (Session session = neo4jDriver.session(sessionConfig)) {
            return session.executeRead(tx -> {
                // Cypher: Find jobs that have ALL specified skills
                String cypher = """
                    MATCH (job:JobListing)-[:REQUIRES_SKILL]->(skill:Skill)
                    WHERE skill.name IN $skillNames
                    WITH job, COUNT(DISTINCT skill) as matchCount
                    WHERE matchCount = SIZE($skillNames)
                    RETURN job.id as jobId,
                           date(job.lastUpdated) as jobDate
                    ORDER BY job.lastUpdated DESC
                    """;

                var result = tx.run(cypher, Map.of("skillNames", skillNames));

                List<String> jobIds = new java.util.ArrayList<>();
                Map<String, Long> dateDistribution = new java.util.HashMap<>();

                result.stream().forEach(record -> {
                    String jobId = record.get("jobId").asString();
                    jobIds.add(jobId);

                    // Group by week for date distribution (ISO week format: YYYY-Www)
                    try {
                        String dateStr = record.get("jobDate").asString();
                        // Extract week from date (e.g., "2025-01-15" -> "2025-W03")
                        String weekKey = dateStr.substring(0, 8) + "01"; // Simplify to month start
                        dateDistribution.merge(weekKey, 1L, Long::sum);
                    } catch (Exception e) {
                        log.debug("Could not parse date for job {}", jobId);
                    }
                });

                log.info("Found {} jobs matching ALL skills: {}", jobIds.size(), skillNames);

                return Map.of(
                        "jobIds", jobIds,
                        "dateDistribution", dateDistribution
                );
            });
        } catch (Exception e) {
            log.error("Failed to get jobs by skills: {}", skillNames, e);
            return Map.of(
                    "jobIds", List.of(),
                    "dateDistribution", Map.of()
            );
        }
    }

    /**
     * Get related skills that co-occur with selected skills
     * Used for skill drill-down: shows what other skills appear in jobs with selected skills
     *
     * @param selectedSkills List of already selected skills
     * @param limit Maximum number of related skills to return
     * @return Map of skill name to job count
     */
    public Map<String, Long> getRelatedSkills(List<String> selectedSkills, int limit) {
        log.info("Finding skills related to: {}", selectedSkills);

        SessionConfig sessionConfig = SessionConfig.builder()
                .withDatabase(database)
                .build();

        try (Session session = neo4jDriver.session(sessionConfig)) {
            return session.executeRead(tx -> {
                // Cypher: Find jobs with ALL selected skills, then get OTHER skills in those jobs
                String cypher = """
                    MATCH (job:JobListing)-[:REQUIRES_SKILL]->(selected:Skill)
                    WHERE selected.name IN $selectedSkills
                    WITH job, COUNT(DISTINCT selected) as matchCount
                    WHERE matchCount = SIZE($selectedSkills)
                    MATCH (job)-[:REQUIRES_SKILL]->(other:Skill)
                    WHERE NOT other.name IN $selectedSkills
                    RETURN other.name as skillName,
                           COUNT(DISTINCT job) as jobCount
                    ORDER BY jobCount DESC
                    LIMIT $limit
                    """;

                var result = tx.run(cypher, Map.of(
                        "selectedSkills", selectedSkills,
                        "limit", limit
                ));

                Map<String, Long> relatedSkills = new java.util.LinkedHashMap<>();
                result.stream().forEach(record -> {
                    String skillName = record.get("skillName").asString();
                    Long jobCount = record.get("jobCount").asLong();
                    relatedSkills.put(skillName, jobCount);
                });

                log.info("Found {} related skills for: {}", relatedSkills.size(), selectedSkills);

                return relatedSkills;
            });
        } catch (Exception e) {
            log.error("Failed to get related skills for: {}", selectedSkills, e);
            return Map.of();
        }
    }

    /**
     * Get skill co-occurrence matrix for heatmap visualization
     * Returns a symmetric matrix showing how often pairs of top skills appear together
     *
     * @param topN Number of top skills to include in matrix
     * @return Map with skill names list, co-occurrence matrix, and max value
     */
    public Map<String, Object> getSkillCooccurrenceMatrix(int topN) {
        log.info("Building skill co-occurrence matrix for top {} skills", topN);

        SessionConfig sessionConfig = SessionConfig.builder()
                .withDatabase(database)
                .build();

        try (Session session = neo4jDriver.session(sessionConfig)) {
            return session.executeRead(tx -> {
                // Step 1: Get top N skills by job count
                String topSkillsCypher = """
                    MATCH (job:JobListing)-[:REQUIRES_SKILL]->(s:Skill)
                    WITH s.name as skillName, COUNT(DISTINCT job) as jobCount
                    ORDER BY jobCount DESC
                    LIMIT $topN
                    RETURN skillName
                    """;

                var topSkillsResult = tx.run(topSkillsCypher, Map.of("topN", topN));
                List<String> topSkills = topSkillsResult.stream()
                        .map(record -> record.get("skillName").asString())
                        .toList();

                if (topSkills.isEmpty()) {
                    log.warn("No skills found for heatmap");
                    return Map.of(
                            "skillNames", List.of(),
                            "cooccurrenceMatrix", Map.of(),
                            "maxCooccurrence", 0L,
                            "totalJobs", 0L
                    );
                }

                // Step 2: For each pair of skills, count co-occurrences (jobs with BOTH skills)
                String cooccurrenceCypher = """
                    UNWIND $skills AS skill1
                    UNWIND $skills AS skill2
                    OPTIONAL MATCH (job:JobListing)-[:REQUIRES_SKILL]->(s1:Skill {name: skill1}),
                                   (job)-[:REQUIRES_SKILL]->(s2:Skill {name: skill2})
                    WITH skill1, skill2, COUNT(DISTINCT job) as cooccurrence
                    RETURN skill1, skill2, cooccurrence
                    """;

                var cooccurrenceResult = tx.run(cooccurrenceCypher, Map.of("skills", topSkills));

                // Build matrix as nested map
                Map<String, Map<String, Long>> matrix = new java.util.HashMap<>();
                long maxCooccurrence = 0L;

                for (String skill : topSkills) {
                    matrix.put(skill, new java.util.HashMap<>());
                }

                cooccurrenceResult.stream().forEach(record -> {
                    String skill1 = record.get("skill1").asString();
                    String skill2 = record.get("skill2").asString();
                    Long count = record.get("cooccurrence").asLong();

                    matrix.get(skill1).put(skill2, count);
                });

                // Find max co-occurrence for color scaling
                for (Map<String, Long> row : matrix.values()) {
                    for (Long count : row.values()) {
                        if (count > maxCooccurrence) {
                            maxCooccurrence = count;
                        }
                    }
                }

                // Get total job count
                String totalJobsCypher = "MATCH (job:JobListing) RETURN COUNT(job) as total";
                var totalJobsResult = tx.run(totalJobsCypher);
                long totalJobs = totalJobsResult.hasNext() ?
                        totalJobsResult.next().get("total").asLong() : 0L;

                log.info("Built {}x{} co-occurrence matrix, max value: {}",
                        topSkills.size(), topSkills.size(), maxCooccurrence);

                return Map.of(
                        "skillNames", topSkills,
                        "cooccurrenceMatrix", matrix,
                        "maxCooccurrence", maxCooccurrence,
                        "totalJobs", totalJobs
                );
            });
        } catch (Exception e) {
            log.error("Failed to build skill co-occurrence matrix", e);
            return Map.of(
                    "skillNames", List.of(),
                    "cooccurrenceMatrix", Map.of(),
                    "maxCooccurrence", 0L,
                    "totalJobs", 0L
            );
        }
    }

    /**
     * Get skill path matrix for career exploration
     * Shows co-occurrence between user skills (rows) and market gap skills (columns)
     *
     * @param userSkills List of skills the user already has
     * @param topMarketSkills Number of top market skills to show as columns
     * @return Map with userSkills, marketSkills, co-occurrence matrix, and max value
     */
    public Map<String, Object> getSkillPathMatrix(List<String> userSkills, int topMarketSkills) {
        log.info("Building skill path matrix: {} user skills × top {} market skills",
                userSkills.size(), topMarketSkills);

        SessionConfig sessionConfig = SessionConfig.builder()
                .withDatabase(database)
                .build();

        try (Session session = neo4jDriver.session(sessionConfig)) {
            return session.executeRead(tx -> {
                // Step 1: Get top N market skills (excluding user skills)
                String topMarketSkillsCypher = """
                    MATCH (job:JobListing)-[:REQUIRES_SKILL]->(s:Skill)
                    WHERE NOT s.name IN $userSkills
                    WITH s.name as skillName, COUNT(DISTINCT job) as jobCount
                    ORDER BY jobCount DESC
                    LIMIT $topN
                    RETURN skillName
                    """;

                var marketSkillsResult = tx.run(topMarketSkillsCypher,
                        Map.of("userSkills", userSkills, "topN", topMarketSkills));
                List<String> marketSkillsList = marketSkillsResult.stream()
                        .map(record -> record.get("skillName").asString())
                        .toList();

                if (marketSkillsList.isEmpty()) {
                    log.warn("No market skills found for path matrix");
                    return Map.of(
                            "userSkills", userSkills,
                            "marketSkills", List.of(),
                            "cooccurrenceMatrix", Map.of(),
                            "maxCooccurrence", 0L
                    );
                }

                // Step 2: For each user skill × market skill pair, count co-occurrences
                String cooccurrenceCypher = """
                    UNWIND $userSkills AS userSkill
                    UNWIND $marketSkills AS marketSkill
                    OPTIONAL MATCH (job:JobListing)-[:REQUIRES_SKILL]->(s1:Skill {name: userSkill}),
                                   (job)-[:REQUIRES_SKILL]->(s2:Skill {name: marketSkill})
                    WITH userSkill, marketSkill, COUNT(DISTINCT job) as cooccurrence
                    RETURN userSkill, marketSkill, cooccurrence
                    """;

                var cooccurrenceResult = tx.run(cooccurrenceCypher,
                        Map.of("userSkills", userSkills, "marketSkills", marketSkillsList));

                // Build matrix as nested map: userSkill -> marketSkill -> count
                Map<String, Map<String, Long>> matrix = new java.util.HashMap<>();
                final long[] maxCooccurrence = {0L}; // Use array to make it effectively final

                for (String userSkill : userSkills) {
                    matrix.put(userSkill, new java.util.HashMap<>());
                }

                cooccurrenceResult.stream().forEach(record -> {
                    String userSkill = record.get("userSkill").asString();
                    String marketSkill = record.get("marketSkill").asString();
                    Long count = record.get("cooccurrence").asLong();

                    matrix.get(userSkill).put(marketSkill, count);
                    if (count > maxCooccurrence[0]) {
                        maxCooccurrence[0] = count;
                    }
                });

                log.info("Built {}×{} skill path matrix, max value: {}",
                        userSkills.size(), marketSkillsList.size(), maxCooccurrence[0]);

                return Map.of(
                        "userSkills", userSkills,
                        "marketSkills", marketSkillsList,
                        "cooccurrenceMatrix", matrix,
                        "maxCooccurrence", maxCooccurrence[0]
                );
            });
        } catch (Exception e) {
            log.error("Failed to build skill path matrix", e);
            return Map.of(
                    "userSkills", userSkills,
                    "marketSkills", List.of(),
                    "cooccurrenceMatrix", Map.of(),
                    "maxCooccurrence", 0L
            );
        }
    }
}
