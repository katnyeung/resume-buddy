package com.resumebuddy.jobsearch.controller;

import com.resumebuddy.jobsearch.dto.CreateProfileRequest;
import com.resumebuddy.jobsearch.dto.JobMatchResponse;
import com.resumebuddy.jobsearch.dto.JobMatchingResultResponse;
import com.resumebuddy.jobsearch.dto.UpdateJobPostRequest;
import com.resumebuddy.jobsearch.service.JobMatchingApplicationService;
import com.resumebuddy.jobsearch.service.JobSearchApplicationService;
import com.resumebuddy.jobsearch.domain.JobSearchProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * REST Controller: Job Search API
 */
@RestController
@RequestMapping("/api/job-search")
@Slf4j
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Job Search", description = "AI-powered job search profile and matching operations")
public class JobSearchController {

    private final JobSearchApplicationService jobSearchService;
    private final JobMatchingApplicationService jobMatchingService;

    /**
     * Create job search profile from selected experiences
     * POST /api/job-search/profiles
     */
    @Operation(
            summary = "Create job search profile",
            description = "Creates a job search profile from selected resume experiences. LLM generates an aggregated job post, which is then vectorized for matching."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Profile created successfully",
                    content = @Content(schema = @Schema(implementation = JobSearchProfile.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/profiles")
    public ResponseEntity<JobSearchProfile> createProfile(@Valid @RequestBody CreateProfileRequest request) {
        log.info("Creating profile for resume {} with {} experiences",
                request.getResumeId(), request.getExperienceIds().size());

        JobSearchProfile profile = jobSearchService.createProfile(
                request.getResumeId(),
                request.getExperienceIds()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(profile);
    }

    /**
     * Update profile's job post
     * PUT /api/job-search/profiles/{id}
     */
    @Operation(
            summary = "Update job post",
            description = "Updates the editable job post for an existing profile. The vector embedding is regenerated."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile updated successfully",
                    content = @Content(schema = @Schema(implementation = JobSearchProfile.class))),
            @ApiResponse(responseCode = "404", description = "Profile not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    })
    @PutMapping("/profiles/{id}")
    public ResponseEntity<JobSearchProfile> updateJobPost(
            @Parameter(description = "Profile ID") @PathVariable String id,
            @Valid @RequestBody UpdateJobPostRequest request) {

        log.info("Updating job post for profile: {}", id);
        JobSearchProfile profile = jobSearchService.updateJobPost(id, request.getEditedJobPost());
        return ResponseEntity.ok(profile);
    }

    /**
     * Get profile by ID
     * GET /api/job-search/profiles/{id}
     */
    @Operation(summary = "Get profile", description = "Retrieves a job search profile by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile found",
                    content = @Content(schema = @Schema(implementation = JobSearchProfile.class))),
            @ApiResponse(responseCode = "404", description = "Profile not found")
    })
    @GetMapping("/profiles/{id}")
    public ResponseEntity<JobSearchProfile> getProfile(
            @Parameter(description = "Profile ID") @PathVariable String id) {
        JobSearchProfile profile = jobSearchService.getProfile(id);
        return ResponseEntity.ok(profile);
    }

    /**
     * Get all profiles for a resume
     * GET /api/job-search/profiles?resumeId={resumeId}
     */
    @Operation(summary = "Get profiles by resume", description = "Lists all job search profiles for a specific resume")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profiles retrieved successfully")
    })
    @GetMapping("/profiles")
    public ResponseEntity<List<JobSearchProfile>> getProfilesByResume(
            @Parameter(description = "Resume ID") @RequestParam String resumeId) {
        List<JobSearchProfile> profiles = jobSearchService.getProfilesByResume(resumeId);
        return ResponseEntity.ok(profiles);
    }

    /**
     * Get all lines for a profile
     * GET /api/job-search/profiles/{id}/lines
     */
    @Operation(summary = "Get profile lines", description = "Retrieves all lines from the mock job post for a profile (single source of truth)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lines retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Profile not found")
    })
    @GetMapping("/profiles/{id}/lines")
    public ResponseEntity<List<com.resumebuddy.jobsearch.domain.JobSearchProfileLine>> getProfileLines(
            @Parameter(description = "Profile ID") @PathVariable String id) {
        log.info("Getting lines for profile: {}", id);
        List<com.resumebuddy.jobsearch.domain.JobSearchProfileLine> lines = jobSearchService.getProfileLines(id);
        return ResponseEntity.ok(lines);
    }

    /**
     * Get all skills for a profile
     * GET /api/job-search/profiles/{id}/skills
     */
    @Operation(summary = "Get profile skills", description = "Retrieves all skills for a job search profile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Skills retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Profile not found")
    })
    @GetMapping("/profiles/{id}/skills")
    public ResponseEntity<List<com.resumebuddy.jobsearch.domain.JobSearchProfileSkill>> getProfileSkills(
            @Parameter(description = "Profile ID") @PathVariable String id) {
        log.info("Getting skills for profile: {}", id);
        List<com.resumebuddy.jobsearch.domain.JobSearchProfileSkill> skills = jobSearchService.getProfileSkills(id);
        return ResponseEntity.ok(skills);
    }

    /**
     * Add a skill to a profile
     * POST /api/job-search/profiles/{id}/skills
     */
    @Operation(summary = "Add profile skill", description = "Adds a new skill to a job search profile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Skill added successfully"),
            @ApiResponse(responseCode = "400", description = "Skill already exists or invalid request"),
            @ApiResponse(responseCode = "404", description = "Profile not found")
    })
    @PostMapping("/profiles/{id}/skills")
    public ResponseEntity<com.resumebuddy.jobsearch.domain.JobSearchProfileSkill> addProfileSkill(
            @Parameter(description = "Profile ID") @PathVariable String id,
            @RequestBody AddSkillRequest request) {
        log.info("Adding skill to profile {}: {} (proficiency: {})", id, request.getSkillName(), request.getProficiencyScore());
        com.resumebuddy.jobsearch.domain.JobSearchProfileSkill skill =
                jobSearchService.addProfileSkill(id, request.getSkillName(), request.getSkillCategory(), request.getProficiencyScore());
        return ResponseEntity.status(HttpStatus.CREATED).body(skill);
    }

    /**
     * Update skill proficiency score
     * PATCH /api/job-search/profiles/{profileId}/skills/{skillId}/proficiency
     */
    @Operation(summary = "Update skill proficiency", description = "Updates the proficiency score (0-100) for a skill")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Proficiency updated successfully"),
            @ApiResponse(responseCode = "404", description = "Skill not found")
    })
    @PatchMapping("/profiles/{profileId}/skills/{skillId}/proficiency")
    public ResponseEntity<com.resumebuddy.jobsearch.domain.JobSearchProfileSkill> updateSkillProficiency(
            @Parameter(description = "Profile ID") @PathVariable String profileId,
            @Parameter(description = "Skill ID") @PathVariable String skillId,
            @RequestBody UpdateProficiencyRequest request) {
        log.info("Updating proficiency for skill {} in profile {}: {}", skillId, profileId, request.getProficiencyScore());
        com.resumebuddy.jobsearch.domain.JobSearchProfileSkill skill =
                jobSearchService.updateSkillProficiency(skillId, request.getProficiencyScore());
        return ResponseEntity.ok(skill);
    }

    /**
     * Remove a skill from a profile
     * DELETE /api/job-search/profiles/{profileId}/skills/{skillId}
     */
    @Operation(summary = "Remove profile skill", description = "Removes a skill from a job search profile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Skill removed successfully"),
            @ApiResponse(responseCode = "404", description = "Skill not found")
    })
    @DeleteMapping("/profiles/{profileId}/skills/{skillId}")
    public ResponseEntity<Void> removeProfileSkill(
            @Parameter(description = "Profile ID") @PathVariable String profileId,
            @Parameter(description = "Skill ID") @PathVariable String skillId) {
        log.info("Removing skill {} from profile {}", skillId, profileId);
        jobSearchService.removeProfileSkill(skillId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Update profile location
     * PUT /api/job-search/profiles/{id}/location
     */
    @Operation(summary = "Update profile location", description = "Updates the target job search location for a profile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Location updated successfully"),
            @ApiResponse(responseCode = "404", description = "Profile not found")
    })
    @PutMapping("/profiles/{id}/location")
    public ResponseEntity<JobSearchProfile> updateLocation(
            @Parameter(description = "Profile ID") @PathVariable String id,
            @RequestBody UpdateLocationRequest request) {
        log.info("Updating location for profile {}: {}", id, request.getLocation());
        JobSearchProfile profile = jobSearchService.updateLocation(id, request.getLocation());
        return ResponseEntity.ok(profile);
    }

    /**
     * Update profile metadata (location and experience level)
     * PUT /api/job-search/profiles/{id}/metadata
     */
    @Operation(summary = "Update profile metadata", description = "Updates location and/or experience level for a profile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Metadata updated successfully"),
            @ApiResponse(responseCode = "404", description = "Profile not found")
    })
    @PutMapping("/profiles/{id}/metadata")
    public ResponseEntity<JobSearchProfile> updateMetadata(
            @Parameter(description = "Profile ID") @PathVariable String id,
            @RequestBody UpdateMetadataRequest request) {
        log.info("Updating metadata for profile {}: location={}, experienceLevel={}, desiredJobTitle={}",
                id, request.getLocation(), request.getExperienceLevel(), request.getDesiredJobTitle());
        JobSearchProfile profile = jobSearchService.updateProfileMetadata(
                id, request.getLocation(), request.getExperienceLevel(), request.getDesiredJobTitle());
        return ResponseEntity.ok(profile);
    }

    // Inner DTO classes
    public static class AddSkillRequest {
        private String skillName;
        private String skillCategory;
        private Integer proficiencyScore; // 0-100

        public String getSkillName() {
            return skillName;
        }

        public void setSkillName(String skillName) {
            this.skillName = skillName;
        }

        public String getSkillCategory() {
            return skillCategory;
        }

        public void setSkillCategory(String skillCategory) {
            this.skillCategory = skillCategory;
        }

        public Integer getProficiencyScore() {
            return proficiencyScore;
        }

        public void setProficiencyScore(Integer proficiencyScore) {
            this.proficiencyScore = proficiencyScore;
        }
    }

    public static class UpdateLocationRequest {
        private String location;

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }
    }

    public static class UpdateMetadataRequest {
        private String location;
        private String experienceLevel;
        private String desiredJobTitle;

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getExperienceLevel() {
            return experienceLevel;
        }

        public void setExperienceLevel(String experienceLevel) {
            this.experienceLevel = experienceLevel;
        }

        public String getDesiredJobTitle() {
            return desiredJobTitle;
        }

        public void setDesiredJobTitle(String desiredJobTitle) {
            this.desiredJobTitle = desiredJobTitle;
        }
    }

    public static class UpdateProficiencyRequest {
        private Integer proficiencyScore;

        public Integer getProficiencyScore() {
            return proficiencyScore;
        }

        public void setProficiencyScore(Integer proficiencyScore) {
            this.proficiencyScore = proficiencyScore;
        }
    }

    /**
     * Delete profile
     * DELETE /api/job-search/profiles/{id}
     */
    @Operation(summary = "Delete profile", description = "Deletes a job search profile and its associated matches")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Profile deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Profile not found")
    })
    @DeleteMapping("/profiles/{id}")
    public ResponseEntity<Void> deleteProfile(
            @Parameter(description = "Profile ID") @PathVariable String id) {
        log.info("Deleting profile: {}", id);
        jobSearchService.deleteProfile(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Search for matching jobs
     * POST /api/job-search/profiles/{id}/search
     */
    @Operation(
            summary = "Search matching jobs",
            description = "Performs vector similarity search to find top-K matching jobs from the job listings database. Results include skill gap analysis."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search completed successfully"),
            @ApiResponse(responseCode = "404", description = "Profile not found")
    })
    @PostMapping("/profiles/{id}/search")
    public ResponseEntity<List<JobMatchResponse>> searchMatchingJobs(
            @Parameter(description = "Profile ID") @PathVariable String id,
            @Parameter(description = "Number of top matches to return (default: 20)")
            @RequestParam(defaultValue = "20") int topK) {

        log.info("Searching for top {} matching jobs for profile: {}", topK, id);
        jobMatchingService.searchMatchingJobs(id, topK);

        // Return enriched matches
        List<JobMatchingApplicationService.JobMatchWithListing> enrichedMatches =
                jobMatchingService.getMatchesWithListings(id);

        List<JobMatchResponse> responses = new ArrayList<>();
        for (JobMatchingApplicationService.JobMatchWithListing enriched : enrichedMatches) {
            JobMatchResponse response = new JobMatchResponse();
            response.setMatchId(enriched.getMatch().getId());
            response.setProfileId(enriched.getMatch().getProfileId());
            response.setListingId(enriched.getMatch().getListingId());
            response.setSimilarityScore(enriched.getMatch().getSimilarityScore());
            response.setMatchLevel(getMatchLevel(enriched.getMatch().getSimilarityScore()));

            // Job listing details
            response.setTitle(enriched.getListing().getTitle());
            response.setCompany(enriched.getListing().getCompany());
            response.setLocation(enriched.getListing().getLocation());
            response.setDescription(enriched.getListing().getDescription());
            response.setUrl(enriched.getListing().getUrl());
            response.setSalaryRange(enriched.getListing().getSalaryRange());
            response.setPostedDate(enriched.getListing().getPostedDate());

            // Skill gap
            response.setMatchedSkills(enriched.getSkillGap().getMatchedSkills());
            response.setMissingSkills(enriched.getSkillGap().getMissingSkills());
            response.setSkillMatchPercentage(enriched.getSkillGap().getMatchPercentage());
            response.setWeightedSkillScore(enriched.getSkillGap().getWeightedScore());

            responses.add(response);
        }

        // Sort by combined score (primary), then by posted date descending (secondary - newest first)
        responses.sort((a, b) -> {
            int scoreCompare = b.getSimilarityScore().compareTo(a.getSimilarityScore());
            if (scoreCompare != 0) return scoreCompare;

            // Secondary sort: newer jobs first (nulls last)
            if (a.getPostedDate() == null && b.getPostedDate() == null) return 0;
            if (a.getPostedDate() == null) return 1; // b is newer
            if (b.getPostedDate() == null) return -1; // a is newer
            return b.getPostedDate().compareTo(a.getPostedDate()); // Descending
        });

        return ResponseEntity.ok(responses);
    }

    /**
     * Get matches for a profile
     * GET /api/job-search/profiles/{id}/matches
     */
    @Operation(
            summary = "Get profile matches",
            description = "Retrieves previously computed job matches for a profile with enriched data (skill gaps, listings)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Matches retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Profile not found")
    })
    @GetMapping("/profiles/{id}/matches")
    public ResponseEntity<List<JobMatchResponse>> getMatches(
            @Parameter(description = "Profile ID") @PathVariable String id) {
        log.info("Getting matches for profile: {}", id);

        List<JobMatchingApplicationService.JobMatchWithListing> enrichedMatches =
                jobMatchingService.getMatchesWithListings(id);

        List<JobMatchResponse> responses = new ArrayList<>();
        for (JobMatchingApplicationService.JobMatchWithListing enriched : enrichedMatches) {
            JobMatchResponse response = new JobMatchResponse();
            response.setMatchId(enriched.getMatch().getId());
            response.setProfileId(enriched.getMatch().getProfileId());
            response.setListingId(enriched.getMatch().getListingId());
            response.setSimilarityScore(enriched.getMatch().getSimilarityScore());
            response.setMatchLevel(getMatchLevel(enriched.getMatch().getSimilarityScore()));

            response.setTitle(enriched.getListing().getTitle());
            response.setCompany(enriched.getListing().getCompany());
            response.setLocation(enriched.getListing().getLocation());
            response.setDescription(enriched.getListing().getDescription());
            response.setUrl(enriched.getListing().getUrl());
            response.setSalaryRange(enriched.getListing().getSalaryRange());
            response.setPostedDate(enriched.getListing().getPostedDate());

            response.setMatchedSkills(enriched.getSkillGap().getMatchedSkills());
            response.setMissingSkills(enriched.getSkillGap().getMissingSkills());
            response.setSkillMatchPercentage(enriched.getSkillGap().getMatchPercentage());
            response.setWeightedSkillScore(enriched.getSkillGap().getWeightedScore());

            responses.add(response);
        }

        // Sort by combined score (primary), then by posted date descending (secondary - newest first)
        responses.sort((a, b) -> {
            int scoreCompare = b.getSimilarityScore().compareTo(a.getSimilarityScore());
            if (scoreCompare != 0) return scoreCompare;

            // Secondary sort: newer jobs first (nulls last)
            if (a.getPostedDate() == null && b.getPostedDate() == null) return 0;
            if (a.getPostedDate() == null) return 1; // b is newer
            if (b.getPostedDate() == null) return -1; // a is newer
            return b.getPostedDate().compareTo(a.getPostedDate()); // Descending
        });

        return ResponseEntity.ok(responses);
    }

    private String getMatchLevel(BigDecimal similarityScore) {
        if (similarityScore.compareTo(new BigDecimal("0.85")) >= 0) {
            return "STRONG";
        } else if (similarityScore.compareTo(new BigDecimal("0.70")) >= 0) {
            return "GOOD";
        } else {
            return "MODERATE";
        }
    }

    /**
     * Get job matching results for a profile
     * GET /api/job-search/profiles/{id}/matching-results
     */
    @Operation(
            summary = "Get job matching results",
            description = "Retrieves comprehensive job matching results for a profile. Performs vector similarity search combined with skill matching analysis. Results are cached - if matches don't exist, they will be computed and saved. Use refresh=true to force re-computation (useful after re-vectorizing job listings)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Matching results retrieved successfully",
                    content = @Content(schema = @Schema(implementation = JobMatchingResultResponse.class))),
            @ApiResponse(responseCode = "404", description = "Profile not found")
    })
    @GetMapping("/profiles/{id}/matching-results")
    public ResponseEntity<JobMatchingResultResponse> getMatchingResults(
            @Parameter(description = "Profile ID") @PathVariable String id,
            @Parameter(description = "Number of top matches to return (default: 20)")
            @RequestParam(defaultValue = "20") int topK,
            @Parameter(description = "Force refresh of cached matches (default: false)")
            @RequestParam(defaultValue = "false") boolean refresh) {

        log.info("Getting matching results for profile: {} (topK: {}, refresh: {})", id, topK, refresh);

        // Get profile
        JobSearchProfile profile = jobSearchService.getProfile(id);

        // Get or create matching results (with optional refresh)
        List<JobMatchingApplicationService.JobMatchWithListing> enrichedMatches =
                jobMatchingService.getOrCreateMatchingResults(id, topK, refresh);

        // Build response
        List<JobMatchResponse> matchResponses = new ArrayList<>();
        for (JobMatchingApplicationService.JobMatchWithListing enriched : enrichedMatches) {
            JobMatchResponse response = new JobMatchResponse();
            response.setMatchId(enriched.getMatch().getId());
            response.setProfileId(enriched.getMatch().getProfileId());
            response.setListingId(enriched.getMatch().getListingId());
            response.setSimilarityScore(enriched.getMatch().getSimilarityScore());
            response.setMatchLevel(getMatchLevel(enriched.getMatch().getSimilarityScore()));

            // Job listing details
            response.setTitle(enriched.getListing().getTitle());
            response.setCompany(enriched.getListing().getCompany());
            response.setLocation(enriched.getListing().getLocation());
            response.setDescription(enriched.getListing().getDescription());
            response.setUrl(enriched.getListing().getUrl());
            response.setSalaryRange(enriched.getListing().getSalaryRange());
            response.setPostedDate(enriched.getListing().getPostedDate());

            // Skill gap
            response.setMatchedSkills(enriched.getSkillGap().getMatchedSkills());
            response.setMissingSkills(enriched.getSkillGap().getMissingSkills());
            response.setSkillMatchPercentage(enriched.getSkillGap().getMatchPercentage());
            response.setWeightedSkillScore(enriched.getSkillGap().getWeightedScore());

            matchResponses.add(response);
        }

        // Sort by combined score (primary), then by posted date descending (secondary - newest first)
        matchResponses.sort((a, b) -> {
            int scoreCompare = b.getSimilarityScore().compareTo(a.getSimilarityScore());
            if (scoreCompare != 0) return scoreCompare;

            // Secondary sort: newer jobs first (nulls last)
            if (a.getPostedDate() == null && b.getPostedDate() == null) return 0;
            if (a.getPostedDate() == null) return 1; // b is newer
            if (b.getPostedDate() == null) return -1; // a is newer
            return b.getPostedDate().compareTo(a.getPostedDate()); // Descending
        });

        // Create summary (first 150 chars of job post)
        String profileSummary = profile.getGeneratedJobPost().length() > 150 ?
                profile.getGeneratedJobPost().substring(0, 150) + "..." :
                profile.getGeneratedJobPost();

        // Create metadata
        JobMatchingResultResponse.SearchMetadata metadata = new JobMatchingResultResponse.SearchMetadata(
                topK,
                matchResponses.size(),
                "vector_similarity + skill_matching"
        );

        // Build final response
        JobMatchingResultResponse result = new JobMatchingResultResponse(
                id,
                matchResponses.size(),
                profileSummary,
                matchResponses,
                metadata
        );

        return ResponseEntity.ok(result);
    }
}
