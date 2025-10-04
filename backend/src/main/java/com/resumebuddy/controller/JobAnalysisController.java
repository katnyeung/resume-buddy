package com.resumebuddy.controller;

import com.resumebuddy.model.dto.JobAnalysisResultDto;
import com.resumebuddy.model.dto.ONetOccupationDto;
import com.resumebuddy.service.JobAnalysisService;
import com.resumebuddy.service.Neo4jGraphService;
import com.resumebuddy.service.ONetIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/resumes")
@CrossOrigin(origins = "http://localhost:3000")
@RequiredArgsConstructor
@Tag(name = "Job Analysis", description = "AI-powered job analysis with O*NET integration and recruiter insights")
public class JobAnalysisController {

    private final JobAnalysisService jobAnalysisService;
    private final Neo4jGraphService neo4jGraphService;
    private final ONetIntegrationService onetIntegrationService;

    @PostMapping("/{resumeId}/experiences/{experienceId}/analyze")
    @Operation(summary = "Analyze job experience", description = "Perform comprehensive job analysis including normalization, O*NET mapping, and recruiter evaluation")
    public ResponseEntity<JobAnalysisResultDto> analyzeJob(
            @PathVariable String resumeId,
            @PathVariable String experienceId) {

        log.info("Received request to analyze job - resume: {}, experience: {}", resumeId, experienceId);

        try {
            JobAnalysisResultDto result = jobAnalysisService.analyzeJob(resumeId, experienceId);
            log.info("Successfully analyzed job experience {}", experienceId);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error analyzing job experience: {}", experienceId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{resumeId}/experiences/{experienceId}/job-analysis")
    @Operation(summary = "Get job analysis", description = "Retrieve existing job analysis results")
    public ResponseEntity<JobAnalysisResultDto> getJobAnalysis(
            @PathVariable String resumeId,
            @PathVariable String experienceId) {

        log.info("Getting job analysis - resume: {}, experience: {}", resumeId, experienceId);

        try {
            JobAnalysisResultDto result = jobAnalysisService.getJobAnalysis(resumeId, experienceId);
            return ResponseEntity.ok(result);

        } catch (RuntimeException e) {
            log.warn("Job analysis not found for experience: {}", experienceId);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            log.error("Error getting job analysis: {}", experienceId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{resumeId}/experiences/{experienceId}/job-analysis/exists")
    @Operation(summary = "Check if job analysis exists", description = "Check if job analysis has been performed for this experience")
    public ResponseEntity<Boolean> analysisExists(
            @PathVariable String resumeId,
            @PathVariable String experienceId) {

        boolean exists = jobAnalysisService.analysisExists(resumeId, experienceId);
        return ResponseEntity.ok(exists);
    }

    @DeleteMapping("/{resumeId}/experiences/{experienceId}/job-analysis")
    @Operation(summary = "Delete job analysis", description = "Delete existing job analysis")
    public ResponseEntity<Void> deleteJobAnalysis(
            @PathVariable String resumeId,
            @PathVariable String experienceId) {

        log.info("Deleting job analysis - resume: {}, experience: {}", resumeId, experienceId);

        try {
            jobAnalysisService.deleteJobAnalysis(resumeId, experienceId);
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            log.error("Error deleting job analysis: {}", experienceId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/neo4j/health")
    @Operation(summary = "Test Neo4j connection", description = "Check if Neo4j Aura database is connected")
    public ResponseEntity<Map<String, Object>> testNeo4jConnection() {
        log.info("Testing Neo4j connection");

        boolean connected = neo4jGraphService.testConnection();
        Map<String, Object> response = Map.of(
                "connected", connected,
                "message", connected ? "Neo4j Aura connected successfully" : "Neo4j connection failed"
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/neo4j/stats")
    @Operation(summary = "Get graph statistics", description = "Get Neo4j graph database statistics")
    public ResponseEntity<Map<String, Object>> getGraphStats() {
        log.info("Getting Neo4j graph statistics");

        Map<String, Object> stats = neo4jGraphService.getGraphStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/onet/test")
    @Operation(summary = "Test O*NET connection", description = "Test O*NET API connection and configuration")
    public ResponseEntity<Map<String, Object>> testONetConnection() {
        log.info("Testing O*NET connection");

        boolean configured = onetIntegrationService.isConfigured();
        Map<String, Object> response = Map.of(
                "configured", configured,
                "message", configured ? "O*NET API credentials configured" : "O*NET API not configured - check username/password"
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/onet/occupation/{socCode}")
    @Operation(summary = "Get O*NET occupation details", description = "Fetch comprehensive O*NET occupation data for a SOC code")
    public ResponseEntity<String> getONetOccupation(@PathVariable String socCode) {
        log.info("Fetching O*NET data for SOC code: {}", socCode);

        try {
            String data = onetIntegrationService.getOccupationDetails(socCode);
            return ResponseEntity.ok(data);

        } catch (Exception e) {
            log.error("Error fetching O*NET data", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/onet/search")
    @Operation(summary = "Search O*NET occupations", description = "Search for occupations by keyword")
    public ResponseEntity<List<ONetOccupationDto>> searchONetOccupations(@RequestParam String keyword) {
        log.info("Searching O*NET occupations with keyword: {}", keyword);

        try {
            List<ONetOccupationDto> results = onetIntegrationService.searchOccupations(keyword);
            return ResponseEntity.ok(results);

        } catch (Exception e) {
            log.error("Error searching O*NET occupations", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/onet/occupation/{socCode}/related")
    @Operation(summary = "Get related occupations", description = "Get related occupations for a SOC code from O*NET")
    public ResponseEntity<List<ONetOccupationDto>> getRelatedOccupations(@PathVariable String socCode) {
        log.info("Fetching related occupations for SOC code: {}", socCode);

        try {
            List<ONetOccupationDto> results = onetIntegrationService.getRelatedOccupations(socCode);
            return ResponseEntity.ok(results);

        } catch (Exception e) {
            log.error("Error fetching related occupations", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
