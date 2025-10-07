package com.resumebuddy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.model.dto.ONetOccupationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * O*NET Web Services Integration
 * Connects to O*NET API using Basic authentication
 * API Documentation: https://services.onetcenter.org/reference/
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ONetIntegrationService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.onet.base-url}")
    private String baseUrl;

    @Value("${app.onet.username}")
    private String username;

    @Value("${app.onet.password}")
    private String password;

    /**
     * Get comprehensive occupation details for a SOC code
     * Combines data from multiple O*NET endpoints
     */
    public String getOccupationDetails(String socCode) {
        log.info("Fetching O*NET data for SOC code: {}", socCode);

        try {
            // Fetch occupation details
            JsonNode occupation = callONetApi("/online/occupations/" + socCode);
            log.debug("Occupation response keys: {}", occupation.fieldNames());

            // Fetch detailed work activities (may not be available for all occupations)
            JsonNode activities = null;
            try {
                activities = callONetApi("/online/occupations/" + socCode + "/details/work_activities");
                log.debug("Activities response keys: {}", activities.fieldNames());
            } catch (Exception e) {
                log.warn("Work activities not available for SOC {}: {}", socCode, e.getMessage());
                activities = objectMapper.createObjectNode(); // Empty node
            }

            // Fetch skills
            JsonNode skills = null;
            try {
                skills = callONetApi("/online/occupations/" + socCode + "/details/skills");
                log.debug("Skills response keys: {}", skills.fieldNames());
            } catch (Exception e) {
                log.warn("Skills not available for SOC {}: {}", socCode, e.getMessage());
                skills = objectMapper.createObjectNode();
            }

            // Fetch knowledge
            JsonNode knowledge = null;
            try {
                knowledge = callONetApi("/online/occupations/" + socCode + "/details/knowledge");
                log.debug("Knowledge response keys: {}", knowledge.fieldNames());
            } catch (Exception e) {
                log.warn("Knowledge not available for SOC {}: {}", socCode, e.getMessage());
                knowledge = objectMapper.createObjectNode();
            }

            // Fetch technology skills (with ?all=1 to get complete list, not just top 10 categories)
            JsonNode technology = null;
            try {
                technology = callONetApi("/online/occupations/" + socCode + "/details/technology_skills?all=1");
                log.debug("Technology response keys: {}", technology.fieldNames());
            } catch (Exception e) {
                log.warn("Technology skills not available for SOC {}: {}", socCode, e.getMessage());
                technology = objectMapper.createObjectNode();
            }

            // Fetch tasks (specific duties for this occupation)
            JsonNode tasks = null;
            try {
                tasks = callONetApi("/online/occupations/" + socCode + "/details/tasks");
                log.debug("Tasks response keys: {}", tasks.fieldNames());
            } catch (Exception e) {
                log.warn("Tasks not available for SOC {}: {}", socCode, e.getMessage());
                tasks = objectMapper.createObjectNode();
            }

            // Build comprehensive occupation data
            Map<String, Object> occupationData = new HashMap<>();
            occupationData.put("code", socCode);
            occupationData.put("title", occupation.path("title").asText());
            occupationData.put("description", occupation.path("description").asText());

            // Extract tasks with importance and category from separate tasks endpoint
            List<Map<String, Object>> tasksList = new ArrayList<>();
            JsonNode tasksArray = tasks.path("task");
            if (!tasksArray.isArray()) {
                tasksArray = tasks.path("element");
            }

            log.debug("Tasks array found: {}, isArray: {}, size: {}",
                tasksArray != null, tasksArray.isArray(),
                tasksArray.isArray() ? tasksArray.size() : 0);

            if (tasksArray.isArray()) {
                tasksArray.forEach(task -> {
                    Map<String, Object> taskMap = new HashMap<>();
                    // O*NET tasks have "statement" field for the task description
                    String taskName = task.path("statement").asText();
                    if (taskName.isEmpty()) {
                        taskName = task.path("name").asText();
                    }
                    taskMap.put("name", taskName);

                    // Importance score from O*NET
                    double importance = task.path("score").path("value").asDouble(0);
                    if (importance == 0) {
                        importance = task.path("scale").path("value").asDouble(0);
                    }
                    taskMap.put("importance", importance);

                    // Category: Core or Supplemental
                    String category = task.path("task_type").path("name").asText("Core");
                    if (category.isEmpty()) {
                        category = task.path("category").asText("Core");
                    }
                    taskMap.put("category", category);

                    if (!taskName.isEmpty()) {
                        tasksList.add(taskMap);
                    }
                });
            }
            occupationData.put("tasks", tasksList);
            log.info("Extracted {} O*NET tasks", tasksList.size());

            // Extract detailed work activities
            List<Map<String, Object>> activitiesList = new ArrayList<>();

            // Log the full activities response to debug
            log.debug("Full activities response: {}", activities.toString().substring(0, Math.min(500, activities.toString().length())));

            // Try multiple possible paths - O*NET uses "element" array
            JsonNode activitiesArray = activities.path("element");
            if (!activitiesArray.isArray()) {
                activitiesArray = activities.path("work_activities");
            }
            if (!activitiesArray.isArray()) {
                activitiesArray = activities.path("detailed_work_activity");
            }

            log.debug("Activities array found: {}, isArray: {}, size: {}",
                activitiesArray != null, activitiesArray.isArray(),
                activitiesArray.isArray() ? activitiesArray.size() : 0);

            // Log first activity if found
            if (activitiesArray.isArray() && activitiesArray.size() > 0) {
                log.debug("First activity structure: {}", activitiesArray.get(0).toString());
            }

            if (activitiesArray.isArray()) {
                activitiesArray.forEach(activity -> {
                    Map<String, Object> activityMap = new HashMap<>();
                    // Try both field name patterns
                    String activityName = activity.path("name").asText();
                    if (activityName.isEmpty()) {
                        activityName = activity.path("element_name").asText();
                    }
                    activityMap.put("name", activityName);

                    double importance = activity.path("score").path("value").asDouble(0);
                    if (importance == 0) {
                        importance = activity.path("scale_value").asDouble(0);
                    }
                    activityMap.put("importance", importance);

                    activitiesList.add(activityMap);
                });
            }
            occupationData.put("detailed_work_activities", activitiesList);
            log.info("Extracted {} detailed work activities", activitiesList.size());

            // Extract skills
            List<Map<String, Object>> skillsList = new ArrayList<>();

            // Try multiple possible paths for skills
            JsonNode skillsArray = skills.path("skills");
            if (!skillsArray.isArray()) {
                skillsArray = skills.path("skill");
            }
            if (!skillsArray.isArray()) {
                skillsArray = skills.path("element");
            }

            log.debug("Skills array found: {}, isArray: {}, size: {}",
                skillsArray != null, skillsArray.isArray(),
                skillsArray.isArray() ? skillsArray.size() : 0);

            // Log first skill element structure if available
            if (skillsArray.isArray() && skillsArray.size() > 0) {
                log.debug("First skill structure: {}", skillsArray.get(0).toString());
            }

            if (skillsArray.isArray()) {
                skillsArray.forEach(skill -> {
                    Map<String, Object> skillMap = new HashMap<>();
                    // O*NET uses "name" not "element_name"
                    String skillName = skill.path("name").asText();
                    if (skillName.isEmpty()) {
                        skillName = skill.path("element_name").asText();
                    }
                    skillMap.put("name", skillName);

                    // O*NET uses "score.value" not "scale_value"
                    double level = skill.path("score").path("value").asDouble(0);
                    if (level == 0) {
                        level = skill.path("scale_value").asDouble(0);
                    }
                    skillMap.put("level", level);

                    skillsList.add(skillMap);
                });
            }
            occupationData.put("skills", skillsList);
            log.info("Extracted {} O*NET skills", skillsList.size());

            // Extract knowledge
            List<Map<String, Object>> knowledgeList = new ArrayList<>();
            JsonNode knowledgeArray = knowledge.path("knowledge");
            if (knowledgeArray.isArray()) {
                knowledgeArray.forEach(k -> {
                    Map<String, Object> knowledgeMap = new HashMap<>();
                    knowledgeMap.put("name", k.path("element_name").asText());
                    knowledgeMap.put("level", k.path("scale_value").asDouble(0));
                    knowledgeList.add(knowledgeMap);
                });
            }
            occupationData.put("knowledge", knowledgeList);

            // Extract technology skills
            List<Map<String, Object>> techSkills = new ArrayList<>();

            // Log technology response for debugging
            log.debug("Full technology response: {}", technology.toString().substring(0, Math.min(500, technology.toString().length())));

            // O*NET technology endpoint returns "category" array (NOT "element")
            JsonNode categoryArray = technology.path("category");
            log.debug("Technology category array found: {}, isArray: {}, size: {}",
                categoryArray != null, categoryArray.isArray(),
                categoryArray.isArray() ? categoryArray.size() : 0);

            if (categoryArray.isArray() && categoryArray.size() > 0) {
                log.debug("First category structure: {}", categoryArray.get(0).toString());

                // O*NET returns categories with nested examples
                categoryArray.forEach(category -> {
                    String categoryName = category.path("title").path("name").asText("");
                    JsonNode examples = category.path("example");

                    if (examples.isArray()) {
                        examples.forEach(example -> {
                            Map<String, Object> techMap = new HashMap<>();
                            String techName = example.path("name").asText();
                            if (!techName.isEmpty()) {
                                techMap.put("name", techName);
                                techMap.put("category", categoryName);
                                techSkills.add(techMap);
                            }
                        });
                    }
                });
            }
            occupationData.put("technology_skills", techSkills);
            log.info("Extracted {} O*NET technology skills", techSkills.size());

            return objectMapper.writeValueAsString(occupationData);

        } catch (Exception e) {
            log.error("Error fetching O*NET data for SOC code: {}", socCode, e);
            throw new RuntimeException("Failed to fetch O*NET data", e);
        }
    }

    /**
     * Search for occupations by keyword
     */
    public List<ONetOccupationDto> searchOccupations(String keyword) {
        log.info("Searching O*NET occupations with keyword: {}", keyword);

        try {
            JsonNode response = callONetApi("/online/search?keyword=" + keyword);
            List<ONetOccupationDto> results = new ArrayList<>();

            JsonNode occupations = response.path("occupation");
            if (occupations.isArray()) {
                occupations.forEach(occ -> {
                    ONetOccupationDto dto = new ONetOccupationDto();
                    dto.setCode(occ.path("code").asText());
                    dto.setTitle(occ.path("title").asText());
                    results.add(dto);
                });
            }

            return results;

        } catch (Exception e) {
            log.error("Error searching O*NET occupations", e);
            throw new RuntimeException("Failed to search O*NET occupations", e);
        }
    }

    /**
     * Get related occupations for a SOC code
     */
    public List<ONetOccupationDto> getRelatedOccupations(String socCode) {
        log.info("Fetching related occupations for SOC code: {}", socCode);

        try {
            JsonNode response = callONetApi("/online/occupations/" + socCode + "/related_occupations");
            List<ONetOccupationDto> results = new ArrayList<>();

            JsonNode occupations = response.path("related_occupation");
            if (occupations.isArray()) {
                occupations.forEach(occ -> {
                    ONetOccupationDto dto = new ONetOccupationDto();
                    dto.setCode(occ.path("code").asText());
                    dto.setTitle(occ.path("title").asText());
                    results.add(dto);
                });
            }

            return results;

        } catch (Exception e) {
            log.error("Error fetching related occupations", e);
            throw new RuntimeException("Failed to fetch related occupations", e);
        }
    }

    /**
     * Make authenticated call to O*NET API
     */
    private JsonNode callONetApi(String endpoint) {
        try {
            String url = baseUrl + endpoint;
            log.debug("Calling O*NET API: {}", url);

            HttpHeaders headers = new HttpHeaders();

            // Create Basic Auth header
            String auth = username + ":" + password;
            byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
            String authHeader = "Basic " + new String(encodedAuth, StandardCharsets.UTF_8);
            headers.set("Authorization", authHeader);

            // Set Accept header for JSON response
            headers.set("Accept", "application/json");

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("O*NET API returned status: " + response.getStatusCode());
            }

            return objectMapper.readTree(response.getBody());

        } catch (Exception e) {
            log.error("Error calling O*NET API endpoint: {}", endpoint, e);
            throw new RuntimeException("O*NET API call failed", e);
        }
    }

    /**
     * Check if O*NET service is configured
     */
    public boolean isConfigured() {
        return username != null && !username.isEmpty() &&
               password != null && !password.isEmpty();
    }
}
