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

            // Fetch detailed work activities
            JsonNode activities = callONetApi("/online/occupations/" + socCode + "/details/work_activities");

            // Fetch skills
            JsonNode skills = callONetApi("/online/occupations/" + socCode + "/details/skills");

            // Fetch knowledge
            JsonNode knowledge = callONetApi("/online/occupations/" + socCode + "/details/knowledge");

            // Fetch technology skills
            JsonNode technology = callONetApi("/online/occupations/" + socCode + "/details/technology_skills");

            // Build comprehensive occupation data
            Map<String, Object> occupationData = new HashMap<>();
            occupationData.put("code", socCode);
            occupationData.put("title", occupation.path("title").asText());
            occupationData.put("description", occupation.path("description").asText());

            // Extract tasks
            List<String> tasks = new ArrayList<>();
            JsonNode tasksNode = occupation.path("tasks");
            if (tasksNode.isArray()) {
                tasksNode.forEach(task -> tasks.add(task.path("statement").asText()));
            }
            occupationData.put("tasks", tasks);

            // Extract detailed work activities
            List<Map<String, Object>> activitiesList = new ArrayList<>();
            JsonNode activitiesArray = activities.path("work_activities");
            if (activitiesArray.isArray()) {
                activitiesArray.forEach(activity -> {
                    Map<String, Object> activityMap = new HashMap<>();
                    activityMap.put("name", activity.path("element_name").asText());
                    activityMap.put("importance", activity.path("scale_value").asDouble(0));
                    activitiesList.add(activityMap);
                });
            }
            occupationData.put("detailed_work_activities", activitiesList);

            // Extract skills
            List<Map<String, Object>> skillsList = new ArrayList<>();
            JsonNode skillsArray = skills.path("skills");
            if (skillsArray.isArray()) {
                skillsArray.forEach(skill -> {
                    Map<String, Object> skillMap = new HashMap<>();
                    skillMap.put("name", skill.path("element_name").asText());
                    skillMap.put("level", skill.path("scale_value").asDouble(0));
                    skillsList.add(skillMap);
                });
            }
            occupationData.put("skills", skillsList);

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
            List<String> techSkills = new ArrayList<>();
            JsonNode techArray = technology.path("technology");
            if (techArray.isArray()) {
                techArray.forEach(tech -> {
                    techSkills.add(tech.path("example").asText());
                });
            }
            occupationData.put("technology_skills", techSkills);

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
