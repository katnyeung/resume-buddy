package com.resumebuddy.jobsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.jobsearch.dto.ExperienceDto;
import com.resumebuddy.jobsearch.dto.GeneratedJobProfileDto;
import com.resumebuddy.jobsearch.dto.ProfileVersionDto;
import com.resumebuddy.jobsearch.dto.SkillDto;
import com.resumebuddy.jobsearch.domain.JobListing;
import com.resumebuddy.jobsearch.domain.JobMatch;
import com.resumebuddy.jobsearch.domain.JobSearchProfile;
import com.resumebuddy.jobsearch.domain.JobSearchProfileLine;
import com.resumebuddy.jobsearch.domain.JobSearchProfileSkill;
import com.resumebuddy.jobsearch.domain.JobSearchProfileVersion;
import com.resumebuddy.jobsearch.repository.JobListingRepository;
import com.resumebuddy.jobsearch.repository.JobMatchRepository;
import com.resumebuddy.jobsearch.repository.JobSearchProfileRepository;
import com.resumebuddy.jobsearch.repository.JobSearchProfileLineRepository;
import com.resumebuddy.jobsearch.repository.JobSearchProfileSkillRepository;
import com.resumebuddy.jobsearch.repository.JobSearchProfileVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Application Service: Job Search Orchestration
 * Coordinates profile creation from multiple experiences
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobSearchApplicationService {

    private final JobSearchProfileRepository profileRepository;
    private final JobSearchProfileLineRepository profileLineRepository;
    private final JobSearchProfileSkillRepository profileSkillRepository;
    private final JobSearchProfileVersionRepository versionRepository;
    private final JobMatchRepository matchRepository;
    private final JobListingRepository listingRepository;
    private final ResumeApiClient resumeApiClient;
    private final JobPostGenerator jobPostGenerator;
    private final VectorEmbeddingService vectorEmbeddingService;
    private final RedisVectorService redisVectorService;
    private final ObjectMapper objectMapper;

    /**
     * Create job search profile from selected experiences
     * Cleans up any existing profile for this resume before creating new one
     */
    @Transactional
    public JobSearchProfile createProfile(String userId, String resumeId, List<String> experienceIds, List<String> excludedKeywords) {
        try {
            log.info("Creating job search profile for user {} with resume {} and {} experiences",
                    userId, resumeId, experienceIds.size());

            // 0. Clean up existing profiles for this resume (if any)
            List<JobSearchProfile> existingProfiles = profileRepository.findByResumeIdOrderByCreatedAtDesc(resumeId);
            if (!existingProfiles.isEmpty()) {
                log.info("Found {} existing profiles for resume {}, cleaning up...", existingProfiles.size(), resumeId);
                for (JobSearchProfile oldProfile : existingProfiles) {
                    deleteProfile(oldProfile.getId());
                }
                log.info("Cleaned up all existing profiles for resume {}", resumeId);
            }

            // 1. Fetch experiences from resume-api
            List<ExperienceDto> experiences = resumeApiClient.fetchExperiences(resumeId, experienceIds);
            log.info("Fetched {} experiences from resume-api", experiences.size());

            // 2. Generate job post with metadata using LLM (includes suggested job title)
            GeneratedJobProfileDto generatedProfile = jobPostGenerator.generateJobPost(experiences);

            // 3. Use LLM-suggested title, or fallback to latest job title
            String suggestedTitle = generatedProfile.getSuggestedJobTitle();
            if (suggestedTitle == null || suggestedTitle.trim().isEmpty()) {
                suggestedTitle = experiences.get(0).getJobTitle(); // Use latest job title as fallback
            }

            log.info("Generated job profile - suggested title: '{}', location: {}, level: {}, post length: {}",
                    suggestedTitle, generatedProfile.getLocation(), generatedProfile.getExperienceLevel(), generatedProfile.getJobPost().length());

            // 4. Generate vector embedding
            float[] embedding = vectorEmbeddingService.generateEmbedding(generatedProfile.getJobPost());
            log.info("Generated vector embedding with dimension: {}", embedding.length);

            // 5. Create profile entity with LLM-generated metadata
            JobSearchProfile profile = new JobSearchProfile();
            profile.setUserId(userId); // Link to authenticated user
            profile.setResumeId(resumeId);
            profile.setSourceExperienceIds(serializeExperienceIds(experienceIds));
            profile.setGeneratedJobPost(generatedProfile.getJobPost());
            profile.setDesiredJobTitle(suggestedTitle); // LLM-suggested or latest job title
            profile.setLocation(generatedProfile.getLocation());
            profile.setExperienceLevel(generatedProfile.getExperienceLevel());

            // Set excluded keywords (comma-separated)
            if (excludedKeywords != null && !excludedKeywords.isEmpty()) {
                profile.setExcludedKeywords(String.join(",", excludedKeywords));
            }

            // 6. Save to MySQL first to get ID
            profile = profileRepository.save(profile);
            log.info("Saved profile to MySQL with ID: {}", profile.getId());

            // 7. Vectorize mock job post lines (NOT user resume lines)
            vectorizeJobPostLines(profile.getId(), generatedProfile.getJobPost());

            // 10. Save skills individually in separate table with LLM proficiency scores
            saveProfileSkills(profile.getId(), experiences, generatedProfile.getSkillProficiencies());

            log.info("Successfully created job search profile: {}", profile.getId());
            return profile;

        } catch (Exception e) {
            log.error("Failed to create job search profile", e);
            throw new RuntimeException("Failed to create job search profile", e);
        }
    }

    /**
     * Update profile's job post (regenerate embedding and re-vectorize lines)
     */
    @Transactional
    public JobSearchProfile updateJobPost(String profileId, String editedJobPost, List<String> excludedKeywords) {
        try {
            log.info("Updating job post for profile: {}", profileId);

            JobSearchProfile profile = profileRepository.findById(profileId)
                    .orElseThrow(() -> new RuntimeException("Profile not found: " + profileId));

            // 1. Delete old line vectors from Redis and MySQL
            deleteProfileLines(profileId);

            // 2. Update profile with new job post and excluded keywords
            profile.setGeneratedJobPost(editedJobPost);

            // Update excluded keywords (comma-separated)
            if (excludedKeywords != null && !excludedKeywords.isEmpty()) {
                profile.setExcludedKeywords(String.join(",", excludedKeywords));
            } else {
                profile.setExcludedKeywords(null);
            }

            profile = profileRepository.save(profile);

            // 4. Re-vectorize edited job post lines
            vectorizeJobPostLines(profileId, editedJobPost);

            // 5. Update the active version if versioning is in use
            updateActiveVersion(profileId, editedJobPost);

            log.info("Successfully updated profile and re-vectorized lines: {}", profileId);
            return profile;

        } catch (Exception e) {
            log.error("Failed to update profile", e);
            throw new RuntimeException("Failed to update profile", e);
        }
    }

    /**
     * Get profile by ID
     */
    public JobSearchProfile getProfile(String profileId) {
        return profileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("Profile not found: " + profileId));
    }

    /**
     * Get all profiles for a resume (filtered by user)
     */
    public List<JobSearchProfile> getProfilesByResume(String resumeId) {
        return profileRepository.findByResumeIdOrderByCreatedAtDesc(resumeId);
    }

    /**
     * Get all profiles for a resume and user (security filtered)
     */
    public List<JobSearchProfile> getProfilesByResumeAndUser(String resumeId, String userId) {
        return profileRepository.findByResumeIdAndUserIdOrderByCreatedAtDesc(resumeId, userId);
    }

    /**
     * Get all lines for a profile (for single source of truth display)
     */
    public List<JobSearchProfileLine> getProfileLines(String profileId) {
        return profileLineRepository.findByProfileId(profileId);
    }

    /**
     * Get all skills for a profile
     */
    public List<JobSearchProfileSkill> getProfileSkills(String profileId) {
        return profileSkillRepository.findByProfileId(profileId);
    }

    /**
     * Add a skill to a profile
     */
    @Transactional
    public JobSearchProfileSkill addProfileSkill(String profileId, String skillName, String skillCategory, Integer proficiencyScore) {
        // Check if skill already exists
        if (profileSkillRepository.existsByProfileIdAndSkillName(profileId, skillName)) {
            throw new RuntimeException("Skill already exists: " + skillName);
        }

        JobSearchProfileSkill skill = new JobSearchProfileSkill();
        skill.setProfileId(profileId);
        skill.setSkillName(skillName);
        skill.setSkillCategory(skillCategory);
        skill.setProficiencyScore(proficiencyScore != null ? proficiencyScore : 50); // Default to 50 if not provided

        return profileSkillRepository.save(skill);
    }

    /**
     * Remove a skill from a profile
     */
    @Transactional
    public void removeProfileSkill(String skillId) {
        profileSkillRepository.deleteById(skillId);
    }

    /**
     * Update skill proficiency score
     */
    @Transactional
    public JobSearchProfileSkill updateSkillProficiency(String skillId, Integer proficiencyScore) {
        JobSearchProfileSkill skill = profileSkillRepository.findById(skillId)
                .orElseThrow(() -> new RuntimeException("Skill not found: " + skillId));

        skill.setProficiencyScore(proficiencyScore);
        return profileSkillRepository.save(skill);
    }

    /**
     * Update profile location
     */
    @Transactional
    public JobSearchProfile updateLocation(String profileId, String location) {
        JobSearchProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("Profile not found: " + profileId));

        profile.setLocation(location);
        return profileRepository.save(profile);
    }

    /**
     * Update profile experience level
     */
    @Transactional
    public JobSearchProfile updateExperienceLevel(String profileId, String experienceLevel) {
        JobSearchProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("Profile not found: " + profileId));

        profile.setExperienceLevel(experienceLevel);
        return profileRepository.save(profile);
    }

    /**
     * Update profile metadata (location, experience level, and desired job title together)
     */
    @Transactional
    public JobSearchProfile updateProfileMetadata(String profileId, String location, String experienceLevel, String desiredJobTitle) {
        JobSearchProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("Profile not found: " + profileId));

        if (location != null) {
            profile.setLocation(location);
        }
        if (experienceLevel != null) {
            profile.setExperienceLevel(experienceLevel);
        }
        if (desiredJobTitle != null) {
            profile.setDesiredJobTitle(desiredJobTitle);
        }
        return profileRepository.save(profile);
    }

    /**
     * Record profile visit (updates last_visit_date to current timestamp)
     * Called when user visits job-search page or clicks "Refresh Results"
     * Used to determine active profiles for LLM keyword generation
     */
    @Transactional
    public JobSearchProfile recordVisit(String profileId) {
        JobSearchProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("Profile not found: " + profileId));

        profile.setLastVisitDate(java.time.LocalDateTime.now());
        return profileRepository.save(profile);
    }

    /**
     * Delete profile (including Redis vectors and line vectors)
     */
    @Transactional
    public void deleteProfile(String profileId) {
        JobSearchProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("Profile not found: " + profileId));

        // Delete line-level vectors (mock job post lines)
        deleteProfileLines(profileId);

        // Delete profile skills (CASCADE will handle this, but explicit is better)
        profileSkillRepository.deleteByProfileId(profileId);

        // Delete MySQL profile entity
        profileRepository.delete(profile);

        log.info("Deleted profile: {}", profileId);
    }

    // ==================== VERSION MANAGEMENT ====================

    /**
     * Get all versions for a profile
     * If no versions exist, creates version 1 from current profile (lazy migration)
     */
    @Transactional
    public List<ProfileVersionDto> getVersions(String profileId) {
        JobSearchProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("Profile not found: " + profileId));

        List<JobSearchProfileVersion> versions = versionRepository.findByProfileIdOrderByVersionNumberAsc(profileId);

        // Lazy migration: if no versions exist, create version 1 from current profile
        if (versions.isEmpty()) {
            log.info("No versions found for profile {}, creating version 1 from current profile", profileId);
            JobSearchProfileVersion version1 = new JobSearchProfileVersion();
            version1.setProfileId(profileId);
            version1.setVersionNumber(1);
            version1.setVersionName("Original");
            version1.setGeneratedJobPost(profile.getGeneratedJobPost());
            version1.setSourceType("RESUME");
            version1.setIsActive(true);
            version1 = versionRepository.save(version1);
            versions = List.of(version1);
        }

        return versions.stream()
                .map(this::toVersionDto)
                .collect(Collectors.toList());
    }

    /**
     * Get the count of applied jobs for a profile
     */
    public int getAppliedJobCount(String profileId) {
        return matchRepository.findByProfileIdAndIsAppliedTrue(profileId).size();
    }

    /**
     * Generate new version from applied jobs
     * Requires minimum 3 applied jobs
     */
    @Transactional
    public ProfileVersionDto generateFromAppliedJobs(String profileId, String versionName) {
        try {
            log.info("Generating new version from applied jobs for profile: {}", profileId);

            // 1. Fetch applied jobs for this profile
            List<JobMatch> appliedMatches = matchRepository.findByProfileIdAndIsAppliedTrue(profileId);

            // 2. Validate minimum 3 applied jobs
            if (appliedMatches.size() < 3) {
                throw new IllegalStateException("Minimum 3 applied jobs required. Currently have: " + appliedMatches.size());
            }

            // 3. Fetch full job listing data
            List<String> listingIds = appliedMatches.stream()
                    .map(JobMatch::getListingId)
                    .collect(Collectors.toList());
            List<JobListing> listings = listingRepository.findByIdIn(listingIds);

            // Filter out listings with empty descriptions
            listings = listings.stream()
                    .filter(l -> l.getDescription() != null && !l.getDescription().trim().isEmpty())
                    .collect(Collectors.toList());

            if (listings.size() < 3) {
                throw new IllegalStateException("Not enough applied jobs with descriptions. Need 3+, have: " + listings.size());
            }

            log.info("Found {} applied jobs with descriptions", listings.size());

            // 4. Call LLM to generate aggregated requirements
            String generatedPost = jobPostGenerator.generateFromAppliedJobs(listings);

            // 5. Handle version limit (max 3)
            ensureVersionLimit(profileId);

            // 6. Get next version number
            int nextVersionNumber = versionRepository.getMaxVersionNumber(profileId) + 1;
            if (nextVersionNumber > 3) {
                nextVersionNumber = findAvailableVersionNumber(profileId);
            }

            // 7. Create new version (not active until user saves)
            JobSearchProfileVersion version = new JobSearchProfileVersion();
            version.setProfileId(profileId);
            version.setVersionNumber(nextVersionNumber);
            version.setVersionName(versionName != null ? versionName : "From " + listings.size() + " Applied Jobs");
            version.setGeneratedJobPost(generatedPost);
            version.setSourceType("APPLIED_JOBS");
            version.setSourceJobIds(serializeJobIds(listings.stream().map(JobListing::getId).collect(Collectors.toList())));
            version.setIsActive(false);

            version = versionRepository.save(version);
            log.info("Created new version {} for profile {}", version.getVersionNumber(), profileId);

            return toVersionDto(version);

        } catch (IllegalStateException e) {
            throw e; // Re-throw validation errors
        } catch (Exception e) {
            log.error("Failed to generate version from applied jobs", e);
            throw new RuntimeException("Failed to generate version from applied jobs", e);
        }
    }

    /**
     * Switch to a specific version (loads text into editor, does NOT vectorize)
     * The user must click "Save All Changes" to activate and vectorize
     */
    @Transactional
    public ProfileVersionDto switchVersion(String profileId, Integer versionNumber) {
        JobSearchProfileVersion version = versionRepository.findByProfileIdAndVersionNumber(profileId, versionNumber)
                .orElseThrow(() -> new RuntimeException("Version not found: " + versionNumber));

        // Deactivate all versions first
        versionRepository.deactivateAllVersions(profileId);

        // Activate the selected version
        version.setIsActive(true);
        version = versionRepository.save(version);

        // Update profile's generated job post with this version's content
        JobSearchProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("Profile not found: " + profileId));
        profile.setGeneratedJobPost(version.getGeneratedJobPost());
        profileRepository.save(profile);

        log.info("Switched to version {} for profile {}", versionNumber, profileId);

        // Return with generatedJobPost included for editor
        ProfileVersionDto dto = toVersionDto(version);
        dto.setGeneratedJobPost(version.getGeneratedJobPost());
        return dto;
    }

    /**
     * Delete a version
     * Cannot delete the active version
     */
    @Transactional
    public void deleteVersion(String profileId, Integer versionNumber) {
        JobSearchProfileVersion version = versionRepository.findByProfileIdAndVersionNumber(profileId, versionNumber)
                .orElseThrow(() -> new RuntimeException("Version not found: " + versionNumber));

        if (version.getIsActive()) {
            throw new IllegalStateException("Cannot delete the active version. Switch to another version first.");
        }

        // Check if this is the only version
        int count = versionRepository.countByProfileId(profileId);
        if (count <= 1) {
            throw new IllegalStateException("Cannot delete the only version.");
        }

        versionRepository.delete(version);
        log.info("Deleted version {} for profile {}", versionNumber, profileId);
    }

    /**
     * Update the active version when user saves (called from updateJobPost)
     */
    private void updateActiveVersion(String profileId, String newJobPost) {
        versionRepository.findByProfileIdAndIsActiveTrue(profileId).ifPresent(version -> {
            version.setGeneratedJobPost(newJobPost);
            versionRepository.save(version);
            log.info("Updated active version {} for profile {}", version.getVersionNumber(), profileId);
        });
    }

    // Version helper methods

    private ProfileVersionDto toVersionDto(JobSearchProfileVersion version) {
        return ProfileVersionDto.builder()
                .id(version.getId())
                .profileId(version.getProfileId())
                .versionNumber(version.getVersionNumber())
                .versionName(version.getVersionName())
                .sourceType(version.getSourceType())
                .isActive(version.getIsActive())
                .createdAt(version.getCreatedAt())
                .build();
    }

    private void ensureVersionLimit(String profileId) {
        int count = versionRepository.countByProfileId(profileId);
        if (count >= 3) {
            // Delete oldest non-active version
            List<JobSearchProfileVersion> oldestVersions = versionRepository.findOldestNonActiveVersions(profileId);
            if (!oldestVersions.isEmpty()) {
                JobSearchProfileVersion toDelete = oldestVersions.get(0);
                versionRepository.delete(toDelete);
                log.info("Deleted oldest non-active version {} to make room for new version", toDelete.getVersionNumber());
            } else {
                throw new IllegalStateException("Cannot create new version: all 3 versions are active or protected");
            }
        }
    }

    private int findAvailableVersionNumber(String profileId) {
        List<JobSearchProfileVersion> versions = versionRepository.findByProfileIdOrderByVersionNumberAsc(profileId);
        Set<Integer> usedNumbers = versions.stream()
                .map(JobSearchProfileVersion::getVersionNumber)
                .collect(Collectors.toSet());

        for (int i = 1; i <= 3; i++) {
            if (!usedNumbers.contains(i)) {
                return i;
            }
        }
        return 1; // Fallback
    }

    private String serializeJobIds(List<String> jobIds) {
        try {
            return objectMapper.writeValueAsString(jobIds);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize job IDs", e);
        }
    }

    // ==================== HELPER METHODS ====================

    private String serializeExperienceIds(List<String> experienceIds) {
        try {
            return objectMapper.writeValueAsString(experienceIds);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize experience IDs", e);
        }
    }

    /**
     * Vectorize individual lines from the LLM-generated mock job post using batch embeddings
     * This allows line-by-line matching with actual job postings
     */
    private void vectorizeJobPostLines(String profileId, String generatedJobPost) {
        try {
            log.info("Vectorizing mock job post lines for profile: {}", profileId);

            // Split mock job post into lines and clean them
            String[] rawLines = generatedJobPost.split("\\r?\\n");
            List<String> cleanedLines = new ArrayList<>();
            List<Integer> lineNumbers = new ArrayList<>();

            for (int i = 0; i < rawLines.length; i++) {
                String line = rawLines[i].trim();

                // Skip only empty lines
                if (line.isEmpty()) {
                    continue;
                }

                // Remove bullet point markers (•, -, *, etc.)
                line = line.replaceAll("^[•\\-\\*]+\\s*", "").trim();

                // Skip only empty lines after cleanup
                if (line.isEmpty()) {
                    continue;
                }

                cleanedLines.add(line);
                lineNumbers.add(i);
            }

            if (cleanedLines.isEmpty()) {
                log.warn("No valid lines to vectorize for profile: {}", profileId);
                return;
            }

            // Generate embeddings in batch (single API call)
            log.info("Sending batch embedding request for {} lines", cleanedLines.size());
            List<float[]> embeddings = vectorEmbeddingService.generateEmbeddings(cleanedLines);

            if (embeddings.size() != cleanedLines.size()) {
                throw new RuntimeException("Mismatch between lines and embeddings count");
            }

            // Create and save profile line entities
            List<JobSearchProfileLine> profileLines = new ArrayList<>();
            for (int i = 0; i < cleanedLines.size(); i++) {
                JobSearchProfileLine profileLine = new JobSearchProfileLine();
                profileLine.setProfileId(profileId);
                profileLine.setLineNumber(lineNumbers.get(i));
                profileLine.setLineContent(cleanedLines.get(i));

                // Save first to get ID
                profileLine = profileLineRepository.save(profileLine);

                // Store vector embedding in BOTH Redis AND MySQL
                // This method saves to Redis immediately and updates MySQL with vector_embedding
                redisVectorService.storeProfileLineVector(profileLine.getId(), embeddings.get(i));

                profileLines.add(profileLine);
            }

            log.info("Stored {} profile line vectors in Redis + MySQL for backup", profileLines.size());
            log.info("Vectorized {} mock job post lines for profile: {} using batch API", profileLines.size(), profileId);

        } catch (Exception e) {
            log.error("Failed to vectorize mock job post lines", e);
            // Don't throw - this is optional enhancement
        }
    }

    /**
     * Delete all lines for a profile (used when updating job post)
     */
    private void deleteProfileLines(String profileId) {
        List<JobSearchProfileLine> lines = profileLineRepository.findByProfileId(profileId);
        for (JobSearchProfileLine line : lines) {
            if (line.getRedisVectorKey() != null) {
                redisVectorService.deleteVector(line.getRedisVectorKey());
            }
        }
        profileLineRepository.deleteByProfileId(profileId);
        log.info("Deleted {} profile lines for profile: {}", lines.size(), profileId);
    }

    /**
     * Save skills from experiences as individual rows
     * Only technical tools/technologies for job board matching
     * Uses LLM proficiency scores if available, otherwise calculates
     */
    private void saveProfileSkills(String profileId, List<ExperienceDto> experiences, Map<String, Integer> llmProficiencies) {
        try {
            log.info("Saving skills for profile: {}", profileId);

            List<JobSearchProfileSkill> skills = new ArrayList<>();

            // Extract all unique technical skills from experiences
            Set<String> processedSkills = new java.util.HashSet<>();

            // Soft skill keywords to filter out (case-insensitive)
            Set<String> softSkillKeywords = Set.of(
                "leadership", "communication", "problem solving", "troubleshooting",
                "team collaboration", "collaboration", "teamwork", "mentoring",
                "time management", "project management", "analytical", "critical thinking",
                "attention to detail", "adaptability", "creativity", "interpersonal",
                "organizational", "presentation", "negotiation", "decision making",
                "conflict resolution", "emotional intelligence"
            );

            for (ExperienceDto exp : experiences) {
                for (SkillDto skillDto : exp.getExtractedSkills()) {
                    String skillName = skillDto.getName();
                    String category = skillDto.getCategory() != null ? skillDto.getCategory() : "";

                    // Skip if already processed
                    if (processedSkills.contains(skillName)) {
                        continue;
                    }

                    // Filter 1: Skip soft skills by category
                    if (category.equalsIgnoreCase("Soft Skill") ||
                        category.equalsIgnoreCase("Soft Skills") ||
                        category.toLowerCase().contains("cognitive") ||
                        category.toLowerCase().contains("interpersonal")) {
                        log.debug("Skipping soft skill: {} (category: {})", skillName, category);
                        continue;
                    }

                    // Filter 2: Skip by keyword matching
                    String skillLower = skillName.toLowerCase();
                    boolean isSoftSkill = softSkillKeywords.stream()
                            .anyMatch(keyword -> skillLower.contains(keyword));

                    if (isSoftSkill) {
                        log.debug("Skipping soft skill by keyword: {}", skillName);
                        continue;
                    }

                    // Filter 3: Normalize and shorten overly specific phrases
                    // "CI/CD Pipelines" → "CI/CD"
                    // "API Development" → "API"
                    // "Microservices Architecture" → "Microservices"
                    String normalized = normalizeSkillName(skillName);

                    // Skip if normalized version already exists
                    if (processedSkills.contains(normalized)) {
                        continue;
                    }

                    processedSkills.add(normalized);

                    // Use LLM proficiency score if available, otherwise calculate
                    int proficiencyScore;
                    if (llmProficiencies != null && llmProficiencies.containsKey(normalized)) {
                        proficiencyScore = llmProficiencies.get(normalized);
                        log.debug("Using LLM proficiency for {}: {}", normalized, proficiencyScore);
                    } else if (llmProficiencies != null && llmProficiencies.containsKey(skillName)) {
                        proficiencyScore = llmProficiencies.get(skillName);
                        log.debug("Using LLM proficiency for {} (original name): {}", skillName, proficiencyScore);
                    } else {
                        proficiencyScore = calculateProficiencyScore(skillDto, experiences);
                        log.debug("Calculated proficiency for {}: {}", normalized, proficiencyScore);
                    }

                    JobSearchProfileSkill skill = new JobSearchProfileSkill();
                    skill.setProfileId(profileId);
                    skill.setSkillName(normalized);
                    skill.setSkillCategory(category);
                    skill.setProficiencyScore(proficiencyScore);

                    skills.add(skill);
                }
            }

            // Batch save all skills
            if (!skills.isEmpty()) {
                profileSkillRepository.saveAll(skills);
                log.info("Saved {} technical skills for profile: {} (filtered from resume)", skills.size(), profileId);
            } else {
                log.warn("No technical skills found for profile: {}", profileId);
            }

        } catch (Exception e) {
            log.error("Failed to save profile skills", e);
            // Don't throw - this is not critical
        }
    }

    /**
     * Normalize skill names to common job board keywords
     * Examples:
     * - "CI/CD Pipelines" → "CI/CD"
     * - "API Development" → "API"
     * - "Microservices Architecture" → "Microservices"
     * - "Unit Testing" → "Unit Testing" (keep as-is)
     */
    private String normalizeSkillName(String skillName) {
        // Remove common suffixes that are too specific
        String normalized = skillName
                .replaceAll("(?i)\\s+(pipeline|pipelines)$", "")
                .replaceAll("(?i)\\s+(development|programming)$", "")
                .replaceAll("(?i)\\s+(architecture|design)$", "")
                .replaceAll("(?i)\\s+(solutions|solution)$", "")
                .replaceAll("(?i)\\s+(tools|tool)$", "")
                .replaceAll("(?i)\\s+(platform|platforms)$", "")
                .replaceAll("(?i)\\s+(services|service)$", "")
                .trim();

        // If we stripped everything, return original
        if (normalized.isEmpty() || normalized.length() < 2) {
            return skillName;
        }

        return normalized;
    }

    /**
     * Calculate proficiency score for a skill (0-100)
     * Used to weight skills during job matching
     *
     * Scoring logic:
     * - Base: 50 (default)
     * - isPrimary: +30 (marked as core skill)
     * - Multiple experiences: +5 per experience (up to +20)
     *
     * Examples:
     * - Primary skill in 1 experience: 80
     * - Secondary skill in 3 experiences: 50 + 15 = 65
     * - Primary skill in 4+ experiences: 100
     */
    private int calculateProficiencyScore(SkillDto skillDto, List<ExperienceDto> allExperiences) {
        int score = 50; // Base score

        // Bonus for primary/core skill
        if (skillDto.isPrimary()) {
            score += 30;
        }

        // Count how many experiences mention this skill
        String skillName = skillDto.getName();
        long experienceCount = allExperiences.stream()
                .filter(exp -> exp.getExtractedSkills().stream()
                        .anyMatch(s -> s.getName().equalsIgnoreCase(skillName)))
                .count();

        // Bonus for appearing in multiple experiences (up to +20)
        int experienceBonus = (int) Math.min(experienceCount - 1, 4) * 5;
        score += experienceBonus;

        // Cap at 100
        return Math.min(score, 100);
    }
}
