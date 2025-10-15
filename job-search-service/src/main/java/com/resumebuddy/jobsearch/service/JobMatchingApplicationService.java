package com.resumebuddy.jobsearch.service;

import com.resumebuddy.jobsearch.domain.JobListing;
import com.resumebuddy.jobsearch.domain.JobListingLine;
import com.resumebuddy.jobsearch.domain.JobMatch;
import com.resumebuddy.jobsearch.domain.JobSearchProfile;
import com.resumebuddy.jobsearch.domain.JobSearchProfileLine;
import com.resumebuddy.jobsearch.domain.JobSearchProfileSkill;
import com.resumebuddy.jobsearch.domain.SkillGap;
import com.resumebuddy.jobsearch.repository.JobListingLineRepository;
import com.resumebuddy.jobsearch.repository.JobListingRepository;
import com.resumebuddy.jobsearch.repository.JobMatchRepository;
import com.resumebuddy.jobsearch.repository.JobSearchProfileLineRepository;
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
    private final JobSearchProfileLineRepository profileLineRepository;
    private final JobListingRepository listingRepository;
    private final JobListingLineRepository listingLineRepository;
    private final JobMatchRepository matchRepository;
    private final RedisVectorService redisVectorService;
    private final SkillMatcher skillMatcher;

    /**
     * Search for matching jobs using 2-stage pipeline:
     * Stage 1: Line-by-line vector matching (primary scoring)
     * Stage 2: Skill keyword matching (validation)
     */
    @Transactional
    public List<JobMatch> searchMatchingJobs(String profileId, int topK) {
        try {
            log.info("=== Starting 2-stage line-by-line job matching for profile: {} (topK: {}) ===", profileId, topK);

            // 1. Get profile
            JobSearchProfile profile = profileRepository.findById(profileId)
                    .orElseThrow(() -> new RuntimeException("Profile not found: " + profileId));

            // Get profile lines
            List<JobSearchProfileLine> profileLines = profileLineRepository.findByProfileIdOrderByLineNumber(profileId);
            log.info("Profile has {} lines for matching", profileLines.size());

            if (profileLines.isEmpty()) {
                log.error("No profile lines found for profile: {}", profileId);
                throw new RuntimeException("Profile has no lines - cannot perform line-by-line matching");
            }

            // Get profile skills for stage 2 (with proficiency scores)
            List<JobSearchProfileSkill> profileSkills = profileSkillRepository.findByProfileId(profileId);
            Set<String> candidateSkills = profileSkills.stream()
                    .map(JobSearchProfileSkill::getSkillName)
                    .collect(java.util.stream.Collectors.toSet());

            // Build proficiency map for weighted matching
            java.util.Map<String, Integer> skillProficiencies = new java.util.HashMap<>();
            for (JobSearchProfileSkill skill : profileSkills) {
                skillProficiencies.put(skill.getSkillName(), skill.getProficiencyScore());
            }

            // STAGE 1: Line-by-line vector matching across ALL jobs
            log.info("STAGE 1: Line-by-line vector search across all job listings");
            java.util.Map<String, java.util.List<Double>> listingScores = new java.util.HashMap<>();
            java.util.Map<String, String> bestMatchContext = new java.util.HashMap<>(); // Track best match per listing

            // For each profile line, search for best matching job lines
            for (JobSearchProfileLine profileLine : profileLines) {
                try {
                    if (profileLine.getRedisVectorKey() == null) {
                        log.warn("Profile line {} has no redis vector key", profileLine.getId());
                        continue;
                    }

                    // Search for top-K similar job lines across ALL jobs
                    List<RedisVectorService.VectorSearchResult> lineMatches =
                            redisVectorService.vectorSearch(profileLine.getRedisVectorKey(), topK * 2);

                    String profileLinePreview = profileLine.getLineContent().substring(0, Math.min(80, profileLine.getLineContent().length()));
                    log.info("Profile line #{}: \"{}...\" found {} matches",
                            profileLine.getLineNumber(),
                            profileLinePreview,
                            lineMatches.size());

                    // Group matches by listing ID and track best match text
                    for (RedisVectorService.VectorSearchResult lineMatch : lineMatches) {
                        String matchedLineKey = lineMatch.getKey();

                        // Extract line ID from key format: "listing:line:{uuid}"
                        if (!matchedLineKey.startsWith("listing:line:")) {
                            continue;
                        }

                        String lineId = matchedLineKey.replace("listing:line:", "");

                        // Find which listing this line belongs to
                        JobListingLine jobLine = listingLineRepository.findById(lineId).orElse(null);
                        if (jobLine != null) {
                            String listingId = jobLine.getListingId();
                            double score = lineMatch.getScore();

                            listingScores.computeIfAbsent(listingId, k -> new ArrayList<>())
                                    .add(score);

                            // Track best matching line text for this listing
                            String currentBestKey = listingId + ":best";
                            if (!bestMatchContext.containsKey(currentBestKey)) {
                                String jobLinePreview = jobLine.getLineContent().substring(0, Math.min(80, jobLine.getLineContent().length()));
                                bestMatchContext.put(currentBestKey,
                                    String.format("  ↳ BEST MATCH [%.3f]: \"%s...\"", score, jobLinePreview));
                            }

                            // Log top 3 matches for this profile line
                            if (lineMatches.indexOf(lineMatch) < 3) {
                                String jobLinePreview = jobLine.getLineContent().substring(0, Math.min(80, jobLine.getLineContent().length()));
                                log.info("    → Match #{} [score={}]: \"{}...\"",
                                        lineMatches.indexOf(lineMatch) + 1,
                                        String.format("%.3f", score),
                                        jobLinePreview);
                            }
                        }
                    }

                } catch (Exception e) {
                    log.error("Failed to match profile line: {}", profileLine.getId(), e);
                }
            }

            log.info("STAGE 1 complete: Found {} candidate listings with line matches", listingScores.size());

            // Aggregate scores per listing and rank
            List<ListingScore> rankedListings = new ArrayList<>();
            for (java.util.Map.Entry<String, java.util.List<Double>> entry : listingScores.entrySet()) {
                String listingId = entry.getKey();
                List<Double> scores = entry.getValue();

                // Aggregate: Use max score (best matching line)
                double maxScore = scores.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

                // Alternative: Average of top-3 scores
                double avgScore = scores.stream()
                        .sorted(java.util.Comparator.reverseOrder())
                        .limit(3)
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);

                // Use max score as primary, with match count as tiebreaker
                rankedListings.add(new ListingScore(listingId, maxScore, scores.size()));
            }

            // Sort by score descending, take top-K
            rankedListings.sort((a, b) -> {
                int scoreCompare = Double.compare(b.score, a.score);
                if (scoreCompare != 0) return scoreCompare;
                return Integer.compare(b.matchCount, a.matchCount); // Tiebreaker: more matches
            });

            List<ListingScore> topKListings = rankedListings.stream()
                    .limit(topK)
                    .collect(java.util.stream.Collectors.toList());

            log.info("Top {} listings selected for final processing", topKListings.size());

            // Delete old matches for this profile
            matchRepository.deleteByProfileId(profileId);

            // STAGE 2: Process top-K with skill validation
            List<JobMatch> matches = new ArrayList<>();
            for (ListingScore listingScore : topKListings) {
                try {
                    // Fetch listing from MySQL
                    JobListing listing = listingRepository.findById(listingScore.listingId).orElse(null);
                    if (listing == null) {
                        log.warn("Listing not found in MySQL: {}", listingScore.listingId);
                        continue;
                    }

                    // STAGE 2: Skill keyword matching with proficiency weighting
                    SkillGap skillGap = skillMatcher.analyzeSkillGapWithProficiency(
                            skillProficiencies,
                            listing.getDescription()
                    );

                    // Calculate combined score for logging context
                    double vectorScore = listingScore.score;
                    double weightedSkillScore = skillGap.getWeightedScore();
                    double combinedScore = (vectorScore * 0.6) + (weightedSkillScore / 100.0 * 0.4);

                    log.info("MATCH - {}: VectorScore={} (60%), WeightedSkillScore={}% (40%), CombinedScore={}, LineMatches={}, Skills={}/{}",
                            listing.getTitle(),
                            String.format("%.3f", vectorScore),
                            String.format("%.1f", weightedSkillScore),
                            String.format("%.3f", combinedScore),
                            listingScore.matchCount,
                            skillGap.getMatchedSkills().size(),
                            candidateSkills.size());

                    // Create JobMatch
                    JobMatch match = new JobMatch();
                    match.setProfileId(profileId);
                    match.setListingId(listingScore.listingId);
                    match.setSimilarityScore(BigDecimal.valueOf(listingScore.score));
                    match.setSkillGaps(skillMatcher.skillGapToJson(skillGap));

                    match = matchRepository.save(match);
                    matches.add(match);

                } catch (Exception e) {
                    log.error("Failed to process job match", e);
                }
            }

            // STAGE 3: Re-rank by combining vector similarity + weighted skill score
            log.info("STAGE 3: Re-ranking {} matches by combined score (vector + weighted skills)", matches.size());
            matches.sort((a, b) -> {
                SkillGap gapA = skillMatcher.jsonToSkillGap(a.getSkillGaps());
                SkillGap gapB = skillMatcher.jsonToSkillGap(b.getSkillGaps());

                // Combined score: 60% vector similarity + 40% weighted skill score
                double scoreA = (a.getSimilarityScore().doubleValue() * 0.6) + (gapA.getWeightedScore() / 100.0 * 0.4);
                double scoreB = (b.getSimilarityScore().doubleValue() * 0.6) + (gapB.getWeightedScore() / 100.0 * 0.4);

                return Double.compare(scoreB, scoreA); // Descending
            });

            // Update similarity scores with combined score for better sorting in API
            for (int i = 0; i < matches.size(); i++) {
                JobMatch match = matches.get(i);
                SkillGap gap = skillMatcher.jsonToSkillGap(match.getSkillGaps());
                double vectorScore = match.getSimilarityScore().doubleValue();
                double combinedScore = (vectorScore * 0.6) + (gap.getWeightedScore() / 100.0 * 0.4);
                match.setSimilarityScore(BigDecimal.valueOf(combinedScore));
                matchRepository.save(match);
            }

            log.info("=== Matching complete: Created {} job matches (re-ranked by combined score) ===", matches.size());
            return matches;

        } catch (Exception e) {
            log.error("Failed to search matching jobs", e);
            throw new RuntimeException("Failed to search matching jobs", e);
        }
    }

    /**
     * Helper class to track listing scores
     */
    private static class ListingScore {
        final String listingId;
        final double score;
        final int matchCount;

        ListingScore(String listingId, double score, int matchCount) {
            this.listingId = listingId;
            this.score = score;
            this.matchCount = matchCount;
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
     * Get or create matching results for a profile
     * If matches don't exist, performs the search and saves results
     * If matches exist, returns cached results
     *
     * @param profileId Profile ID
     * @param topK Number of top matches
     * @param forceRefresh If true, always perform fresh search (ignore cache)
     */
    @Transactional
    public List<JobMatchWithListing> getOrCreateMatchingResults(String profileId, int topK, boolean forceRefresh) {
        try {
            log.info("Getting or creating matching results for profile: {} (topK: {}, forceRefresh: {})",
                    profileId, topK, forceRefresh);

            // Check if matches already exist
            List<JobMatch> existingMatches = matchRepository.findByProfileIdOrderBySimilarityScoreDesc(profileId);

            // Force refresh OR no matches exist OR topK changed significantly
            if (forceRefresh || existingMatches.isEmpty() || Math.abs(existingMatches.size() - topK) > 5) {
                log.info("Performing FRESH search (forceRefresh={}, existing={}, topK={})",
                        forceRefresh, existingMatches.size(), topK);
                searchMatchingJobs(profileId, topK);
            } else {
                log.info("Using cached matches: {} results", existingMatches.size());
            }

            // Return enriched matches
            return getMatchesWithListings(profileId);

        } catch (Exception e) {
            log.error("Failed to get or create matching results", e);
            throw new RuntimeException("Failed to get matching results", e);
        }
    }

    /**
     * Backwards compatibility: default to NOT forcing refresh
     */
    @Transactional
    public List<JobMatchWithListing> getOrCreateMatchingResults(String profileId, int topK) {
        return getOrCreateMatchingResults(profileId, topK, false);
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
