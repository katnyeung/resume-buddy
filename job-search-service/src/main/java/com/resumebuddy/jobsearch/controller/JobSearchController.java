package com.resumebuddy.jobsearch.controller;

import com.resumebuddy.jobsearch.domain.JobListing;
import com.resumebuddy.jobsearch.domain.JobMatch;
import com.resumebuddy.jobsearch.dto.*;
import com.resumebuddy.jobsearch.repository.JobListingRepository;
import com.resumebuddy.jobsearch.service.JobMatchingApplicationService;
import com.resumebuddy.jobsearch.service.JobSearchApplicationService;
import com.resumebuddy.jobsearch.service.Neo4jJobListingService;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private final Neo4jJobListingService neo4jJobListingService;
    private final JobListingRepository jobListingRepository;

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
    public ResponseEntity<JobSearchProfile> createProfile(
            @Valid @RequestBody CreateProfileRequest request,
            Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        log.info("Creating profile for user {} with resume {} and {} experiences",
                userId, request.getResumeId(), request.getExperienceIds().size());

        JobSearchProfile profile = jobSearchService.createProfile(
                userId,
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
            @Parameter(description = "Resume ID") @RequestParam String resumeId,
            Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        List<JobSearchProfile> profiles = jobSearchService.getProfilesByResumeAndUser(resumeId, userId);
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

    /**
     * Update last visit date for a profile
     * PATCH /api/job-search/profiles/{id}/visit
     */
    @Operation(
            summary = "Record profile visit",
            description = "Updates the last_visit_date timestamp for a profile. Called when user visits job-search page or clicks 'Refresh Results'. Used to determine active profiles for LLM keyword generation."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Visit recorded successfully"),
            @ApiResponse(responseCode = "404", description = "Profile not found")
    })
    @PatchMapping("/profiles/{id}/visit")
    public ResponseEntity<JobSearchProfile> recordVisit(
            @Parameter(description = "Profile ID") @PathVariable String id) {
        log.info("Recording visit for profile: {}", id);
        JobSearchProfile profile = jobSearchService.recordVisit(id);
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
            @RequestParam(defaultValue = "20") int topK,
            @Parameter(description = "Only include jobs posted within last N days (0 = no limit, default: 30)")
            @RequestParam(defaultValue = "30") int maxDaysOld) {

        log.info("Searching for top {} matching jobs for profile: {} (maxDaysOld: {})", topK, id, maxDaysOld);
        jobMatchingService.searchMatchingJobs(id, topK, maxDaysOld);

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
            response.setFetchedAt(enriched.getListing().getFetchedAt());

            // Skill gap
            response.setMatchedSkills(enriched.getSkillGap().getMatchedSkills());
            response.setMissingSkills(enriched.getSkillGap().getMissingSkills());
            response.setSkillMatchPercentage(enriched.getSkillGap().getMatchPercentage());
            response.setWeightedSkillScore(enriched.getSkillGap().getWeightedScore());

            // User actions
            response.setIsSaved(enriched.getMatch().getIsSaved());
            response.setIsApplied(enriched.getMatch().getIsApplied());
            response.setIsRedflag(enriched.getMatch().getIsRedflag());
            response.setFlaggedAt(enriched.getMatch().getFlaggedAt());

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

            // User actions
            response.setIsSaved(enriched.getMatch().getIsSaved());
            response.setIsApplied(enriched.getMatch().getIsApplied());
            response.setIsRedflag(enriched.getMatch().getIsRedflag());
            response.setFlaggedAt(enriched.getMatch().getFlaggedAt());

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
            @RequestParam(defaultValue = "false") boolean refresh,
            @Parameter(description = "Only include jobs posted within last N days (0 = no limit, default: 30)")
            @RequestParam(defaultValue = "30") int maxDaysOld) {

        log.info("Getting matching results for profile: {} (topK: {}, refresh: {}, maxDaysOld: {})",
                id, topK, refresh, maxDaysOld);

        // Record visit (updates last_visit_date for keyword generation tracking)
        jobSearchService.recordVisit(id);

        // Get profile
        JobSearchProfile profile = jobSearchService.getProfile(id);

        // Get or create matching results (with optional refresh and date filter)
        List<JobMatchingApplicationService.JobMatchWithListing> enrichedMatches =
                jobMatchingService.getOrCreateMatchingResults(id, topK, refresh, maxDaysOld);

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
            response.setFetchedAt(enriched.getListing().getFetchedAt());

            // Skill gap
            response.setMatchedSkills(enriched.getSkillGap().getMatchedSkills());
            response.setMissingSkills(enriched.getSkillGap().getMissingSkills());
            response.setSkillMatchPercentage(enriched.getSkillGap().getMatchPercentage());
            response.setWeightedSkillScore(enriched.getSkillGap().getWeightedScore());

            // User actions
            response.setIsSaved(enriched.getMatch().getIsSaved());
            response.setIsApplied(enriched.getMatch().getIsApplied());
            response.setIsRedflag(enriched.getMatch().getIsRedflag());
            response.setFlaggedAt(enriched.getMatch().getFlaggedAt());

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

    /**
     * Update saved rating for a match
     * PATCH /api/job-search/matches/{matchId}/save
     */
    @Operation(
            summary = "Update saved rating",
            description = "Updates the star rating (0-3) for a job match. 0=not saved, 1-3=saved with priority. Saved matches are preserved during force refresh."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Saved rating updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid rating value (must be 0-3)"),
            @ApiResponse(responseCode = "404", description = "Match not found")
    })
    @PatchMapping("/matches/{matchId}/save")
    public ResponseEntity<JobMatch> toggleSaved(
            @Parameter(description = "Match ID") @PathVariable String matchId,
            @RequestBody SaveMatchRequest request) {
        log.info("Updating saved rating for match {}: {}", matchId, request.getRating());

        JobMatch match = jobMatchingService.toggleSaved(matchId, request.getRating());
        return ResponseEntity.ok(match);
    }

    /**
     * Mark match as applied
     * PATCH /api/job-search/matches/{matchId}/apply
     */
    @Operation(
            summary = "Mark as applied",
            description = "Marks a job match as applied. This action sets the applied timestamp and cannot be undone."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Match marked as applied successfully"),
            @ApiResponse(responseCode = "404", description = "Match not found")
    })
    @PatchMapping("/matches/{matchId}/apply")
    public ResponseEntity<JobMatch> markApplied(
            @Parameter(description = "Match ID") @PathVariable String matchId) {
        log.info("Marking match {} as applied", matchId);

        JobMatch match = jobMatchingService.markApplied(matchId);
        return ResponseEntity.ok(match);
    }

    /**
     * Toggle redflag status for a match
     * PATCH /api/job-search/matches/{matchId}/redflag
     */
    @Operation(
            summary = "Toggle redflag status",
            description = "Toggles the redflag (not interested/expired) status for a job match. Redflagged matches are shown in a collapsible section and automatically deleted after 7 days."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Redflag status updated successfully"),
            @ApiResponse(responseCode = "404", description = "Match not found")
    })
    @PatchMapping("/matches/{matchId}/redflag")
    public ResponseEntity<JobMatch> toggleRedflag(
            @Parameter(description = "Match ID") @PathVariable String matchId,
            @RequestBody RedflagRequest request) {
        log.info("Toggling redflag status for match {}: {}", matchId, request.isRedflag());

        JobMatch match = jobMatchingService.toggleRedflag(matchId, request.isRedflag());
        return ResponseEntity.ok(match);
    }

    /**
     * Skill Drilldown: Get top in-demand skills
     * GET /api/job-search/skills/top?limit=30
     */
    @Operation(
            summary = "Get top in-demand skills",
            description = "Returns top N skills by job count from Neo4j graph. " +
                    "Used to populate skill tag cloud for interactive filtering."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Skills retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to fetch skills")
    })
    @GetMapping("/skills/top")
    public ResponseEntity<List<SkillCooccurrence>> getTopSkills(
            @Parameter(description = "Number of top skills to return") @RequestParam(defaultValue = "30") int limit) {
        log.info("Fetching top {} in-demand skills", limit);

        try {
            Map<String, Long> topSkills = neo4jJobListingService.getTopInDemandSkills(limit);

            List<SkillCooccurrence> result = topSkills.entrySet().stream()
                    .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue())) // Sort by job count DESC
                    .map(entry -> new SkillCooccurrence(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to fetch top skills", e);
            return ResponseEntity.status(500).body(new ArrayList<>());
        }
    }

    /**
     * Skill Drilldown: Find jobs by skills (Neo4j graph query)
     * POST /api/job-search/skills/drilldown
     */
    @Operation(
            summary = "Find jobs by skills (Neo4j)",
            description = "Uses Neo4j graph to find jobs that require ALL specified skills (AND logic). " +
                    "Returns job IDs, date distribution, and related skills for drill-down UI."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Drilldown results retrieved successfully",
                    content = @Content(schema = @Schema(implementation = SkillDrilldownResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Neo4j query failed")
    })
    @PostMapping("/skills/drilldown")
    public ResponseEntity<SkillDrilldownResponse> skillDrilldown(@Valid @RequestBody SkillDrilldownRequest request) {
        log.info("Skill drilldown requested for: {}", request.getSkills());

        try {
            // Step 1: Query Neo4j for jobs matching ALL skills
            Map<String, Object> jobData = neo4jJobListingService.getJobsBySkills(request.getSkills());
            @SuppressWarnings("unchecked")
            List<String> jobIds = (List<String>) jobData.get("jobIds");
            @SuppressWarnings("unchecked")
            Map<String, Long> dateDistribution = (Map<String, Long>) jobData.get("dateDistribution");

            // Step 2: Get related skills (skills that co-occur with selected skills)
            Map<String, Long> relatedSkillsMap = neo4jJobListingService.getRelatedSkills(request.getSkills(), 50);
            List<SkillCooccurrence> relatedSkills = relatedSkillsMap.entrySet().stream()
                    .map(entry -> new SkillCooccurrence(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            // Step 3: Build response
            SkillDrilldownResponse response = new SkillDrilldownResponse();
            response.setTotalJobs((long) jobIds.size());
            response.setJobIds(jobIds);
            response.setDateDistribution(dateDistribution);
            response.setRelatedSkills(relatedSkills);

            log.info("Drilldown complete: {} jobs, {} related skills", jobIds.size(), relatedSkills.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Skill drilldown failed for: {}", request.getSkills(), e);
            return ResponseEntity.status(500).body(new SkillDrilldownResponse(
                    0L, List.of(), Map.of(), List.of()
            ));
        }
    }

    /**
     * Fetch jobs by IDs (from Neo4j drilldown results)
     * POST /api/job-search/jobs/by-ids
     */
    @Operation(
            summary = "Fetch jobs by IDs",
            description = "Retrieves full job details from MySQL for given job IDs (from Neo4j queries). " +
                    "Optionally filters by date. Used to display jobs after skill drilldown."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Jobs retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Database query failed")
    })
    @PostMapping("/jobs/by-ids")
    public ResponseEntity<List<JobListing>> getJobsByIds(@Valid @RequestBody JobsByIdsRequest request) {
        log.info("Fetching {} jobs by IDs (maxDaysOld: {})", request.getJobIds().size(), request.getMaxDaysOld());

        try {
            // Fetch jobs from MySQL by IDs
            List<JobListing> jobs = jobListingRepository.findByIdIn(request.getJobIds());

            // Apply date filter if specified
            if (request.getMaxDaysOld() != null && request.getMaxDaysOld() > 0) {
                LocalDateTime cutoff = LocalDateTime.now().minusDays(request.getMaxDaysOld());
                jobs = jobs.stream()
                        .filter(job -> {
                            LocalDateTime date = job.getPostedDate() != null ? job.getPostedDate() : job.getFetchedAt();
                            return date.isAfter(cutoff);
                        })
                        .collect(Collectors.toList());
            }

            log.info("Returning {} jobs (after date filtering)", jobs.size());
            return ResponseEntity.ok(jobs);

        } catch (Exception e) {
            log.error("Failed to fetch jobs by IDs", e);
            return ResponseEntity.status(500).body(new ArrayList<>());
        }
    }

    // Request DTOs
    public static class SaveMatchRequest {
        private int rating; // 0-3 star rating

        public int getRating() {
            return rating;
        }

        public void setRating(int rating) {
            this.rating = rating;
        }
    }

    public static class RedflagRequest {
        private boolean redflag;

        public boolean isRedflag() {
            return redflag;
        }

        public void setRedflag(boolean redflag) {
            this.redflag = redflag;
        }
    }
}
