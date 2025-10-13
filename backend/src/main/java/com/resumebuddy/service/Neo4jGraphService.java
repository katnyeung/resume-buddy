package com.resumebuddy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.model.ResumeAnalysisExperience;
import com.resumebuddy.model.dto.NormalizedJobDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Neo4j Graph Database Service
 * Manages the knowledge graph for job analysis, occupations, and work activities
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class Neo4jGraphService {

    private final Driver neo4jDriver;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.openai.api-key}")
    private String apiKey;

    @Value("${app.openai.model}")
    private String model;

    @Value("${app.openai.base-url}")
    private String baseUrl;

    /**
     * Store job experience and occupation mapping in Neo4j graph with pre-extracted skills
     * This version accepts skills from comprehensive LLM analysis
     */
    // Instance variable to store skills for later mapping
    private List<Map<String, Object>> lastExtractedSkills = new ArrayList<>();

    public void storeJobAnalysisInGraphWithSkills(
            String resumeId,
            String experienceId,
            ResumeAnalysisExperience experience,
            NormalizedJobDto normalized,
            List<Map<String, Object>> skills) {

        log.info("Storing job analysis in Neo4j graph for experience: {}", experienceId);

        try (Session session = neo4jDriver.session()) {
            // Step 0: Clean up existing data for this experience (in case of re-analysis)
            session.executeWrite(tx -> {
                cleanupExistingJobData(tx, experienceId);
                return null;
            });

            log.info("Using {} pre-extracted skills from comprehensive analysis", skills.size());

            // Save for later O*NET mapping
            this.lastExtractedSkills = skills;

            // Step 1: Store everything in Neo4j
            session.executeWrite(tx -> {
                // Create or merge ALL Occupation nodes (multi-occupation support)
                for (NormalizedJobDto.SocCodeMapping socMapping : normalized.getSocCodes()) {
                    createOccupationNodeForSocCode(tx, socMapping, normalized);
                }

                // Create JobExperience node (MERGE not CREATE)
                createJobExperienceNode(tx, resumeId, experienceId, experience, normalized);

                // Note: MAPS_TO relationships are created later by createMultiOccupationMappings()
                // to ensure all occupation nodes exist first

                // Create Skill nodes and relationships
                createSkillNodesAndRelationships(tx, experienceId, skills);

                return null;
            });

            log.info("Successfully stored job analysis in Neo4j graph with {} skills", skills.size());

        } catch (Exception e) {
            log.error("Error storing job analysis in Neo4j graph", e);
            // Don't throw - allow the analysis to continue even if graph storage fails
        }
    }

    /**
     * Store job experience and occupation mapping in Neo4j graph
     * @deprecated Use storeJobAnalysisInGraphWithSkills instead - accepts pre-extracted skills
     */
    @Deprecated
    public void storeJobAnalysisInGraph(
            String resumeId,
            String experienceId,
            ResumeAnalysisExperience experience,
            NormalizedJobDto normalized) {

        log.info("Storing job analysis in Neo4j graph for experience: {}", experienceId);

        try (Session session = neo4jDriver.session()) {
            // Step 0: Clean up existing data for this experience (in case of re-analysis)
            session.executeWrite(tx -> {
                cleanupExistingJobData(tx, experienceId);
                return null;
            });

            // Step 1: Extract skills from job description using LLM
            List<Map<String, Object>> skills = extractSkillsWithLLM(
                experience.getJobTitle(),
                experience.getDescription()
            );
            log.info("Extracted {} skills from job description", skills.size());

            // Save for later O*NET mapping
            this.lastExtractedSkills = skills;

            // Step 2: Store everything in Neo4j
            session.executeWrite(tx -> {
                // Create or merge ALL Occupation nodes (multi-occupation support)
                for (NormalizedJobDto.SocCodeMapping socMapping : normalized.getSocCodes()) {
                    createOccupationNodeForSocCode(tx, socMapping, normalized);
                }

                // Create JobExperience node (MERGE not CREATE)
                createJobExperienceNode(tx, resumeId, experienceId, experience, normalized);

                // Note: MAPS_TO relationships are created later by createMultiOccupationMappings()
                // to ensure all occupation nodes exist first

                // Create Skill nodes and relationships
                createSkillNodesAndRelationships(tx, experienceId, skills);

                return null;
            });

            log.info("Successfully stored job analysis in Neo4j graph with {} skills", skills.size());

        } catch (Exception e) {
            log.error("Error storing job analysis in Neo4j graph", e);
            // Don't throw - allow the analysis to continue even if graph storage fails
        }
    }

    /**
     * Clean up existing job data before re-analysis
     * Deletes the JobExperience node and all its relationships to avoid duplicates
     */
    private void cleanupExistingJobData(TransactionContext tx, String experienceId) {
        String query = """
            MATCH (je:JobExperience {id: $experienceId})
            DETACH DELETE je
            """;

        tx.run(query, Values.parameters("experienceId", experienceId));
        log.debug("Cleaned up existing JobExperience node and relationships for experience: {}", experienceId);
    }

    /**
     * Get the last extracted skills for mapping to O*NET
     */
    public List<Map<String, Object>> getLastExtractedSkills() {
        return lastExtractedSkills;
    }

    /**
     * Create or update Occupation node for a specific SOC code mapping
     */
    private void createOccupationNodeForSocCode(TransactionContext tx, NormalizedJobDto.SocCodeMapping socMapping, NormalizedJobDto normalized) {
        String query = """
            MERGE (o:Occupation {soc_code: $socCode})
            SET o.title = $title,
                o.normalized_title = $title,
                o.confidence = $confidence,
                o.seniority_level = $seniority,
                o.technical_depth = $technicalDepth,
                o.has_leadership = $hasLeadership,
                o.last_updated = datetime()
            RETURN o
            """;

        tx.run(query, Values.parameters(
                "socCode", socMapping.getCode(),
                "title", socMapping.getTitle(),
                "confidence", socMapping.getConfidence(),
                "seniority", normalized.getSeniority(),
                "technicalDepth", normalized.getTechnicalDepth(),
                "hasLeadership", normalized.getHasLeadership()
        ));

        log.debug("Created/updated Occupation node: {} ({})", socMapping.getTitle(), socMapping.getCode());
    }

    /**
     * Create or update Occupation node (legacy - for primary occupation only)
     * @deprecated Use createOccupationNodeForSocCode for multi-occupation support
     */
    @Deprecated
    private void createOccupationNode(TransactionContext tx, NormalizedJobDto normalized) {
        if (normalized.getSocCodes() == null || normalized.getSocCodes().isEmpty()) {
            return;
        }
        createOccupationNodeForSocCode(tx, normalized.getSocCodes().get(0), normalized);
    }

    /**
     * Create or update JobExperience node (uses MERGE to avoid duplicates)
     */
    private void createJobExperienceNode(
            TransactionContext tx,
            String resumeId,
            String experienceId,
            ResumeAnalysisExperience experience,
            NormalizedJobDto normalized) {

        String query = """
            MERGE (je:JobExperience {id: $id})
            SET je.resume_id = $resumeId,
                je.original_title = $originalTitle,
                je.normalized_title = $normalizedTitle,
                je.company = $company,
                je.start_date = $startDate,
                je.end_date = $endDate,
                je.seniority = $seniority,
                je.description = $description,
                je.analyzed_at = datetime()
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

        log.debug("Created/updated JobExperience node: {} at {}", experience.getJobTitle(), experience.getCompanyName());
    }

    /**
     * Create MAPS_TO relationships to multiple occupations
     * This allows one job to map to multiple O*NET occupations for broader skill coverage
     * Note: Old MAPS_TO relationships are already deleted by cleanupExistingJobData()
     */
    public void createMultiOccupationMappings(String experienceId, List<NormalizedJobDto.SocCodeMapping> socCodes) {
        log.info("Creating {} occupation mappings for experience {}", socCodes.size(), experienceId);

        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                for (int i = 0; i < socCodes.size(); i++) {
                    NormalizedJobDto.SocCodeMapping socMapping = socCodes.get(i);
                    boolean isPrimary = (i == 0);

                    String query = """
                        MATCH (je:JobExperience {id: $experienceId})
                        MATCH (o:Occupation {soc_code: $socCode})
                        MERGE (je)-[r:MAPS_TO]->(o)
                        SET r.confidence = $confidence,
                            r.is_primary = $isPrimary,
                            r.rank = $rank,
                            r.created_at = datetime()
                        RETURN r
                        """;

                    tx.run(query, Values.parameters(
                            "experienceId", experienceId,
                            "socCode", socMapping.getCode(),
                            "confidence", socMapping.getConfidence(),
                            "isPrimary", isPrimary,
                            "rank", i + 1
                    ));

                    log.debug("Created MAPS_TO relationship: experience {} -> {} ({}, confidence: {}, rank: {})",
                            experienceId, socMapping.getCode(), socMapping.getTitle(),
                            socMapping.getConfidence(), i + 1);
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Error creating multi-occupation mappings", e);
        }
    }

    /**
     * Create relationship: (JobExperience)-[:MAPS_TO]->(Occupation)
     * Legacy method for single occupation - kept for backward compatibility
     */
    private void createJobToOccupationRelationship(TransactionContext tx, String experienceId, String socCode) {
        String query = """
            MATCH (je:JobExperience {id: $experienceId})
            MATCH (o:Occupation {soc_code: $socCode})
            MERGE (je)-[r:MAPS_TO]->(o)
            SET r.created_at = datetime(),
                r.is_primary = true,
                r.rank = 1
            RETURN r
            """;

        tx.run(query, Values.parameters(
                "experienceId", experienceId,
                "socCode", socCode
        ));

        log.debug("Created MAPS_TO relationship from experience {} to occupation {}", experienceId, socCode);
    }

    /**
     * Check if occupation's O*NET data (skills, technologies, tasks, activities)
     * needs to be refreshed based on last update time.
     *
     * @param socCode SOC code to check
     * @param daysThreshold Number of days before data is considered stale
     * @return true if data doesn't exist or is older than threshold, false otherwise
     */
    public boolean isOccupationDataStale(String socCode, int daysThreshold) {
        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                String query = """
                    MATCH (o:Occupation {soc_code: $socCode})
                    RETURN o.onet_data_updated_at AS lastUpdated
                    """;

                Result result = tx.run(query, Values.parameters("socCode", socCode));

                if (!result.hasNext()) {
                    // Occupation doesn't exist yet - needs to be populated
                    log.debug("SOC {} not found in graph - needs O*NET data", socCode);
                    return true;
                }

                Record record = result.single();
                if (record.get("lastUpdated").isNull()) {
                    // O*NET data never populated for this occupation
                    log.debug("SOC {} exists but O*NET data never populated", socCode);
                    return true;
                }

                // Neo4j datetime() returns ZonedDateTime, not LocalDateTime
                java.time.ZonedDateTime lastUpdated = record.get("lastUpdated").asZonedDateTime();
                java.time.ZonedDateTime threshold = java.time.ZonedDateTime.now().minusDays(daysThreshold);
                boolean isStale = lastUpdated.isBefore(threshold);

                log.debug("SOC {} last O*NET update: {}, threshold: {} days, isStale: {}",
                        socCode, lastUpdated, daysThreshold, isStale);

                return isStale;
            });
        } catch (Exception e) {
            log.error("Error checking occupation staleness for SOC {}: {}", socCode, e.getMessage());
            // On error, return true to trigger refresh (fail-safe behavior)
            return true;
        }
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
     * Create O*NET Skill, Technology, and Activity nodes from O*NET data
     */
    private void createActivityNodesFromONetData(TransactionContext tx, String socCode, String onetData) {
        try {
            JsonNode root = objectMapper.readTree(onetData);

            // Extract and create O*NET Skill nodes
            JsonNode skillsArray = root.path("skills");
            if (skillsArray.isArray()) {
                skillsArray.forEach(skill -> {
                    createONetSkillNode(tx, socCode, skill);
                });
            }

            // Extract and create O*NET Technology nodes
            JsonNode techArray = root.path("technology_skills");
            if (techArray.isArray()) {
                techArray.forEach(tech -> {
                    createONetTechnologyNode(tx, socCode, tech);
                });
            }

            // Extract and create O*NET Task nodes (programmer-specific tasks)
            JsonNode tasksArray = root.path("tasks");
            if (tasksArray.isArray()) {
                tasksArray.forEach(task -> {
                    createONetTaskNode(tx, socCode, task);
                });
            }

            // Extract and create O*NET Activity nodes (detailed work activities)
            JsonNode activitiesArray = root.path("detailed_work_activities");
            if (activitiesArray.isArray()) {
                activitiesArray.forEach(activity -> {
                    createONetActivityNode(tx, socCode, activity);
                });
            }

            // Update occupation's onet_data_updated_at timestamp to mark data as fresh
            String updateTimestampQuery = """
                MATCH (o:Occupation {soc_code: $socCode})
                SET o.onet_data_updated_at = datetime()
                RETURN o
                """;
            tx.run(updateTimestampQuery, Values.parameters("socCode", socCode));

            log.info("Created O*NET skill/technology/task/activity nodes for occupation: {}", socCode);

        } catch (Exception e) {
            log.error("Error parsing O*NET data for {}: {}", socCode, e.getMessage());
        }
    }

    /**
     * Create individual O*NET Skill node and link to Occupation
     */
    private void createONetSkillNode(TransactionContext tx, String socCode, JsonNode skill) {
        String skillName = skill.path("name").asText();
        if (skillName.isEmpty()) return;

        String skillId = "onet-skill-" + skillName.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        double importance = skill.path("level").asDouble(0);

        String query = """
            MERGE (s:ONetSkill {id: $skillId})
            SET s.name = $name,
                s.importance = $importance,
                s.type = 'cognitive',
                s.last_updated = datetime()
            WITH s
            MATCH (o:Occupation {soc_code: $socCode})
            MERGE (o)-[r:REQUIRES_SKILL]->(s)
            SET r.importance = $importance
            RETURN s
            """;

        tx.run(query, Values.parameters(
            "skillId", skillId,
            "name", skillName,
            "importance", importance,
            "socCode", socCode
        ));

        log.debug("Created ONetSkill: {} for occupation {}", skillName, socCode);
    }

    /**
     * Create individual O*NET Technology node, link to Category, and link to Occupation
     */
    private void createONetTechnologyNode(TransactionContext tx, String socCode, JsonNode tech) {
        String techName = tech.path("name").asText();
        if (techName.isEmpty()) return;

        String techId = "onet-tech-" + techName.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        String categoryName = tech.path("category").asText("");

        // Generate category ID
        String categoryId = "";
        if (!categoryName.isEmpty()) {
            categoryId = "onet-tech-cat-" + categoryName.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        }

        String query = """
            // Create or merge the technology category node
            MERGE (c:ONetTechnologyCategory {id: $categoryId})
            SET c.name = $categoryName,
                c.last_updated = datetime()

            // Create or merge the technology node
            MERGE (t:ONetTechnology {id: $techId})
            SET t.name = $name,
                t.last_updated = datetime()

            // Link technology to category
            MERGE (t)-[:BELONGS_TO_CATEGORY]->(c)

            // Link occupation to technology
            WITH t
            MATCH (o:Occupation {soc_code: $socCode})
            MERGE (o)-[r:USES_TECHNOLOGY]->(t)
            RETURN t
            """;

        tx.run(query, Values.parameters(
            "techId", techId,
            "name", techName,
            "categoryId", categoryId,
            "categoryName", categoryName,
            "socCode", socCode
        ));

        log.debug("Created ONetTechnology: {} in category: {} for occupation {}", techName, categoryName, socCode);
    }

    /**
     * Create individual O*NET Task node and link to Occupation
     * Tasks are more specific than "Detailed Work Activities" - they represent
     * actual day-to-day tasks like "Write, analyze, review programs"
     */
    private void createONetTaskNode(TransactionContext tx, String socCode, JsonNode task) {
        String taskName = task.path("name").asText();
        if (taskName.isEmpty()) return;

        // Generate ID from task name (truncated to avoid extremely long IDs)
        String truncated = taskName.length() > 100 ? taskName.substring(0, 100) : taskName;
        String taskId = "onet-task-" + truncated.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("-+$", "");

        double importance = task.path("importance").asDouble(0);
        String category = task.path("category").asText("Core");

        String query = """
            MERGE (t:ONetTask {id: $taskId})
            SET t.name = $name,
                t.importance = $importance,
                t.category = $category,
                t.last_updated = datetime()
            WITH t
            MATCH (o:Occupation {soc_code: $socCode})
            MERGE (o)-[r:REQUIRES_TASK]->(t)
            SET r.importance = $importance,
                r.category = $category
            RETURN t
            """;

        tx.run(query, Values.parameters(
            "taskId", taskId,
            "name", taskName,
            "importance", importance,
            "category", category,
            "socCode", socCode
        ));

        log.debug("Created ONetTask: {} (importance: {}, category: {}) for occupation {}",
            taskName.substring(0, Math.min(50, taskName.length())), importance, category, socCode);
    }

    /**
     * Create individual O*NET Activity node and link to Occupation
     * @deprecated Use createONetTaskNode instead - Tasks are more specific than Activities
     */
    @Deprecated
    private void createONetActivityNode(TransactionContext tx, String socCode, JsonNode activity) {
        String activityName = activity.path("name").asText();
        if (activityName.isEmpty()) return;

        String activityId = "onet-activity-" + activityName.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        double importance = activity.path("importance").asDouble(0);

        String query = """
            MERGE (a:ONetActivity {id: $activityId})
            SET a.name = $name,
                a.importance = $importance,
                a.last_updated = datetime()
            WITH a
            MATCH (o:Occupation {soc_code: $socCode})
            MERGE (o)-[r:REQUIRES_ACTIVITY]->(a)
            SET r.importance = $importance
            RETURN a
            """;

        tx.run(query, Values.parameters(
            "activityId", activityId,
            "name", activityName,
            "importance", importance,
            "socCode", socCode
        ));

        log.debug("Created ONetActivity: {} for occupation {}", activityName, socCode);
    }

    /**
     * Fetch O*NET skills and technologies for an occupation from Neo4j graph
     * instead of using LLM to map them. This avoids expensive LLM calls by
     * querying existing O*NET taxonomy data already stored in the graph.
     *
     * @param socCode SOC code to fetch O*NET data for
     * @return Map with "skills" and "technologies" lists
     */
    public Map<String, List<String>> fetchONetTaxonomyFromGraph(String socCode) {
        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                Map<String, List<String>> result = new HashMap<>();

                // Fetch O*NET soft skills
                String skillQuery = """
                    MATCH (o:Occupation {soc_code: $socCode})-[:REQUIRES_SKILL]->(s:ONetSkill)
                    RETURN s.name AS name
                    ORDER BY s.importance DESC
                    """;
                Result skillResult = tx.run(skillQuery, Values.parameters("socCode", socCode));
                List<String> skills = skillResult.list(record -> record.get("name").asString());

                // Fetch O*NET technologies
                String techQuery = """
                    MATCH (o:Occupation {soc_code: $socCode})-[:USES_TECHNOLOGY]->(t:ONetTechnology)
                    RETURN t.name AS name
                    ORDER BY t.name
                    """;
                Result techResult = tx.run(techQuery, Values.parameters("socCode", socCode));
                List<String> technologies = techResult.list(record -> record.get("name").asString());

                result.put("skills", skills);
                result.put("technologies", technologies);

                log.info("Fetched from Neo4j graph for SOC {}: {} soft skills, {} technologies",
                        socCode, skills.size(), technologies.size());

                return result;
            });
        } catch (Exception e) {
            log.error("Error fetching O*NET taxonomy from graph for SOC {}: {}", socCode, e.getMessage());
            return Map.of("skills", new ArrayList<>(), "technologies", new ArrayList<>());
        }
    }

    /**
     * Create skill mapping relationships using rule-based matching instead of LLM.
     * Uses simple string matching and category-based rules to map job skills to O*NET taxonomy.
     * This is much faster and cheaper than LLM-based mapping while maintaining good accuracy.
     *
     * @param experienceId The job experience ID
     * @param socCode The SOC code for the occupation
     * @param jobSkills List of extracted job skills
     * @param onetTaxonomy Map containing "skills" and "technologies" from Neo4j graph
     */
    public void createSkillMappingsFromGraph(
            String experienceId,
            String socCode,
            List<Map<String, Object>> jobSkills,
            Map<String, List<String>> onetTaxonomy) {

        List<String> onetSkills = onetTaxonomy.get("skills");
        List<String> onetTechnologies = onetTaxonomy.get("technologies");

        log.info("Creating skill mappings for {} job skills using graph taxonomy ({} O*NET skills, {} technologies)",
                jobSkills.size(), onetSkills.size(), onetTechnologies.size());

        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                int softSkillMappings = 0;
                int techMappings = 0;

                for (Map<String, Object> jobSkill : jobSkills) {
                    String skillName = (String) jobSkill.get("name");
                    String category = (String) jobSkill.get("category");

                    // Rule 1: Exact match with O*NET technology (case-insensitive)
                    for (String tech : onetTechnologies) {
                        if (skillName.equalsIgnoreCase(tech)) {
                            createRelatedToRelationshipRuleBased(tx, experienceId, skillName, tech, socCode, 1.0, "exact");
                            techMappings++;
                            break;
                        }
                    }

                    // Rule 2: Partial/substring match for technologies
                    for (String tech : onetTechnologies) {
                        String skillLower = skillName.toLowerCase();
                        String techLower = tech.toLowerCase();

                        if (skillLower.contains(techLower) || techLower.contains(skillLower)) {
                            // Skip if already matched exactly
                            if (!skillName.equalsIgnoreCase(tech)) {
                                createRelatedToRelationshipRuleBased(tx, experienceId, skillName, tech, socCode, 0.85, "partial");
                                techMappings++;
                            }
                        }
                    }

                    // Rule 3: Match extracted soft skills with O*NET soft skills
                    // Soft skills like "Programming", "Critical Thinking" extracted from job description
                    // will match O*NET soft skills directly (handled by exact/partial match above for skills too)
                    for (String onetSkill : onetSkills) {
                        String skillLower = skillName.toLowerCase();
                        String onetSkillLower = onetSkill.toLowerCase();

                        // Exact match
                        if (skillLower.equals(onetSkillLower)) {
                            createDemonstratesRelationshipRuleBased(tx, experienceId, skillName, onetSkill, socCode, 1.0);
                            softSkillMappings++;
                            break;
                        }
                        // Partial match (e.g., "Programming Languages" contains "Programming")
                        else if (skillLower.contains(onetSkillLower) || onetSkillLower.contains(skillLower)) {
                            createDemonstratesRelationshipRuleBased(tx, experienceId, skillName, onetSkill, socCode, 0.85);
                            softSkillMappings++;
                            break;
                        }
                    }
                }

                log.info("Created {} soft skill mappings and {} technology mappings using rule-based matching",
                        softSkillMappings, techMappings);

                return null;
            });
        } catch (Exception e) {
            log.error("Error creating skill mappings from graph: {}", e.getMessage());
        }
    }

    /**
     * Helper method to check if a list contains a string (case-insensitive)
     */
    private boolean containsIgnoreCase(List<String> list, String target) {
        return list.stream().anyMatch(item -> item.equalsIgnoreCase(target));
    }

    /**
     * Create DEMONSTRATES relationship using rule-based mapping (not LLM)
     */
    private void createDemonstratesRelationshipRuleBased(TransactionContext tx, String experienceId,
                                                         String jobSkill, String onetSkill, String socCode, double confidence) {
        String skillId = "skill-" + jobSkill.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        String onetSkillId = "onet-skill-" + onetSkill.toLowerCase().replaceAll("[^a-z0-9]+", "-");

        String query = """
            MATCH (exp:JobExperience {id: $experienceId})-[:REQUIRES_SKILL]->(s:Skill {id: $skillId})
            MATCH (os:ONetSkill {id: $onetSkillId})
            MERGE (s)-[r:DEMONSTRATES]->(os)
            SET r.confidence = $confidence,
                r.mapped_by = 'rule',
                r.mapped_at = datetime()
            RETURN s, r, os
            """;

        tx.run(query, Values.parameters(
                "experienceId", experienceId,
                "skillId", skillId,
                "onetSkillId", onetSkillId,
                "confidence", confidence
        ));

        log.debug("Created DEMONSTRATES: {} -> {} (confidence: {}, rule-based)", jobSkill, onetSkill, confidence);
    }

    /**
     * Create RELATED_TO relationship using rule-based mapping (not LLM)
     */
    private void createRelatedToRelationshipRuleBased(TransactionContext tx, String experienceId, String jobSkill,
                                                      String onetTech, String socCode, double confidence, String relationshipType) {
        String skillId = "skill-" + jobSkill.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        String techId = "onet-tech-" + onetTech.toLowerCase().replaceAll("[^a-z0-9]+", "-");

        String query = """
            MATCH (exp:JobExperience {id: $experienceId})-[:REQUIRES_SKILL]->(s:Skill {id: $skillId})
            MATCH (t:ONetTechnology {id: $techId})
            MERGE (s)-[r:RELATED_TO]->(t)
            SET r.confidence = $confidence,
                r.relationship = $relationship,
                r.mapped_by = 'rule',
                r.mapped_at = datetime()
            RETURN s, r, t
            """;

        tx.run(query, Values.parameters(
                "experienceId", experienceId,
                "skillId", skillId,
                "techId", techId,
                "confidence", confidence,
                "relationship", relationshipType
        ));

        log.debug("Created RELATED_TO: {} -> {} ({}, confidence: {}, rule-based)",
                jobSkill, onetTech, relationshipType, confidence);
    }

    /**
     * Map job skills to O*NET skills and technologies using LLM
     * Creates DEMONSTRATES (Skill -> ONetSkill) and RELATED_TO (Skill -> ONetTechnology) relationships
     * Note: Old skill mapping relationships are already cleaned by cleanupExistingJobData()
     */
    public void mapSkillsToONet(String experienceId, String socCode, List<Map<String, Object>> jobSkills, String onetDataJson) {
        log.info("Mapping {} job skills to O*NET skills/technologies for experience: {}", jobSkills.size(), experienceId);

        try {
            // Parse O*NET data to get available soft skills and technologies
            JsonNode onetData = objectMapper.readTree(onetDataJson);
            JsonNode onetSkills = onetData.path("skills");
            JsonNode onetTechnologies = onetData.path("technology_skills");

            // Build lists for LLM prompt
            List<String> onetSkillNames = new ArrayList<>();
            if (onetSkills.isArray()) {
                onetSkills.forEach(skill -> onetSkillNames.add(skill.path("name").asText()));
            }

            List<String> onetTechNames = new ArrayList<>();
            if (onetTechnologies.isArray()) {
                onetTechnologies.forEach(tech -> onetTechNames.add(tech.path("name").asText()));
            }

            List<String> jobSkillNames = jobSkills.stream()
                    .map(s -> s.get("name").toString())
                    .toList();

            // Call LLM to map skills
            String llmResponse = callLLMForSkillMapping(jobSkillNames, onetSkillNames, onetTechNames);
            log.debug("LLM skill mapping response: {}", llmResponse);

            // Parse LLM response
            JsonNode mappingResult = objectMapper.readTree(llmResponse);
            JsonNode softSkillMappings = mappingResult.path("soft_skill_mappings");
            JsonNode technologyMappings = mappingResult.path("technology_mappings");

            // Clean up old DEMONSTRATES and RELATED_TO relationships for this experience's skills
            try (Session session = neo4jDriver.session()) {
                session.executeWrite(tx -> {
                    String cleanupQuery = """
                        MATCH (je:JobExperience {id: $experienceId})-[:REQUIRES_SKILL]->(s:Skill)
                        OPTIONAL MATCH (s)-[r:DEMONSTRATES]->()
                        DELETE r
                        WITH je
                        MATCH (je)-[:REQUIRES_SKILL]->(s:Skill)
                        OPTIONAL MATCH (s)-[r2:RELATED_TO]->()
                        DELETE r2
                        """;
                    tx.run(cleanupQuery, Values.parameters("experienceId", experienceId));
                    log.debug("Cleaned up old skill mapping relationships for experience: {}", experienceId);
                    return null;
                });

                // Create new DEMONSTRATES and RELATED_TO relationships
                session.executeWrite(tx -> {
                    // Process soft skill mappings
                    if (softSkillMappings.isArray()) {
                        softSkillMappings.forEach(mapping -> {
                            String jobSkill = mapping.path("job_skill").asText();
                            String onetSkill = mapping.path("onet_skill").asText();
                            double confidence = mapping.path("confidence").asDouble(0.8);

                            createDemonstratesRelationship(tx, experienceId, jobSkill, onetSkill, socCode, confidence);
                        });
                    }

                    // Process technology mappings
                    if (technologyMappings.isArray()) {
                        technologyMappings.forEach(mapping -> {
                            String jobSkill = mapping.path("job_skill").asText();
                            String onetTech = mapping.path("onet_technology").asText();
                            double confidence = mapping.path("confidence").asDouble(0.8);
                            String relationship = mapping.path("relationship_type").asText("similar");

                            createRelatedToRelationship(tx, experienceId, jobSkill, onetTech, socCode, confidence, relationship);
                        });
                    }

                    return null;
                });
            }

            log.info("Successfully mapped skills to O*NET for experience: {}", experienceId);

        } catch (Exception e) {
            log.error("Error mapping skills to O*NET", e);
        }
    }

    /**
     * Call LLM to map job skills to O*NET skills and technologies
     */
    private String callLLMForSkillMapping(List<String> jobSkills, List<String> onetSkills, List<String> onetTechnologies) {
        String prompt = String.format("""
            You are an expert at mapping job technical skills to O*NET soft/cognitive skills and technology skills.

            Job Skills (from job description):
            %s

            O*NET Soft/Cognitive Skills (available for this occupation):
            %s

            O*NET Technology Skills (available for this occupation):
            %s

            For each job skill, determine:
            1. Which O*NET soft skills it demonstrates (e.g., "Java" demonstrates "Programming", "Critical Thinking")
            2. Which O*NET technologies it's related to (e.g., "Spring Boot" is related to "Java")

            Relationship types for technologies:
            - "similar": Nearly the same tool (e.g., PostgreSQL ~ Oracle Database)
            - "subset": Skill is part of a larger category (e.g., Spring Boot is subset of Java)
            - "uses": Skill uses the technology (e.g., Kubernetes uses Docker)
            - "alternative": Different tools for same purpose (e.g., Jenkins ~ GitHub Actions)

            Return JSON:
            {
              "soft_skill_mappings": [
                {
                  "job_skill": "Java",
                  "onet_skill": "Programming",
                  "confidence": 0.95
                }
              ],
              "technology_mappings": [
                {
                  "job_skill": "Spring Boot",
                  "onet_technology": "Java",
                  "confidence": 0.90,
                  "relationship_type": "subset"
                }
              ]
            }

            Only map to O*NET skills/technologies that exist in the provided lists.
            Confidence should be 0.0-1.0 (higher = stronger relationship).
            """,
                String.join(", ", jobSkills),
                String.join(", ", onetSkills),
                String.join(", ", onetTechnologies)
        );

        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
        ));
        request.put("temperature", 0.3);
        request.put("response_format", Map.of("type", "json_object"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/chat/completions",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
        );

        try {
            JsonNode responseJson = objectMapper.readTree(response.getBody());
            return responseJson.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("Error parsing LLM response", e);
            return "{}";
        }
    }

    /**
     * Create DEMONSTRATES relationship: Skill -> ONetSkill
     */
    private void createDemonstratesRelationship(TransactionContext tx, String experienceId, String jobSkill,
                                                String onetSkill, String socCode, double confidence) {
        String skillId = "skill-" + jobSkill.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        String onetSkillId = "onet-skill-" + onetSkill.toLowerCase().replaceAll("[^a-z0-9]+", "-");

        String query = """
            MATCH (exp:JobExperience {id: $experienceId})-[:REQUIRES_SKILL]->(s:Skill {id: $skillId})
            MATCH (os:ONetSkill {id: $onetSkillId})
            MERGE (s)-[r:DEMONSTRATES]->(os)
            SET r.confidence = $confidence,
                r.mapped_by = 'LLM',
                r.mapped_at = datetime()
            RETURN s, r, os
            """;

        tx.run(query, Values.parameters(
                "experienceId", experienceId,
                "skillId", skillId,
                "onetSkillId", onetSkillId,
                "confidence", confidence
        ));

        log.debug("Created DEMONSTRATES: {} -> {} (confidence: {})", jobSkill, onetSkill, confidence);
    }

    /**
     * Create RELATED_TO relationship: Skill -> ONetTechnology
     */
    private void createRelatedToRelationship(TransactionContext tx, String experienceId, String jobSkill,
                                             String onetTech, String socCode, double confidence, String relationshipType) {
        String skillId = "skill-" + jobSkill.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        String onetTechId = "onet-tech-" + onetTech.toLowerCase().replaceAll("[^a-z0-9]+", "-");

        String query = """
            MATCH (exp:JobExperience {id: $experienceId})-[:REQUIRES_SKILL]->(s:Skill {id: $skillId})
            MATCH (ot:ONetTechnology {id: $onetTechId})
            MERGE (s)-[r:RELATED_TO]->(ot)
            SET r.confidence = $confidence,
                r.relationship = $relationship,
                r.mapped_by = 'LLM',
                r.mapped_at = datetime()
            RETURN s, r, ot
            """;

        tx.run(query, Values.parameters(
                "experienceId", experienceId,
                "skillId", skillId,
                "onetTechId", onetTechId,
                "confidence", confidence,
                "relationship", relationshipType
        ));

        log.debug("Created RELATED_TO: {} -> {} ({}, confidence: {})", jobSkill, onetTech, relationshipType, confidence);
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
     * Delete all Neo4j data for a resume
     * Called when user deletes a resume from the UI
     * Removes all JobExperience nodes, DescriptionLine nodes, and their relationships for this resume
     */
    public void deleteResumeFromGraph(String resumeId) {
        log.info("Deleting all graph data for resume: {}", resumeId);

        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                // Delete DescriptionLine nodes connected to JobExperience nodes for this resume
                String deleteDescriptionLinesQuery = """
                    MATCH (je:JobExperience {resume_id: $resumeId})-[r:HAS_DESCRIPTION_LINE]->(dl:DescriptionLine)
                    DETACH DELETE dl
                    """;
                tx.run(deleteDescriptionLinesQuery, Values.parameters("resumeId", resumeId));
                log.debug("Deleted DescriptionLine nodes for resume: {}", resumeId);

                // Delete JobExperience nodes and their relationships
                String deleteJobExperienceQuery = """
                    MATCH (je:JobExperience {resume_id: $resumeId})
                    DETACH DELETE je
                    """;
                tx.run(deleteJobExperienceQuery, Values.parameters("resumeId", resumeId));
                log.info("Deleted all JobExperience and DescriptionLine nodes for resume: {}", resumeId);

                return null;
            });
        } catch (Exception e) {
            log.error("Error deleting graph data for resume {}: {}", resumeId, e.getMessage());
            // Don't throw - allow the resume deletion to continue even if graph cleanup fails
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

                // Count skills
                var skillCount = tx.run("MATCH (s:Skill) RETURN count(s) AS count")
                        .single().get("count").asLong();
                stats.put("skills", skillCount);

                // Count relationships
                var relCount = tx.run("MATCH ()-[r:MAPS_TO]->() RETURN count(r) AS count")
                        .single().get("count").asLong();
                stats.put("mappings", relCount);

                var skillRelCount = tx.run("MATCH ()-[r:REQUIRES_SKILL]->() RETURN count(r) AS count")
                        .single().get("count").asLong();
                stats.put("skillRelationships", skillRelCount);

                return stats;
            });
        } catch (Exception e) {
            log.error("Error getting graph stats", e);
            return Map.of("error", e.getMessage());
        }
    }

    // ==================== SKILL EXTRACTION AND STORAGE ====================

    /**
     * Extract skills from job description using LLM
     */
    private List<Map<String, Object>> extractSkillsWithLLM(String jobTitle, String description) {
        try {
            String prompt = String.format("""
                Extract all technical and soft skills from this job description.

                Job Title: %s
                Description:
                %s

                For each skill:
                1. Normalize the name (e.g., "React.js" → "React", "Java 8" → "Java")
                2. Categorize (Programming Language, Framework, Cloud Platform, Database, DevOps Tool, Soft Skill, etc.)
                3. Estimate proficiency level (0-100) based on context
                4. Mark if it's a core/primary skill for this role

                Return ONLY valid JSON array (no markdown, no code blocks):
                [
                  {
                    "name": "Java",
                    "category": "Programming Language",
                    "subcategory": "Backend",
                    "proficiencyLevel": 90,
                    "isTechnical": true,
                    "isPrimary": true,
                    "mentionedCount": 3
                  }
                ]

                Important:
                - Normalize skill names
                - Include both explicit (mentioned) and implicit (inferred) skills
                - Limit to top 15-20 most relevant skills
                """, jobTitle, description);

            String response = callLLM(prompt);

            // Clean response (remove markdown code blocks if present)
            String cleaned = response
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

            // Parse JSON array
            JsonNode array = objectMapper.readTree(cleaned);
            List<Map<String, Object>> skills = new ArrayList<>();

            for (JsonNode node : array) {
                Map<String, Object> skill = new HashMap<>();
                skill.put("name", node.path("name").asText());
                skill.put("category", node.path("category").asText());
                skill.put("subcategory", node.path("subcategory").asText(""));
                skill.put("proficiencyLevel", node.path("proficiencyLevel").asInt(70));
                skill.put("isTechnical", node.path("isTechnical").asBoolean(true));
                skill.put("isPrimary", node.path("isPrimary").asBoolean(false));
                skill.put("mentionedCount", node.path("mentionedCount").asInt(1));
                skills.add(skill);
            }

            return skills;

        } catch (Exception e) {
            log.error("Error extracting skills with LLM", e);
            return new ArrayList<>();
        }
    }

    /**
     * Call LLM API
     */
    private String callLLM(String prompt) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(
            Map.of("role", "user", "content", prompt)
        ));
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", 2000);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/chat/completions",
            HttpMethod.POST,
            request,
            String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("LLM API call failed: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        return root.path("choices").get(0).path("message").path("content").asText().trim();
    }

    /**
     * Create Skill nodes and REQUIRES_SKILL relationships
     */
    private void createSkillNodesAndRelationships(
            TransactionContext tx,
            String experienceId,
            List<Map<String, Object>> skills) {

        for (Map<String, Object> skill : skills) {
            String skillName = (String) skill.get("name");
            String skillId = generateSkillId(skillName);

            // Create or merge Skill node
            String createSkillQuery = """
                MERGE (s:Skill {id: $skillId})
                SET s.name = $name,
                    s.category = $category,
                    s.subcategory = $subcategory,
                    s.is_technical = $isTechnical,
                    s.last_updated = datetime()
                RETURN s
                """;

            tx.run(createSkillQuery, Values.parameters(
                "skillId", skillId,
                "name", skill.get("name"),
                "category", skill.get("category"),
                "subcategory", skill.get("subcategory"),
                "isTechnical", skill.get("isTechnical")
            ));

            // Create REQUIRES_SKILL relationship
            String createRelQuery = """
                MATCH (je:JobExperience {id: $experienceId})
                MATCH (s:Skill {id: $skillId})
                MERGE (je)-[r:REQUIRES_SKILL]->(s)
                SET r.proficiency_level = $proficiencyLevel,
                    r.is_primary = $isPrimary,
                    r.mentioned_count = $mentionedCount,
                    r.created_at = datetime()
                RETURN r
                """;

            tx.run(createRelQuery, Values.parameters(
                "experienceId", experienceId,
                "skillId", skillId,
                "proficiencyLevel", skill.get("proficiencyLevel"),
                "isPrimary", skill.get("isPrimary"),
                "mentionedCount", skill.get("mentionedCount")
            ));

            log.debug("Created Skill node and relationship: {} for experience {}", skillName, experienceId);
        }
    }

    /**
     * Generate skill ID from normalized name
     */
    private String generateSkillId(String name) {
        return "skill-" + name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }

    // ==================== DESCRIPTION LINE PARSING ====================

    /**
     * Parse job description into individual lines and store in Neo4j graph
     * Creates DescriptionLine nodes and HAS_DESCRIPTION_LINE relationships
     */
    public List<String> parseDescriptionIntoLines(String experienceId, String description) {
        if (description == null || description.trim().isEmpty()) {
            log.warn("Empty description for experience {}", experienceId);
            return new ArrayList<>();
        }

        // Handle both actual newlines and escaped newlines (\n as string)
        // First, convert escaped newlines to actual newlines if needed
        String normalizedDescription = description.replace("\\n", "\n");

        // Split description by newlines (handles \r\n, \n, and \r)
        String[] lines = normalizedDescription.split("\\r?\\n");
        List<String> parsedLines = new ArrayList<>();

        log.debug("Attempting to parse {} raw lines from description", lines.length);

        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                // Clean up existing description lines first
                String cleanupQuery = """
                    MATCH (je:JobExperience {id: $experienceId})-[r:HAS_DESCRIPTION_LINE]->(dl:DescriptionLine)
                    DETACH DELETE dl
                    """;
                tx.run(cleanupQuery, Values.parameters("experienceId", experienceId));

                // Create new description line nodes
                int sequence = 1;
                for (String line : lines) {
                    String trimmedLine = line.trim();

                    // Skip empty lines or lines with only bullets/dashes
                    if (trimmedLine.isEmpty() || trimmedLine.matches("^[-•*]+$")) {
                        continue;
                    }

                    // Remove common bullet point characters at the start
                    trimmedLine = trimmedLine.replaceFirst("^[-•*\\s]+", "").trim();

                    if (!trimmedLine.isEmpty()) {
                        createDescriptionLineNode(tx, experienceId, sequence, trimmedLine);
                        parsedLines.add(trimmedLine);
                        log.debug("Created DescriptionLine {} with text: {}",
                                  sequence, trimmedLine.substring(0, Math.min(50, trimmedLine.length())) + "...");
                        sequence++;
                    }
                }

                log.info("Parsed {} description lines for experience {}", parsedLines.size(), experienceId);
                return null;
            });
        } catch (Exception e) {
            log.error("Error parsing description into lines for experience {}", experienceId, e);
        }

        return parsedLines;
    }

    /**
     * Create DescriptionLine node and link to JobExperience
     */
    private void createDescriptionLineNode(TransactionContext tx, String experienceId, int sequence, String text) {
        String lineId = "desc-line-" + experienceId + "-" + sequence;

        String query = """
            MERGE (dl:DescriptionLine {id: $lineId})
            SET dl.experience_id = $experienceId,
                dl.sequence = $sequence,
                dl.text = $text,
                dl.parsed_at = datetime()
            WITH dl
            MATCH (je:JobExperience {id: $experienceId})
            MERGE (je)-[r:HAS_DESCRIPTION_LINE {sequence: $sequence}]->(dl)
            RETURN dl
            """;

        tx.run(query, Values.parameters(
            "lineId", lineId,
            "experienceId", experienceId,
            "sequence", sequence,
            "text", text
        ));

        log.debug("Created DescriptionLine node: {} (sequence {})", lineId, sequence);
    }

    /**
     * Store description line mappings to O*NET activities and tasks
     * Called after LLM analyzes description lines
     */
    public void storeDescriptionLineMappings(
            String experienceId,
            String socCode,
            List<Map<String, Object>> lineMappings) {

        log.info("Storing {} description line mappings for experience {}", lineMappings.size(), experienceId);

        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                for (Map<String, Object> lineMapping : lineMappings) {
                    int sequence = (Integer) lineMapping.get("sequence");
                    String lineId = "desc-line-" + experienceId + "-" + sequence;

                    // Update DescriptionLine with impact data
                    updateDescriptionLineMetadata(tx, lineId, lineMapping);

                    // Create DEMONSTRATES_ACTIVITY relationships
                    List<Map<String, Object>> activities =
                        (List<Map<String, Object>>) lineMapping.get("activities");
                    if (activities != null) {
                        for (Map<String, Object> activity : activities) {
                            createDemonstratesActivityRelationship(tx, lineId, activity);
                        }
                    }

                    // Create DEMONSTRATES_TASK relationships
                    List<Map<String, Object>> tasks =
                        (List<Map<String, Object>>) lineMapping.get("tasks");
                    if (tasks != null) {
                        for (Map<String, Object> task : tasks) {
                            createDemonstratesTaskRelationship(tx, lineId, task);
                        }
                    }
                }
                return null;
            });

            log.info("Successfully stored description line mappings for experience {}", experienceId);

        } catch (Exception e) {
            log.error("Error storing description line mappings", e);
        }
    }

    /**
     * Update DescriptionLine node with impact metadata
     */
    private void updateDescriptionLineMetadata(TransactionContext tx, String lineId, Map<String, Object> metadata) {
        String query = """
            MATCH (dl:DescriptionLine {id: $lineId})
            SET dl.impact_metrics = $impactMetrics,
                dl.has_quantifiable_impact = $hasQuantifiableImpact,
                dl.impact_level = $impactLevel,
                dl.scope = $scope
            RETURN dl
            """;

        tx.run(query, Values.parameters(
            "lineId", lineId,
            "impactMetrics", metadata.get("impactMetrics"),
            "hasQuantifiableImpact", metadata.get("hasQuantifiableImpact"),
            "impactLevel", metadata.get("impactLevel"),
            "scope", metadata.get("scope")
        ));
    }

    /**
     * Create DEMONSTRATES_ACTIVITY relationship: DescriptionLine -> ONetActivity
     */
    private void createDemonstratesActivityRelationship(
            TransactionContext tx,
            String lineId,
            Map<String, Object> activity) {

        String activityId = (String) activity.get("activityId");
        Double confidence = (Double) activity.get("confidence");
        String reasoning = (String) activity.get("reasoning");

        String query = """
            MATCH (dl:DescriptionLine {id: $lineId})
            MATCH (oa:ONetActivity {id: $activityId})
            MERGE (dl)-[r:DEMONSTRATES_ACTIVITY]->(oa)
            SET r.confidence = $confidence,
                r.reasoning = $reasoning,
                r.mapped_at = datetime()
            RETURN dl, r, oa
            """;

        tx.run(query, Values.parameters(
            "lineId", lineId,
            "activityId", activityId,
            "confidence", confidence,
            "reasoning", reasoning
        ));

        log.debug("Created DEMONSTRATES_ACTIVITY: {} -> {} (confidence: {})",
            lineId, activityId, confidence);
    }

    /**
     * Create DEMONSTRATES_TASK relationship: DescriptionLine -> ONetTask
     */
    private void createDemonstratesTaskRelationship(
            TransactionContext tx,
            String lineId,
            Map<String, Object> task) {

        String taskId = (String) task.get("taskId");
        Double confidence = (Double) task.get("confidence");
        String reasoning = (String) task.get("reasoning");

        String query = """
            MATCH (dl:DescriptionLine {id: $lineId})
            MATCH (ot:ONetTask {id: $taskId})
            MERGE (dl)-[r:DEMONSTRATES_TASK]->(ot)
            SET r.confidence = $confidence,
                r.reasoning = $reasoning,
                r.mapped_at = datetime()
            RETURN dl, r, ot
            """;

        tx.run(query, Values.parameters(
            "lineId", lineId,
            "taskId", taskId,
            "confidence", confidence,
            "reasoning", reasoning
        ));

        log.debug("Created DEMONSTRATES_TASK: {} -> {} (confidence: {})",
            lineId, taskId, confidence);
    }

    /**
     * Fetch O*NET activities and tasks for description line mapping
     * Returns formatted lists for LLM prompt
     */
    public Map<String, List<String>> fetchONetActivitiesAndTasks(String socCode) {
        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                Map<String, List<String>> result = new HashMap<>();

                // Fetch O*NET activities
                String activityQuery = """
                    MATCH (o:Occupation {soc_code: $socCode})-[:REQUIRES_ACTIVITY]->(a:ONetActivity)
                    RETURN a.id AS id, a.name AS name
                    ORDER BY a.importance DESC
                    """;
                Result activityResult = tx.run(activityQuery, Values.parameters("socCode", socCode));
                List<String> activities = activityResult.list(record ->
                    String.format("%s|%s", record.get("id").asString(), record.get("name").asString())
                );

                // Fetch O*NET tasks
                String taskQuery = """
                    MATCH (o:Occupation {soc_code: $socCode})-[:REQUIRES_TASK]->(t:ONetTask)
                    RETURN t.id AS id, t.name AS name
                    ORDER BY t.importance DESC
                    """;
                Result taskResult = tx.run(taskQuery, Values.parameters("socCode", socCode));
                List<String> tasks = taskResult.list(record ->
                    String.format("%s|%s", record.get("id").asString(), record.get("name").asString())
                );

                result.put("activities", activities);
                result.put("tasks", tasks);

                log.info("Fetched {} activities and {} tasks for SOC {}",
                    activities.size(), tasks.size(), socCode);

                return result;
            });
        } catch (Exception e) {
            log.error("Error fetching O*NET activities/tasks for SOC {}", socCode, e);
            return Map.of("activities", new ArrayList<>(), "tasks", new ArrayList<>());
        }
    }

    /**
     * Store Skill -> ONetTask relationships from LLM analysis
     * Maps which skills are required for specific O*NET tasks
     * Called after description line mapping with skill-task mappings from LLM
     */
    public void storeSkillToTaskMappings(
            String experienceId,
            List<com.resumebuddy.model.dto.DescriptionLineMappingDto.SkillToTaskMappingDto> mappings) {

        log.info("Storing {} skill-to-task mappings for experience {}", mappings.size(), experienceId);

        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                for (com.resumebuddy.model.dto.DescriptionLineMappingDto.SkillToTaskMappingDto mapping : mappings) {
                    String skillId = generateSkillId(mapping.getSkillName());
                    String taskId = mapping.getTaskId();

                    String query = """
                        MATCH (exp:JobExperience {id: $experienceId})-[:REQUIRES_SKILL]->(s:Skill {id: $skillId})
                        MATCH (t:ONetTask {id: $taskId})
                        MERGE (s)-[r:RELATES_TO_TASK]->(t)
                        SET r.confidence = $confidence,
                            r.reasoning = $reasoning,
                            r.mapped_by = 'LLM',
                            r.mapped_at = datetime()
                        RETURN s, r, t
                        """;

                    tx.run(query, Values.parameters(
                        "experienceId", experienceId,
                        "skillId", skillId,
                        "taskId", taskId,
                        "confidence", mapping.getConfidence(),
                        "reasoning", mapping.getReasoning()
                    ));

                    log.debug("Created RELATES_TO_TASK: {} -> {} (confidence: {})",
                        mapping.getSkillName(), mapping.getTaskName(), mapping.getConfidence());
                }
                return null;
            });

            log.info("Successfully stored {} skill-to-task mappings", mappings.size());

        } catch (Exception e) {
            log.error("Error storing skill-to-task mappings: {}", e.getMessage());
            // Don't throw - allow analysis to continue even if this fails
        }
    }

    // ==================== DEEP GRAPH ANALYSIS QUERIES (PHASE 6) ====================

    /**
     * Analyze which skills have strong demonstration evidence vs weak/none
     * Traverses: Skill -> RELATES_TO_TASK -> ONetTask <- DEMONSTRATES_TASK <- DescriptionLine
     * Returns skill evidence strength for all skills in a job experience
     */
    public List<Map<String, Object>> getSkillDemonstrationAnalysis(String experienceId) {
        log.info("Analyzing skill demonstration evidence for experience: {}", experienceId);

        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                String query = """
                    MATCH (je:JobExperience {id: $experienceId})-[req:REQUIRES_SKILL]->(s:Skill)
                    OPTIONAL MATCH (s)-[:RELATES_TO_TASK]->(t:ONetTask)
                    OPTIONAL MATCH (je)-[:HAS_DESCRIPTION_LINE]->(dl:DescriptionLine)-[:DEMONSTRATES_TASK]->(t)
                    WITH s,
                         count(DISTINCT t) AS tasksLinked,
                         count(DISTINCT dl) AS linesShowcasing,
                         collect(DISTINCT dl.text) AS exampleLines,
                         collect(DISTINCT t.id + '|' + t.name) AS taskNames,
                         avg(t.importance) AS avgTaskImportance,
                         req.is_primary AS isPrimary,
                         s.category AS category
                    RETURN s.name AS skillName,
                           category,
                           tasksLinked,
                           linesShowcasing,
                           exampleLines,
                           taskNames,
                           avgTaskImportance,
                           isPrimary,
                           CASE
                             WHEN linesShowcasing >= 2 THEN 'STRONG'
                             WHEN linesShowcasing = 1 THEN 'MODERATE'
                             WHEN tasksLinked > 0 THEN 'WEAK'
                             ELSE 'NONE'
                           END AS evidenceStrength
                    ORDER BY isPrimary DESC, linesShowcasing DESC
                    """;

                Result result = tx.run(query, Values.parameters("experienceId", experienceId));

                List<Map<String, Object>> analysis = new ArrayList<>();
                while (result.hasNext()) {
                    Record record = result.next();
                    Map<String, Object> skillAnalysis = new HashMap<>();
                    skillAnalysis.put("skillName", record.get("skillName").asString());
                    skillAnalysis.put("category", record.get("category").asString(""));
                    skillAnalysis.put("tasksLinked", record.get("tasksLinked").asInt());
                    skillAnalysis.put("linesShowcasing", record.get("linesShowcasing").asInt());
                    skillAnalysis.put("exampleLines", record.get("exampleLines").asList(org.neo4j.driver.Value::asString));
                    skillAnalysis.put("taskNames", record.get("taskNames").asList(org.neo4j.driver.Value::asString));
                    skillAnalysis.put("avgTaskImportance", record.get("avgTaskImportance").isNull() ? 0.0 : record.get("avgTaskImportance").asDouble());
                    skillAnalysis.put("isPrimary", record.get("isPrimary").asBoolean(false));
                    skillAnalysis.put("evidenceStrength", record.get("evidenceStrength").asString());
                    analysis.add(skillAnalysis);
                }

                log.info("Analyzed {} skills for demonstration evidence", analysis.size());
                return analysis;
            });
        } catch (Exception e) {
            log.error("Error analyzing skill demonstration for experience {}: {}", experienceId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Score each description line by how many skills it showcases
     * High-value lines demonstrate multiple technical skills
     * Traverses: DescriptionLine -> DEMONSTRATES_TASK -> ONetTask <- RELATES_TO_TASK <- Skill
     */
    public List<Map<String, Object>> getDescriptionLineValueScores(String experienceId) {
        log.info("Calculating description line value scores for experience: {}", experienceId);

        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                String query = """
                    MATCH (je:JobExperience {id: $experienceId})-[:HAS_DESCRIPTION_LINE]->(dl:DescriptionLine)
                    OPTIONAL MATCH (dl)-[:DEMONSTRATES_TASK]->(t:ONetTask)<-[:RELATES_TO_TASK]-(s:Skill)
                    WHERE (je)-[:REQUIRES_SKILL]->(s)
                      AND toLower(dl.text) CONTAINS toLower(s.name)
                    WITH dl,
                         collect(DISTINCT s.name) AS skillsShowcased,
                         count(DISTINCT s) AS skillCount,
                         dl.impact_level AS impactLevel,
                         dl.sequence AS sequence,
                         dl.text AS text
                    RETURN sequence,
                           text,
                           skillsShowcased,
                           skillCount,
                           impactLevel,
                           CASE
                             WHEN skillCount >= 4 THEN 'EXCELLENT'
                             WHEN skillCount >= 2 THEN 'GOOD'
                             WHEN skillCount = 1 THEN 'MODERATE'
                             ELSE 'LOW'
                           END AS valueRating
                    ORDER BY skillCount DESC, sequence ASC
                    """;

                Result result = tx.run(query, Values.parameters("experienceId", experienceId));

                List<Map<String, Object>> lineValues = new ArrayList<>();
                while (result.hasNext()) {
                    Record record = result.next();
                    Map<String, Object> lineValue = new HashMap<>();
                    lineValue.put("sequence", record.get("sequence").asInt());
                    lineValue.put("text", record.get("text").asString());
                    lineValue.put("skillsShowcased", record.get("skillsShowcased").asList(org.neo4j.driver.Value::asString));
                    lineValue.put("skillCount", record.get("skillCount").asInt());
                    lineValue.put("impactLevel", record.get("impactLevel").asString(""));
                    lineValue.put("valueRating", record.get("valueRating").asString());
                    lineValues.add(lineValue);
                }

                log.info("Calculated value scores for {} description lines", lineValues.size());
                return lineValues;
            });
        } catch (Exception e) {
            log.error("Error calculating line value scores for experience {}: {}", experienceId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Find high-importance tasks that the candidate COULD demonstrate
     * (they have related skills) but currently DON'T demonstrate
     * Returns untapped opportunities for resume improvement
     */
    public List<Map<String, Object>> getMissingSkillTaskOpportunities(String experienceId, double minImportance) {
        log.info("Finding missing skill-task opportunities for experience: {} (min importance: {})",
                experienceId, minImportance);

        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                String query = """
                    // Find candidate's skills
                    MATCH (je:JobExperience {id: $experienceId})-[:REQUIRES_SKILL]->(s:Skill)

                    // Find tasks those skills relate to
                    MATCH (s)-[:RELATES_TO_TASK]->(t:ONetTask)
                    WHERE t.importance >= $minImportance

                    // Check if those tasks are already demonstrated
                    OPTIONAL MATCH (je)-[:HAS_DESCRIPTION_LINE]->(dl:DescriptionLine)-[:DEMONSTRATES_TASK]->(t)

                    // Only return tasks NOT yet demonstrated
                    WITH s, t, dl
                    WHERE dl IS NULL

                    // Get the occupation context
                    MATCH (je)-[:MAPS_TO]->(o:Occupation)-[:REQUIRES_TASK]->(t)

                    RETURN DISTINCT
                           s.name AS skillName,
                           t.id AS taskId,
                           t.name AS taskName,
                           t.importance AS importance,
                           t.category AS taskCategory,
                           o.title AS occupationTitle
                    ORDER BY t.importance DESC
                    LIMIT 10
                    """;

                Result result = tx.run(query, Values.parameters(
                        "experienceId", experienceId,
                        "minImportance", minImportance
                ));

                List<Map<String, Object>> opportunities = new ArrayList<>();
                while (result.hasNext()) {
                    Record record = result.next();
                    Map<String, Object> opportunity = new HashMap<>();
                    opportunity.put("skillName", record.get("skillName").asString());
                    opportunity.put("taskId", record.get("taskId").asString());
                    opportunity.put("taskName", record.get("taskName").asString());
                    opportunity.put("importance", record.get("importance").asDouble());
                    opportunity.put("taskCategory", record.get("taskCategory").asString());
                    opportunity.put("occupationTitle", record.get("occupationTitle").asString());
                    opportunities.add(opportunity);
                }

                log.info("Found {} missing skill-task opportunities", opportunities.size());
                return opportunities;
            });
        } catch (Exception e) {
            log.error("Error finding missing opportunities for experience {}: {}", experienceId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Find high-importance O*NET tasks/activities NOT demonstrated at all
     * These are gaps regardless of candidate's skills
     * Returns top N missing items for each type
     */
    public Map<String, List<Map<String, Object>>> getMissingTasksAndActivities(
            String experienceId,
            int topN,
            double minImportance) {

        log.info("Finding missing tasks/activities for experience: {} (top {}, min importance: {})",
                experienceId, topN, minImportance);

        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                Map<String, List<Map<String, Object>>> result = new HashMap<>();

                // Query for missing tasks
                String taskQuery = """
                    MATCH (je:JobExperience {id: $experienceId})-[:MAPS_TO]->(o:Occupation)-[:REQUIRES_TASK]->(t:ONetTask)
                    WHERE t.importance >= $minImportance
                      AND NOT EXISTS {
                        MATCH (je)-[:HAS_DESCRIPTION_LINE]->(dl:DescriptionLine)-[:DEMONSTRATES_TASK]->(t)
                      }
                    RETURN DISTINCT t.id AS taskId,
                                    t.name AS taskName,
                                    t.importance AS importance,
                                    t.category AS category
                    ORDER BY t.importance DESC
                    LIMIT $topN
                    """;

                Result taskResult = tx.run(taskQuery, Values.parameters(
                        "experienceId", experienceId,
                        "minImportance", minImportance,
                        "topN", topN
                ));

                List<Map<String, Object>> missingTasks = new ArrayList<>();
                while (taskResult.hasNext()) {
                    Record record = taskResult.next();
                    Map<String, Object> task = new HashMap<>();
                    task.put("taskId", record.get("taskId").asString());
                    task.put("taskName", record.get("taskName").asString());
                    task.put("importance", record.get("importance").asDouble());
                    task.put("category", record.get("category").asString());
                    missingTasks.add(task);
                }

                // Query for missing activities
                String activityQuery = """
                    MATCH (je:JobExperience {id: $experienceId})-[:MAPS_TO]->(o:Occupation)-[:REQUIRES_ACTIVITY]->(a:ONetActivity)
                    WHERE a.importance >= $minImportance
                      AND a.importance < 95
                      AND NOT EXISTS {
                        MATCH (je)-[:HAS_DESCRIPTION_LINE]->(dl:DescriptionLine)-[:DEMONSTRATES_ACTIVITY]->(a)
                      }
                    RETURN DISTINCT a.id AS activityId,
                                    a.name AS activityName,
                                    a.importance AS importance
                    ORDER BY a.importance DESC
                    LIMIT $topN
                    """;

                Result activityResult = tx.run(activityQuery, Values.parameters(
                        "experienceId", experienceId,
                        "minImportance", minImportance,
                        "topN", topN
                ));

                List<Map<String, Object>> missingActivities = new ArrayList<>();
                while (activityResult.hasNext()) {
                    Record record = activityResult.next();
                    Map<String, Object> activity = new HashMap<>();
                    activity.put("activityId", record.get("activityId").asString());
                    activity.put("activityName", record.get("activityName").asString());
                    activity.put("importance", record.get("importance").asDouble());
                    missingActivities.add(activity);
                }

                result.put("tasks", missingTasks);
                result.put("activities", missingActivities);

                log.info("Found {} missing tasks and {} missing activities",
                        missingTasks.size(), missingActivities.size());

                return result;
            });
        } catch (Exception e) {
            log.error("Error finding missing tasks/activities for experience {}: {}", experienceId, e.getMessage());
            return Map.of("tasks", new ArrayList<>(), "activities", new ArrayList<>());
        }
    }

    /**
     * Find missing skills by category - skills that relate to occupation's required tasks
     * but the candidate doesn't currently have
     * Groups by category and limits total results to 40 skills
     */
    public List<Map<String, Object>> getMissingSkillsByCategory(String experienceId, int limitPerCategory) {
        log.info("Finding missing skills by category for experience: {} (limit {} per category)",
                experienceId, limitPerCategory);

        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                String query = """
                    // Get occupation's required tasks
                    MATCH (je:JobExperience {id: $experienceId})-[:MAPS_TO]->(o:Occupation)
                    MATCH (o)-[:REQUIRES_TASK]->(task:ONetTask)

                    // Find skills that relate to those tasks
                    MATCH (skill:Skill)-[:RELATES_TO_TASK]->(task)

                    // Filter out skills the candidate already has AND filter out generic domain skills
                    WHERE NOT EXISTS {
                      MATCH (je)-[:REQUIRES_SKILL]->(candidateSkill:Skill)
                      WHERE candidateSkill.name = skill.name
                    }
                    AND NOT skill.name IN ['Domain Expertise', 'Domain Knowledge']

                    // Collect task names and calculate metrics
                    WITH skill.name AS skillName,
                         skill.category AS category,
                         collect(DISTINCT task.name)[0..3] AS taskNames,
                         count(DISTINCT task) AS requiredByTasks,
                         avg(task.importance) AS avgImportance

                    // Group by category and limit
                    WITH category,
                         collect({
                           skillName: skillName,
                           requiredByTasks: requiredByTasks,
                           avgImportance: avgImportance,
                           taskNames: taskNames
                         }) AS skillsInCategory

                    UNWIND skillsInCategory[0..$limitPerCategory] AS skill

                    RETURN category,
                           skill.skillName AS skillName,
                           skill.requiredByTasks AS requiredByTasks,
                           skill.avgImportance AS avgImportance,
                           skill.taskNames AS taskNames
                    ORDER BY category, skill.avgImportance DESC, skill.requiredByTasks DESC
                    LIMIT 40
                    """;

                Result result = tx.run(query, Values.parameters(
                        "experienceId", experienceId,
                        "limitPerCategory", limitPerCategory
                ));

                List<Map<String, Object>> missingSkills = new ArrayList<>();
                while (result.hasNext()) {
                    Record record = result.next();
                    Map<String, Object> skillData = new HashMap<>();
                    skillData.put("skillName", record.get("skillName").asString());
                    skillData.put("category", record.get("category").asString("Uncategorized"));
                    skillData.put("requiredByTasks", record.get("requiredByTasks").asInt());
                    skillData.put("avgImportance", record.get("avgImportance").asDouble());
                    skillData.put("taskNames", record.get("taskNames").asList(v -> v.asString()));
                    missingSkills.add(skillData);
                }

                log.info("Found {} missing skills (max 40 total, filtered out Domain Expertise/Knowledge)",
                        missingSkills.size());
                return missingSkills;
            });
        } catch (Exception e) {
            log.error("Error finding missing skills for experience {}: {}", experienceId, e.getMessage());
            return new ArrayList<>();
        }
    }

    // ==================== TASK-LEVEL SKILL POPULARITY ANALYSIS (PHASE 6.5) ====================

    /**
     * Get task-level skill popularity analysis for a specific O*NET task
     * Shows all skills that can accomplish this task and how many people use each skill
     * Also indicates whether the current user has each skill
     *
     * @param experienceId Current user's job experience ID
     * @param taskId O*NET task ID
     * @return List of skills with popularity counts and user ownership flag
     */
    public List<Map<String, Object>> getTaskSkillPopularityAnalysis(String experienceId, String taskId) {
        log.info("Analyzing skill popularity for task {} in experience {}", taskId, experienceId);

        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                String query = """
                    // Get the task
                    MATCH (task:ONetTask {id: $taskId})

                    // Find all skills that relate to this task
                    MATCH (skill:Skill)-[:RELATES_TO_TASK]->(task)

                    // Count how many JobExperiences use each skill
                    MATCH (je:JobExperience)-[:REQUIRES_SKILL]->(skill)
                    WITH task, skill,
                         count(DISTINCT je) AS jobCount,
                         count(DISTINCT je.resume_id) AS resumeCount

                    // Check if current user has this skill
                    OPTIONAL MATCH (userJe:JobExperience {id: $experienceId})-[:REQUIRES_SKILL]->(skill)

                    RETURN skill.name AS skillName,
                           skill.category AS skillCategory,
                           resumeCount AS peopleWithSkill,
                           (userJe IS NOT NULL) AS userHasSkill
                    ORDER BY resumeCount DESC
                    """;

                Result result = tx.run(query, Values.parameters(
                        "experienceId", experienceId,
                        "taskId", taskId
                ));

                List<Map<String, Object>> skillPopularity = new ArrayList<>();
                while (result.hasNext()) {
                    Record record = result.next();
                    Map<String, Object> skillData = new HashMap<>();
                    skillData.put("skillName", record.get("skillName").asString());
                    skillData.put("skillCategory", record.get("skillCategory").asString(""));
                    skillData.put("peopleWithSkill", record.get("peopleWithSkill").asInt());
                    skillData.put("userHasSkill", record.get("userHasSkill").asBoolean());
                    skillPopularity.add(skillData);
                }

                log.info("Found {} skills for task {}, {} professionals total",
                        skillPopularity.size(), taskId,
                        skillPopularity.stream().mapToInt(s -> (Integer) s.get("peopleWithSkill")).sum());

                return skillPopularity;
            });
        } catch (Exception e) {
            log.error("Error analyzing skill popularity for task {} in experience {}: {}",
                    taskId, experienceId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get skill co-occurrence analysis for a specific task and user skill
     * Shows which OTHER skills are commonly paired with the user's skill
     * Example: "72% of Spring Boot users also have Docker"
     *
     * @param experienceId Current user's job experience ID
     * @param taskId O*NET task ID
     * @param userSkill The skill the user has for this task
     * @return List of co-occurring skills with percentages and counts
     */
    public List<Map<String, Object>> getTaskSkillCooccurrence(String experienceId, String taskId, String userSkill) {
        log.info("Analyzing skill co-occurrence for {} on task {} in experience {}",
                userSkill, taskId, experienceId);

        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                String query = """
                    // Find JobExperiences that use this skill for this task
                    MATCH (skill:Skill {name: $userSkill})-[:RELATES_TO_TASK]->(task:ONetTask {id: $taskId})
                    MATCH (je:JobExperience)-[:REQUIRES_SKILL]->(skill)

                    // Find other skills these JobExperiences have
                    MATCH (je)-[:REQUIRES_SKILL]->(coSkill:Skill)
                    WHERE coSkill <> skill

                    // Count co-occurrence
                    WITH coSkill,
                         count(DISTINCT je) AS coCount,
                         count(DISTINCT je.resume_id) AS resumeCount

                    // Calculate percentage
                    MATCH (allJe:JobExperience)-[:REQUIRES_SKILL]->(skill:Skill {name: $userSkill})
                    WITH coSkill,
                         resumeCount,
                         count(DISTINCT allJe.resume_id) AS totalWithUserSkill,
                         (resumeCount * 100.0 / count(DISTINCT allJe.resume_id)) AS cooccurrencePercentage

                    // Check if current user has this co-occurring skill
                    OPTIONAL MATCH (userJe:JobExperience {id: $experienceId})-[:REQUIRES_SKILL]->(coSkill)

                    RETURN coSkill.name AS skillName,
                           coSkill.category AS category,
                           resumeCount AS peopleCount,
                           cooccurrencePercentage AS percentage,
                           (userJe IS NOT NULL) AS userHasSkill
                    ORDER BY cooccurrencePercentage DESC
                    LIMIT 10
                    """;

                Result result = tx.run(query, Values.parameters(
                        "experienceId", experienceId,
                        "taskId", taskId,
                        "userSkill", userSkill
                ));

                List<Map<String, Object>> cooccurrence = new ArrayList<>();
                while (result.hasNext()) {
                    Record record = result.next();
                    Map<String, Object> coSkillData = new HashMap<>();
                    coSkillData.put("skillName", record.get("skillName").asString());
                    coSkillData.put("category", record.get("category").asString(""));
                    coSkillData.put("peopleCount", record.get("peopleCount").asInt());
                    coSkillData.put("percentage", record.get("percentage").asDouble());
                    coSkillData.put("userHasSkill", record.get("userHasSkill").asBoolean());
                    cooccurrence.add(coSkillData);
                }

                log.info("Found {} co-occurring skills for {} on task {}",
                        cooccurrence.size(), userSkill, taskId);

                return cooccurrence;
            });
        } catch (Exception e) {
            log.error("Error analyzing skill co-occurrence for {} on task {} in experience {}: {}",
                    userSkill, taskId, experienceId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Find skills for a specific task that the user doesn't have (but others do)
     * Shows competitive gaps - skills commonly used for this task that user is missing
     *
     * @param experienceId Current user's job experience ID
     * @param taskId O*NET task ID
     * @return List of missing skills with popularity counts
     */
    public List<Map<String, Object>> getTaskMissingSkillsAnalysis(String experienceId, String taskId) {
        log.info("Finding missing skills for task {} in experience {}", taskId, experienceId);

        try (Session session = neo4jDriver.session()) {
            return session.executeRead(tx -> {
                String query = """
                    // Get the task and related skills
                    MATCH (task:ONetTask {id: $taskId})
                    MATCH (skill:Skill)-[:RELATES_TO_TASK]->(task)

                    // Count popularity
                    MATCH (je:JobExperience)-[:REQUIRES_SKILL]->(skill)
                    WITH task, skill, count(DISTINCT je.resume_id) AS resumeCount

                    // Check if user has this skill
                    MATCH (userJe:JobExperience {id: $experienceId})
                    WHERE NOT EXISTS {
                      MATCH (userJe)-[:REQUIRES_SKILL]->(userSkill:Skill)
                      WHERE userSkill.name = skill.name
                    }

                    RETURN skill.name AS skillName,
                           skill.category AS category,
                           resumeCount AS peopleWithSkill,
                           task.importance AS taskImportance
                    ORDER BY resumeCount DESC, taskImportance DESC
                    LIMIT 5
                    """;

                Result result = tx.run(query, Values.parameters(
                        "experienceId", experienceId,
                        "taskId", taskId
                ));

                List<Map<String, Object>> missingSkills = new ArrayList<>();
                while (result.hasNext()) {
                    Record record = result.next();
                    Map<String, Object> missingSkillData = new HashMap<>();
                    missingSkillData.put("skillName", record.get("skillName").asString());
                    missingSkillData.put("category", record.get("category").asString(""));
                    missingSkillData.put("peopleWithSkill", record.get("peopleWithSkill").asInt());
                    missingSkillData.put("taskImportance", record.get("taskImportance").asDouble());
                    missingSkills.add(missingSkillData);
                }

                log.info("Found {} missing skills for task {} in experience {}",
                        missingSkills.size(), taskId, experienceId);

                return missingSkills;
            });
        } catch (Exception e) {
            log.error("Error finding missing skills for task {} in experience {}: {}",
                    taskId, experienceId, e.getMessage());
            return new ArrayList<>();
        }
    }
}
