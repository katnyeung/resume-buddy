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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private final com.resumebuddy.jobsearch.service.ResumeApiClient resumeApiClient;
    private final com.resumebuddy.jobsearch.repository.CrawlActivityLogRepository crawlActivityLogRepository;

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
                request.getExperienceIds(),
                request.getExcludedKeywords()
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
        JobSearchProfile profile = jobSearchService.updateJobPost(id, request.getEditedJobPost(), request.getExcludedKeywords());
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
     * Find which excluded keywords are present in a job description (case-insensitive)
     */
    private List<String> findMatchedKeywords(String description, List<String> excludedKeywords) {
        if (description == null || excludedKeywords == null || excludedKeywords.isEmpty()) {
            return Collections.emptyList();
        }

        String lowerDesc = description.toLowerCase();
        return excludedKeywords.stream()
                .filter(keyword -> lowerDesc.contains(keyword.toLowerCase()))
                .collect(Collectors.toList());
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

        // Check for negative keywords in each job description
        if (profile.getExcludedKeywords() != null && !profile.getExcludedKeywords().isEmpty()) {
            List<String> excludedKeywords = Arrays.asList(profile.getExcludedKeywords().split(","))
                    .stream()
                    .map(String::trim)
                    .filter(kw -> !kw.isEmpty())
                    .collect(Collectors.toList());

            for (JobMatchResponse match : matchResponses) {
                List<String> matchedKeywords = findMatchedKeywords(match.getDescription(), excludedKeywords);
                match.setMatchedNegativeKeywords(matchedKeywords);
            }
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
     * Skill Train: Get skill train data for a resume
     * GET /api/job-search/resumes/{resumeId}/skill-train
     */
    @Operation(
            summary = "Get skill train data",
            description = "Returns user's skills from resume and top market skills with job counts. " +
                    "Used to populate the skill train visualization showing career paths."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Skill train data retrieved successfully",
                    content = @Content(schema = @Schema(implementation = SkillTrainDto.class))),
            @ApiResponse(responseCode = "500", description = "Failed to fetch skill train data")
    })
    @GetMapping("/resumes/{resumeId}/skill-train")
    public ResponseEntity<SkillTrainDto> getSkillTrain(
            @Parameter(description = "Resume ID") @PathVariable String resumeId) {
        log.info("Fetching skill train data for resume: {}", resumeId);

        try {
            // Step 1: Fetch user's skills from Resume API
            List<String> userSkills = resumeApiClient.fetchResumeSkills(resumeId);
            log.info("Fetched {} user skills from resume {}", userSkills.size(), resumeId);

            // Step 2: Get top 50 in-demand skills from Neo4j
            Map<String, Long> marketSkills = neo4jJobListingService.getTopInDemandSkills(50);

            // Step 3: Build job count map for all skills (user + market)
            Map<String, Long> jobCountBySkill = new LinkedHashMap<>();

            // Add user skills with job counts
            for (String skill : userSkills) {
                Long jobCount = neo4jJobListingService.getJobCountForSkill(skill);
                jobCountBySkill.put(skill, jobCount);
            }

            // Add market skills (may overlap with user skills)
            jobCountBySkill.putAll(marketSkills);

            SkillTrainDto response = SkillTrainDto.builder()
                    .userSkills(userSkills)
                    .marketSkills(marketSkills)
                    .jobCountBySkill(jobCountBySkill)
                    .build();

            log.info("Skill train data ready: {} user skills, {} market skills",
                    userSkills.size(), marketSkills.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch skill train data for resume {}", resumeId, e);
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * Skill Train: Get skill path exploration data
     * POST /api/job-search/skill-train/path
     */
    @Operation(
            summary = "Explore skill path",
            description = "Finds jobs matching ALL selected skills (AND logic) and returns related skills. " +
                    "Used for interactive skill train path exploration."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Skill path data retrieved successfully",
                    content = @Content(schema = @Schema(implementation = SkillPathResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Failed to fetch skill path data")
    })
    @PostMapping("/skill-train/path")
    public ResponseEntity<SkillPathResponse> exploreSkillPath(
            @Valid @RequestBody SkillPathRequest request) {
        log.info("Exploring skill path for: {}", request.getSkills());

        try {
            // Step 1: Get jobs matching ALL skills (AND logic)
            Map<String, Object> jobData = neo4jJobListingService.getJobsBySkills(request.getSkills());
            @SuppressWarnings("unchecked")
            List<String> jobIds = (List<String>) jobData.get("jobIds");

            // Step 2: Get related skills (next stations on the train)
            int maxRelated = request.getMaxRelatedSkills() != null ? request.getMaxRelatedSkills() : 20;
            Map<String, Long> relatedSkills = neo4jJobListingService.getRelatedSkills(
                    request.getSkills(), maxRelated);

            SkillPathResponse response = SkillPathResponse.builder()
                    .selectedSkills(request.getSkills())
                    .jobCount(jobIds.size())
                    .relatedSkills(relatedSkills)
                    .build();

            log.info("Skill path exploration complete: {} jobs, {} related skills",
                    jobIds.size(), relatedSkills.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to explore skill path for: {}", request.getSkills(), e);
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * Skill Train: Get skill path matrix (user skills × market gap skills)
     * POST /api/job-search/skill-train/matrix
     */
    @Operation(
            summary = "Get skill path matrix",
            description = "Returns a matrix showing co-occurrence between user's skills (rows) and top market gap skills (columns). " +
                    "Used for visual career path exploration showing which skill combinations are valuable."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Matrix data retrieved successfully",
                    content = @Content(schema = @Schema(implementation = com.resumebuddy.jobsearch.dto.SkillPathMatrixResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Failed to build matrix")
    })
    @PostMapping("/skill-train/matrix")
    public ResponseEntity<com.resumebuddy.jobsearch.dto.SkillPathMatrixResponse> getSkillPathMatrix(
            @Valid @RequestBody SkillPathMatrixRequest request) {
        log.info("Building skill path matrix for {} user skills, top {} market skills",
                request.getUserSkills().size(), request.getTopMarketSkills());

        try {
            Map<String, Object> matrixData = neo4jJobListingService.getSkillPathMatrix(
                    request.getUserSkills(),
                    request.getTopMarketSkills() != null ? request.getTopMarketSkills() : 15
            );

            @SuppressWarnings("unchecked")
            List<String> userSkills = (List<String>) matrixData.get("userSkills");
            @SuppressWarnings("unchecked")
            List<String> marketSkills = (List<String>) matrixData.get("marketSkills");
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Long>> cooccurrenceMatrix =
                    (Map<String, Map<String, Long>>) matrixData.get("cooccurrenceMatrix");
            Long maxCooccurrence = (Long) matrixData.get("maxCooccurrence");

            com.resumebuddy.jobsearch.dto.SkillPathMatrixResponse response =
                    com.resumebuddy.jobsearch.dto.SkillPathMatrixResponse.builder()
                            .userSkills(userSkills)
                            .marketSkills(marketSkills)
                            .cooccurrenceMatrix(cooccurrenceMatrix)
                            .maxCooccurrence(maxCooccurrence)
                            .build();

            log.info("Skill path matrix built: {}×{} matrix, max co-occurrence: {}",
                    userSkills.size(), marketSkills.size(), maxCooccurrence);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to build skill path matrix", e);
            return ResponseEntity.status(500).body(null);
        }
    }

    // Request DTO for skill path matrix
    public static class SkillPathMatrixRequest {
        private List<String> userSkills;
        private Integer topMarketSkills; // Default: 15

        public List<String> getUserSkills() {
            return userSkills;
        }

        public void setUserSkills(List<String> userSkills) {
            this.userSkills = userSkills;
        }

        public Integer getTopMarketSkills() {
            return topMarketSkills;
        }

        public void setTopMarketSkills(Integer topMarketSkills) {
            this.topMarketSkills = topMarketSkills;
        }
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
     * Skill Drilldown: Get top skills grouped by category
     * GET /api/job-search/skills/top-by-category?perCategory=10
     */
    @Operation(
            summary = "Get top skills by category",
            description = "Returns top N skills per category from Neo4j graph. " +
                    "Skills are automatically categorized into both technical and non-technical categories: " +
                    "Programming, Frameworks & Tools, Cloud & Infrastructure, Data & AI, " +
                    "Leadership & Management, Business & Strategy, Communication & Soft Skills, Domain Expertise, and Other. " +
                    "Used for organized skill tag cloud display suitable for all job types."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Skills retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to fetch skills")
    })
    @GetMapping("/skills/top-by-category")
    public ResponseEntity<Map<String, List<SkillCooccurrence>>> getTopSkillsByCategory(
            @Parameter(description = "Number of top skills per category") @RequestParam(defaultValue = "10") int perCategory) {
        log.info("Fetching top {} skills per category", perCategory);

        try {
            Map<String, Long> allSkills = neo4jJobListingService.getTopInDemandSkills(200); // Fetch more to categorize

            // Categorize skills using inclusive keyword matching (technical + non-technical)
            Map<String, List<SkillCooccurrence>> categorized = new LinkedHashMap<>();
            // Technical categories
            categorized.put("Programming", new ArrayList<>());
            categorized.put("Frameworks & Tools", new ArrayList<>());
            categorized.put("Cloud & Infrastructure", new ArrayList<>());
            categorized.put("Data & AI", new ArrayList<>());
            // Non-technical categories
            categorized.put("Leadership & Management", new ArrayList<>());
            categorized.put("Business & Strategy", new ArrayList<>());
            categorized.put("Communication & Soft Skills", new ArrayList<>());
            categorized.put("Domain Expertise", new ArrayList<>());
            categorized.put("Other", new ArrayList<>());

            for (Map.Entry<String, Long> entry : allSkills.entrySet()) {
                String skill = entry.getKey().toLowerCase();
                SkillCooccurrence skillData = new SkillCooccurrence(entry.getKey(), entry.getValue());

                // Technical categories
                if (skill.matches(".*(java|python|javascript|typescript|go|rust|c\\+\\+|c#|kotlin|swift|ruby|php|scala|programming|coding|development).*")) {
                    categorized.get("Programming").add(skillData);
                } else if (skill.matches(".*(spring|react|angular|vue|django|flask|express|nestjs|laravel|rails|framework|library|sdk|api|rest|graphql).*")) {
                    categorized.get("Frameworks & Tools").add(skillData);
                } else if (skill.matches(".*(aws|azure|gcp|cloud|kubernetes|docker|terraform|jenkins|gitlab|ci/cd|devops|ansible|infrastructure|deployment).*")) {
                    categorized.get("Cloud & Infrastructure").add(skillData);
                } else if (skill.matches(".*(ai|machine learning|ml|deep learning|tensorflow|pytorch|nlp|data science|analytics|sql|database|mysql|postgresql|mongodb|redis|big data|tableau|power bi).*")) {
                    categorized.get("Data & AI").add(skillData);
                // Non-technical categories
                } else if (skill.matches(".*(leadership|management|team lead|project management|agile|scrum|stakeholder|mentor|coaching|people management).*")) {
                    categorized.get("Leadership & Management").add(skillData);
                } else if (skill.matches(".*(strategy|business|planning|analysis|product|marketing|sales|finance|accounting|operations|consulting|negotiation).*")) {
                    categorized.get("Business & Strategy").add(skillData);
                } else if (skill.matches(".*(communication|presentation|writing|documentation|collaboration|teamwork|problem solving|critical thinking|creativity).*")) {
                    categorized.get("Communication & Soft Skills").add(skillData);
                } else if (skill.matches(".*(healthcare|medical|legal|education|retail|manufacturing|logistics|supply chain|engineering|design|architecture).*")) {
                    categorized.get("Domain Expertise").add(skillData);
                } else {
                    categorized.get("Other").add(skillData);
                }
            }

            // Limit each category to perCategory skills
            Map<String, List<SkillCooccurrence>> result = new LinkedHashMap<>();
            for (Map.Entry<String, List<SkillCooccurrence>> entry : categorized.entrySet()) {
                List<SkillCooccurrence> limited = entry.getValue().stream()
                        .sorted((s1, s2) -> Long.compare(s2.getJobCount(), s1.getJobCount()))
                        .limit(perCategory)
                        .collect(Collectors.toList());

                if (!limited.isEmpty()) {
                    result.put(entry.getKey(), limited);
                }
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to fetch categorized skills", e);
            return ResponseEntity.status(500).body(new LinkedHashMap<>());
        }
    }

    /**
     * Skill Heatmap: Get skill co-occurrence matrix
     * GET /api/job-search/skills/heatmap?topN=20
     */
    @Operation(
            summary = "Get skill co-occurrence heatmap",
            description = "Returns a matrix showing how often pairs of top skills appear together in jobs. " +
                    "Used for heatmap visualization showing skill pair demand in the job market."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Heatmap data retrieved successfully",
                    content = @Content(schema = @Schema(implementation = com.resumebuddy.jobsearch.dto.SkillHeatmapResponse.class))),
            @ApiResponse(responseCode = "500", description = "Failed to build heatmap")
    })
    @GetMapping("/skills/heatmap")
    public ResponseEntity<com.resumebuddy.jobsearch.dto.SkillHeatmapResponse> getSkillHeatmap(
            @Parameter(description = "Number of top skills to include in matrix (default: 20)")
            @RequestParam(defaultValue = "20") int topN) {
        log.info("Building skill co-occurrence heatmap for top {} skills", topN);

        try {
            Map<String, Object> matrixData = neo4jJobListingService.getSkillCooccurrenceMatrix(topN);

            @SuppressWarnings("unchecked")
            List<String> skillNames = (List<String>) matrixData.get("skillNames");
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Long>> cooccurrenceMatrix =
                    (Map<String, Map<String, Long>>) matrixData.get("cooccurrenceMatrix");
            Long maxCooccurrence = (Long) matrixData.get("maxCooccurrence");
            Long totalJobs = (Long) matrixData.get("totalJobs");

            com.resumebuddy.jobsearch.dto.SkillHeatmapResponse response =
                    com.resumebuddy.jobsearch.dto.SkillHeatmapResponse.builder()
                            .skillNames(skillNames)
                            .cooccurrenceMatrix(cooccurrenceMatrix)
                            .maxCooccurrence(maxCooccurrence)
                            .totalJobs(totalJobs)
                            .build();

            log.info("Heatmap built: {}x{} matrix, max co-occurrence: {}",
                    skillNames.size(), skillNames.size(), maxCooccurrence);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to build skill heatmap", e);
            return ResponseEntity.status(500).body(null);
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

    /**
     * Get recent crawl activity history
     * GET /api/job-search/crawl/history
     */
    @Operation(
            summary = "Get recent crawl activity history",
            description = "Returns the most recent job crawling activities for user visibility. " +
                    "Shows when job boards were last updated and how many jobs were fetched/saved. " +
                    "Public endpoint for transparency - no authentication required."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "History retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to retrieve history")
    })
    @GetMapping("/crawl/history")
    public ResponseEntity<List<com.resumebuddy.jobsearch.domain.CrawlActivityLog>> getCrawlHistory(
            @Parameter(description = "Number of recent activities to return (default: 5)")
            @RequestParam(defaultValue = "5") int limit) {

        log.info("Fetching crawl activity history (limit: {})", limit);

        try {
            // Use Pageable to control the limit
            List<com.resumebuddy.jobsearch.domain.CrawlActivityLog> history = crawlActivityLogRepository.findAllByOrderByCrawledAtDesc(
                    org.springframework.data.domain.PageRequest.of(0, limit)
            );
            log.info("Retrieved {} crawl activity records", history.size());
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Failed to fetch crawl activity history", e);
            return ResponseEntity.status(500).body(new ArrayList<>());
        }
    }
}
