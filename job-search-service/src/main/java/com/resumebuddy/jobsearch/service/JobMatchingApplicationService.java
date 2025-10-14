package com.resumebuddy.jobsearch.service;

import com.resumebuddy.jobsearch.domain.JobListing;
import com.resumebuddy.jobsearch.domain.JobMatch;
import com.resumebuddy.jobsearch.domain.JobSearchProfile;
import com.resumebuddy.jobsearch.domain.JobSearchProfileSkill;
import com.resumebuddy.jobsearch.domain.SkillGap;
import com.resumebuddy.jobsearch.repository.JobListingRepository;
import com.resumebuddy.jobsearch.repository.JobMatchRepository;
import com.resumebuddy.jobsearch.repository.JobSearchProfileRepository;
import com.resumebuddy.jobsearch.repository.JobSearchProfileSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Application Service: Job Matching
 * Performs vector search and skill gap analysis
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobMatchingApplicationService {

    private final JobSearchProfileRepository profileRepository;
    private final JobSearchProfileSkillRepository profileSkillRepository;
    private final JobListingRepository listingRepository;
    private final JobMatchRepository matchRepository;
    private final RedisVectorService redisVectorService;
    private final SkillMatcher skillMatcher;

    /**
     * Search for matching jobs using vector similarity
     */
    @Transactional
    public List<JobMatch> searchMatchingJobs(String profileId, int topK) {
        try {
            log.info("Searching for top {} matching jobs for profile: {}", topK, profileId);

            // 1. Get profile
            JobSearchProfile profile = profileRepository.findById(profileId)
                    .orElseThrow(() -> new RuntimeException("Profile not found: " + profileId));

            // 2. Perform vector search in Redis
            List<RedisVectorService.VectorSearchResult> searchResults =
                    redisVectorService.vectorSearch(profile.getRedisVectorKey(), topK);

            log.info("Found {} vector search results", searchResults.size());

            // 3. Delete old matches for this profile
            matchRepository.deleteByProfileId(profileId);

            // 4. Create JobMatch entities with skill gap analysis
            List<JobMatch> matches = new ArrayList<>();
            for (RedisVectorService.VectorSearchResult result : searchResults) {
                // Extract listing ID from Redis key (format: "listing:vector:{id}")
                String listingId = result.getKey().replace("listing:vector:", "");

                // Fetch listing from MySQL
                JobListing listing = listingRepository.findById(listingId).orElse(null);
                if (listing == null) {
                    log.warn("Listing not found in MySQL: {}", listingId);
                    continue;
                }

                // Analyze skill gap - get skills from job_search_profile_skill table
                List<JobSearchProfileSkill> profileSkills = profileSkillRepository.findByProfileId(profileId);
                Set<String> candidateSkills = profileSkills.stream()
                        .map(JobSearchProfileSkill::getSkillName)
                        .collect(java.util.stream.Collectors.toSet());
                Set<String> jobSkills = skillMatcher.extractJobSkillsFromJson(listing.getRequiredSkills());
                SkillGap skillGap = skillMatcher.analyzeSkillGap(candidateSkills, jobSkills);

                // Create JobMatch
                JobMatch match = new JobMatch();
                match.setProfileId(profileId);
                match.setListingId(listingId);
                match.setSimilarityScore(BigDecimal.valueOf(result.getScore()));
                match.setSkillGaps(skillMatcher.skillGapToJson(skillGap));

                match = matchRepository.save(match);
                matches.add(match);
            }

            log.info("Created {} job matches", matches.size());
            return matches;

        } catch (Exception e) {
            log.error("Failed to search matching jobs", e);
            throw new RuntimeException("Failed to search matching jobs", e);
        }
    }

    /**
     * Get matches for a profile
     */
    public List<JobMatch> getMatches(String profileId) {
        return matchRepository.findByProfileIdOrderBySimilarityScoreDesc(profileId);
    }

    /**
     * Get matches with enriched job listing data
     */
    public List<JobMatchWithListing> getMatchesWithListings(String profileId) {
        List<JobMatch> matches = matchRepository.findByProfileIdOrderBySimilarityScoreDesc(profileId);
        List<JobMatchWithListing> enrichedMatches = new ArrayList<>();

        for (JobMatch match : matches) {
            JobListing listing = listingRepository.findById(match.getListingId()).orElse(null);
            if (listing != null) {
                SkillGap skillGap = skillMatcher.jsonToSkillGap(match.getSkillGaps());
                enrichedMatches.add(new JobMatchWithListing(match, listing, skillGap));
            }
        }

        return enrichedMatches;
    }

    /**
     * DTO for enriched match data
     */
    public static class JobMatchWithListing {
        private final JobMatch match;
        private final JobListing listing;
        private final SkillGap skillGap;

        public JobMatchWithListing(JobMatch match, JobListing listing, SkillGap skillGap) {
            this.match = match;
            this.listing = listing;
            this.skillGap = skillGap;
        }

        public JobMatch getMatch() {
            return match;
        }

        public JobListing getListing() {
            return listing;
        }

        public SkillGap getSkillGap() {
            return skillGap;
        }
    }
}
