package com.resumebuddy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.model.ResumeAnalysisExperience;
import com.resumebuddy.model.dto.NormalizedJobDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Values;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
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
     * Store job experience and occupation mapping in Neo4j graph
     * Enhanced to also extract and store skills
     */
    // Instance variable to store skills for later mapping
    private List<Map<String, Object>> lastExtractedSkills = new ArrayList<>();

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
     * Deletes all relationships for this JobExperience node to avoid duplicates
     */
    private void cleanupExistingJobData(TransactionContext tx, String experienceId) {
        String query = """
            MATCH (je:JobExperience {id: $experienceId})
            OPTIONAL MATCH (je)-[r]-()
            DELETE r
            """;

        tx.run(query, Values.parameters("experienceId", experienceId));
        log.debug("Cleaned up existing relationships for experience: {}", experienceId);
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
                o.normalized_title = $normalizedTitle,
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
                "normalizedTitle", normalized.getNormalizedTitle(),
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

            log.info("Created O*NET skill/technology/task nodes for occupation: {}", socCode);

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
}
