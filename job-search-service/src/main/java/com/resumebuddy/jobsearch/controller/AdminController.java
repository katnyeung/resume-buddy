package com.resumebuddy.jobsearch.controller;

import com.resumebuddy.jobsearch.application.service.JobCrawlingApplicationService;
import com.resumebuddy.jobsearch.domain.JobListing;
import com.resumebuddy.jobsearch.domain.JobListingLine;
import com.resumebuddy.jobsearch.domain.JobSearchProfile;
import com.resumebuddy.jobsearch.domain.service.JobDescriptionParser;
import com.resumebuddy.jobsearch.domain.service.KeywordGenerationService;
import com.resumebuddy.jobsearch.dto.analysis.JobAnalysisRequest;
import com.resumebuddy.jobsearch.dto.analysis.JobAnalysisResponse;
import com.resumebuddy.jobsearch.dto.crawl.JobCrawlRequest;
import com.resumebuddy.jobsearch.dto.crawl.JobCrawlResponse;
import com.resumebuddy.jobsearch.repository.JobListingLineRepository;
import com.resumebuddy.jobsearch.repository.JobListingRepository;
import com.resumebuddy.jobsearch.repository.JobSearchProfileRepository;
import com.resumebuddy.jobsearch.service.JobAnalysisService;
import com.resumebuddy.jobsearch.service.RedisVectorService;
import com.resumebuddy.jobsearch.service.VectorEmbeddingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * REST Controller: Admin Operations
 * Manual job crawling and administrative tasks
 */
@RestController
@RequestMapping("/api/job-search/admin")
@Slf4j
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Admin", description = "Administrative operations for job crawling and maintenance")
public class AdminController {

    private final JobCrawlingApplicationService jobCrawlingService;
    private final KeywordGenerationService keywordGenerationService;
    private final JobListingRepository jobListingRepository;
    private final JobListingLineRepository jobListingLineRepository;
    private final JobSearchProfileRepository profileRepository;
    private final com.resumebuddy.jobsearch.repository.JobSearchProfileLineRepository profileLineRepository;
    private final RedisVectorService redisVectorService;
    private final VectorEmbeddingService vectorEmbeddingService;
    private final JobDescriptionParser jobDescriptionParser;
    private final JobAnalysisService jobAnalysisService;

    @Value("${app.job-crawling.max-days-old:7}")
    private int maxDaysOld;

    /**
     * Manual job crawl trigger (for testing/debugging)
     * POST /api/job-search/admin/crawl
     */
    @Operation(
            summary = "Trigger manual job crawl",
            description = "Fetches jobs from external APIs (Adzuna, Reed, etc.) and stores them in the database. " +
                    "Used for testing and debugging. Production crawls use scheduled tasks."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Crawl completed successfully",
                    content = @Content(schema = @Schema(implementation = JobCrawlResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "500", description = "Crawl failed")
    })
    @PostMapping("/crawl")
    public ResponseEntity<JobCrawlResponse> triggerCrawl(@Valid @RequestBody JobCrawlRequest request) {
        log.info("Manual crawl triggered - source: {}, keywords: {}, location: {}",
                request.getSource(), request.getKeywords(), request.getLocation());

        JobCrawlResponse response = jobCrawlingService.crawlJobs(request);

        return ResponseEntity.ok(response);
    }

    /**
     * Simulate scheduled crawl with LLM-generated keywords
     * POST /api/job-search/admin/crawl/scheduled-simulation
     */
    @Operation(
            summary = "Simulate scheduled crawl (User-driven LLM keywords)",
            description = "Simulates the automated scheduled crawl flow: " +
                    "1) Analyzes ACTIVE user profiles (visited in last 7 days). " +
                    "2) Uses Grok LLM to generate COMPLETE job titles based on desired titles + top skills by proficiency. " +
                    "3) Crawls jobs for each LLM-generated keyword with appropriate locations. " +
                    "This is the same flow that runs on the scheduler (disabled by default)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Scheduled crawl simulation completed",
                    content = @Content(schema = @Schema(implementation = ScheduledCrawlResponse.class))),
            @ApiResponse(responseCode = "500", description = "Simulation failed")
    })
    @PostMapping("/crawl/scheduled-simulation")
    public ResponseEntity<ScheduledCrawlResponse> simulateScheduledCrawl(
            @Parameter(description = "Number of keyword pairs to generate (default: 10)")
            @RequestParam(defaultValue = "10") int keywordCount,
            @Parameter(description = "Max results per keyword (default: 50)")
            @RequestParam(defaultValue = "50") int maxResultsPerKeyword,
            @Parameter(description = "Ignored - LLM generates locations based on user profiles")
            @RequestParam(required = false) String location) {

        log.info("=== Manual Scheduled Crawl Simulation Started ===");
        log.info("Generating {} complete job title keywords, {} jobs per keyword (based on active user profiles)",
                keywordCount, maxResultsPerKeyword);

        long startTime = System.currentTimeMillis();
        ScheduledCrawlResponse response = new ScheduledCrawlResponse();

        try {
            // 1. Generate optimal keywords using Grok LLM based on active user profiles
            log.info("Step 1: Generating complete job title keywords using LLM (analyzing active profiles)...");
            List<KeywordGenerationService.KeywordPair> keywordPairs =
                    keywordGenerationService.generateSearchKeywords(keywordCount);

            log.info("LLM generated {} complete job title keywords", keywordPairs.size());
            response.setKeywordPairsGenerated(keywordPairs.size());

            int totalFetched = 0;
            int totalSaved = 0;
            int totalUpdated = 0;
            int totalDuplicates = 0;
            int totalFailed = 0;
            List<KeywordCrawlResult> results = new ArrayList<>();

            // 2. Crawl jobs for each keyword pair (using LLM-generated locations)
            // NOTE: Any Adzuna API error will throw and stop the entire crawl
            for (KeywordGenerationService.KeywordPair pair : keywordPairs) {
                log.info("Step 2.{}: Crawling jobs for '{}' in {}/{} (excluding: {})",
                        results.size() + 1, pair.getKeyword(),
                        pair.getTargetCountryCode(), pair.getTargetCityRegion(),
                        pair.getExclude());

                JobCrawlRequest request = new JobCrawlRequest();
                request.setSource("ADZUNA");
                request.setKeywords(pair.getKeyword());
                // Use LLM-generated location instead of parameter
                request.setLocation(pair.getTargetCountryCode() + ":" + pair.getTargetCityRegion());
                request.setMaxResults(maxResultsPerKeyword);
                request.setPage(1);
                request.setExcludeKeywords(pair.getExclude());
                request.setMaxDaysOld(maxDaysOld); // Configured in application.yml (same as scheduler)

                // This will throw immediately on any Adzuna API error, stopping the crawl
                JobCrawlResponse crawlResponse = jobCrawlingService.crawlJobs(request);

                totalFetched += crawlResponse.getTotalFetched();
                totalSaved += crawlResponse.getTotalSaved();
                totalUpdated += crawlResponse.getDuplicatesUpdated();
                totalDuplicates += crawlResponse.getDuplicatesSkipped();
                totalFailed += crawlResponse.getFailedToProcess();

                // Record result for this keyword
                KeywordCrawlResult result = new KeywordCrawlResult(
                        pair.getKeyword(),
                        pair.getExclude(),
                        crawlResponse.getTotalFetched(),
                        crawlResponse.getTotalSaved(),
                        crawlResponse.getDuplicatesUpdated(),
                        crawlResponse.getDuplicatesSkipped(),
                        crawlResponse.getFailedToProcess()
                );
                results.add(result);

                log.info("Completed crawl for '{}': Fetched={}, Saved={}, Updated={}, Duplicates={}",
                        pair.getKeyword(), crawlResponse.getTotalFetched(),
                        crawlResponse.getTotalSaved(), crawlResponse.getDuplicatesUpdated(),
                        crawlResponse.getDuplicatesSkipped());

                // Rate limiting: sleep 2 seconds between requests (skip for last request)
                if (results.size() < keywordPairs.size()) {
                    Thread.sleep(2000);
                }
            }

            long processingTime = System.currentTimeMillis() - startTime;

            // Build response
            response.setTotalFetched(totalFetched);
            response.setTotalSaved(totalSaved);
            response.setTotalUpdated(totalUpdated);
            response.setTotalDuplicates(totalDuplicates);
            response.setTotalFailed(totalFailed);
            response.setResults(results);
            response.setProcessingTimeMs(processingTime);
            response.setMessage("Scheduled crawl simulation completed successfully");

            log.info("=== Scheduled Crawl Simulation Completed - Fetched: {}, Saved: {}, Updated: {}, Duplicates: {}, Failed: {}, Time: {}ms ===",
                    totalFetched, totalSaved, totalUpdated, totalDuplicates, totalFailed, processingTime);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("CRITICAL: Scheduled crawl simulation STOPPED due to error: {}", e.getMessage(), e);

            response.setMessage("Simulation STOPPED due to error: " + e.getMessage());
            response.setProcessingTimeMs(processingTime);
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Re-vectorize all profile lines from database
     * POST /api/job-search/admin/revectorize/profile-lines
     */
    @Operation(
            summary = "Re-vectorize all profile lines",
            description = "Rebuilds Redis vector cache for all job search profile lines. " +
                    "Useful after Redis cleanup or index changes. Processes all profiles in batches."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Re-vectorization completed",
                    content = @Content(schema = @Schema(implementation = LineRevectorizeResponse.class))),
            @ApiResponse(responseCode = "500", description = "Re-vectorization failed")
    })
    @PostMapping("/revectorize/profile-lines")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<LineRevectorizeResponse> revectorizeProfileLines(
            @Parameter(description = "Batch size for processing (default: 50)")
            @RequestParam(defaultValue = "50") int batchSize) {

        log.info("Starting re-vectorization of all profile lines (batch size: {})", batchSize);
        long startTime = System.currentTimeMillis();

        try {
            List<JobSearchProfile> allProfiles = profileRepository.findAll();
            log.info("Found {} profiles to re-vectorize lines", allProfiles.size());

            int processedProfiles = 0;
            int totalLinesCreated = 0;
            int failedProfiles = 0;
            List<String> failedIds = new ArrayList<>();

            // Process in batches to avoid memory issues
            for (int i = 0; i < allProfiles.size(); i += batchSize) {
                int end = Math.min(i + batchSize, allProfiles.size());
                List<JobSearchProfile> batch = allProfiles.subList(i, end);

                log.info("Processing batch {}/{} ({} profiles)",
                        (i / batchSize) + 1,
                        (allProfiles.size() + batchSize - 1) / batchSize,
                        batch.size());

                // Collect all lines for this batch
                List<com.resumebuddy.jobsearch.domain.JobSearchProfileLine> allLines = new ArrayList<>();
                List<String> allLineContents = new ArrayList<>();

                for (JobSearchProfile profile : batch) {
                    try {
                        // Get existing profile lines
                        List<com.resumebuddy.jobsearch.domain.JobSearchProfileLine> profileLines =
                                profileLineRepository.findByProfileIdOrderByLineNumber(profile.getId());

                        if (profileLines.isEmpty()) {
                            log.warn("No lines found for profile: {}", profile.getId());
                            continue;
                        }

                        // Collect lines for batch vectorization
                        for (com.resumebuddy.jobsearch.domain.JobSearchProfileLine line : profileLines) {
                            allLines.add(line);
                            allLineContents.add(line.getLineContent());
                        }

                        log.debug("Found {} lines for profile: {}", profileLines.size(), profile.getId());

                    } catch (Exception e) {
                        log.error("Failed to get lines for profile: {}", profile.getId(), e);
                        failedProfiles++;
                        failedIds.add(profile.getId());
                    }
                }

                // Batch vectorize all lines
                if (!allLines.isEmpty()) {
                    try {
                        log.info("Generating embeddings for {} lines from batch", allLines.size());
                        List<float[]> lineEmbeddings = vectorEmbeddingService.generateEmbeddings(allLineContents);

                        if (lineEmbeddings.size() != allLines.size()) {
                            log.error("Mismatch: {} lines but {} embeddings", allLines.size(), lineEmbeddings.size());
                        } else {
                            // Store line vectors in Redis
                            for (int j = 0; j < allLines.size(); j++) {
                                try {
                                    com.resumebuddy.jobsearch.domain.JobSearchProfileLine line = allLines.get(j);
                                    float[] embedding = lineEmbeddings.get(j);

                                    // Store vector in Redis (reuse existing key or create new)
                                    String redisKey = line.getRedisVectorKey();
                                    if (redisKey == null || redisKey.isEmpty()) {
                                        redisKey = "profile:line:" + line.getId();
                                        line.setRedisVectorKey(redisKey);
                                    }

                                    redisVectorService.storeVector(redisKey, embedding);

                                } catch (Exception e) {
                                    log.error("Failed to store line vector: {}", allLines.get(j).getLineContent(), e);
                                }
                            }

                            totalLinesCreated += allLines.size();
                            processedProfiles += batch.size() - failedProfiles;
                        }
                    } catch (Exception e) {
                        log.error("Failed to batch vectorize lines for batch", e);
                    }
                }

                log.info("Batch completed: {}/{} profiles processed, {} lines vectorized",
                        processedProfiles, allProfiles.size(), totalLinesCreated);
            }

            long processingTime = System.currentTimeMillis() - startTime;
            LineRevectorizeResponse response = new LineRevectorizeResponse(
                    allProfiles.size(),
                    processedProfiles,
                    failedProfiles,
                    totalLinesCreated,
                    failedIds,
                    processingTime
            );

            log.info("Profile line re-vectorization completed: {}/{} profiles successful, {} lines vectorized, {}ms",
                    processedProfiles, allProfiles.size(), totalLinesCreated, processingTime);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Profile line re-vectorization failed", e);
            long processingTime = System.currentTimeMillis() - startTime;
            LineRevectorizeResponse response = new LineRevectorizeResponse(
                    0, 0, 0, 0, new ArrayList<>(), processingTime
            );
            response.setMessage("Failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Re-vectorize all job listing lines from database
     * POST /api/job-search/admin/revectorize/listing-lines
     */
    @Operation(
            summary = "Re-vectorize all job listing lines",
            description = "Parses all job descriptions into lines and rebuilds Redis vector cache for line-by-line matching. " +
                    "This will DELETE existing lines and create new ones. Processes all listings in batches."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Re-vectorization completed",
                    content = @Content(schema = @Schema(implementation = LineRevectorizeResponse.class))),
            @ApiResponse(responseCode = "500", description = "Re-vectorization failed")
    })
    @PostMapping("/revectorize/listing-lines")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<LineRevectorizeResponse> revectorizeListingLines(
            @Parameter(description = "Batch size for processing (default: 50)")
            @RequestParam(defaultValue = "50") int batchSize) {

        log.info("Starting re-vectorization of all job listing lines (batch size: {})", batchSize);
        long startTime = System.currentTimeMillis();

        try {
            List<JobListing> allListings = jobListingRepository.findAll();
            log.info("Found {} job listings to parse and vectorize lines", allListings.size());

            int processedListings = 0;
            int totalLinesCreated = 0;
            int failedListings = 0;
            List<String> failedIds = new ArrayList<>();

            // Process in batches to avoid memory issues
            for (int i = 0; i < allListings.size(); i += batchSize) {
                int end = Math.min(i + batchSize, allListings.size());
                List<JobListing> batch = allListings.subList(i, end);

                log.info("Processing batch {}/{} ({} listings)",
                        (i / batchSize) + 1,
                        (allListings.size() + batchSize - 1) / batchSize,
                        batch.size());

                // Collect all lines for this batch
                List<JobListingLine> allLines = new ArrayList<>();
                List<String> allLineContents = new ArrayList<>();

                for (JobListing listing : batch) {
                    try {
                        // Delete existing lines for this listing
                        jobListingLineRepository.deleteByListingId(listing.getId());

                        // Parse description into lines
                        List<String> parsedLines = jobDescriptionParser.parseDescription(listing.getDescription());

                        // Create JobListingLine entities
                        for (int lineNum = 0; lineNum < parsedLines.size(); lineNum++) {
                            JobListingLine line = new JobListingLine();
                            line.setListingId(listing.getId());
                            line.setLineNumber(lineNum + 1);
                            line.setLineContent(parsedLines.get(lineNum));

                            allLines.add(line);
                            allLineContents.add(parsedLines.get(lineNum));
                        }

                        log.debug("Parsed {} lines for listing: {}", parsedLines.size(), listing.getTitle());

                    } catch (Exception e) {
                        log.error("Failed to parse description for listing: {}", listing.getId(), e);
                        failedListings++;
                        failedIds.add(listing.getId());
                    }
                }

                // Save all lines to MySQL first to get IDs
                if (!allLines.isEmpty()) {
                    List<JobListingLine> savedLines = jobListingLineRepository.saveAll(allLines);
                    log.info("Saved {} lines to MySQL, now generating embeddings", savedLines.size());

                    try {
                        List<float[]> lineEmbeddings = vectorEmbeddingService.generateEmbeddings(allLineContents);

                        if (lineEmbeddings.size() != savedLines.size()) {
                            log.error("Mismatch: {} lines but {} embeddings", savedLines.size(), lineEmbeddings.size());
                        } else {
                            // Store line vectors in Redis using database IDs
                            for (int j = 0; j < savedLines.size(); j++) {
                                try {
                                    JobListingLine line = savedLines.get(j);
                                    float[] embedding = lineEmbeddings.get(j);

                                    // CRITICAL: Use database ID in Redis key so matching can find the line
                                    String redisKey = "listing:line:" + line.getId();
                                    redisVectorService.storeVector(redisKey, embedding);

                                    // Update line with Redis key
                                    line.setRedisVectorKey(redisKey);
                                } catch (Exception e) {
                                    log.error("Failed to store line vector: {}", savedLines.get(j).getLineContent(), e);
                                }
                            }

                            // Batch update redis keys
                            jobListingLineRepository.saveAll(savedLines);
                            totalLinesCreated += savedLines.size();
                            processedListings += batch.size() - failedListings;
                        }
                    } catch (Exception e) {
                        log.error("Failed to batch vectorize lines for batch", e);
                    }
                }

                log.info("Batch completed: {}/{} listings processed, {} lines created",
                        processedListings, allListings.size(), totalLinesCreated);
            }

            long processingTime = System.currentTimeMillis() - startTime;
            LineRevectorizeResponse response = new LineRevectorizeResponse(
                    allListings.size(),
                    processedListings,
                    failedListings,
                    totalLinesCreated,
                    failedIds,
                    processingTime
            );

            log.info("Line re-vectorization completed: {}/{} listings successful, {} lines created, {}ms",
                    processedListings, allListings.size(), totalLinesCreated, processingTime);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Line re-vectorization failed", e);
            long processingTime = System.currentTimeMillis() - startTime;
            LineRevectorizeResponse response = new LineRevectorizeResponse(
                    0, 0, 0, 0, new ArrayList<>(), processingTime
            );
            response.setMessage("Failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Rebuild Redis index with line-level prefixes
     * POST /api/job-search/admin/rebuild-index
     */
    @Operation(
            summary = "Rebuild Redis vector index (DESTRUCTIVE)",
            description = "Drops existing Redis vector index and recreates it with correct line-level prefixes " +
                    "(profile:line:, listing:line:). This will make all old full-document vectors unsearchable. " +
                    "You MUST run /revectorize/listing-lines and /revectorize/profile-lines after this to rebuild line vectors."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Index rebuilt successfully"),
            @ApiResponse(responseCode = "500", description = "Index rebuild failed")
    })
    @PostMapping("/rebuild-index")
    public ResponseEntity<RebuildIndexResponse> rebuildIndex() {
        log.info("=== Starting Redis index rebuild ===");
        long startTime = System.currentTimeMillis();

        try {
            // This will be handled by updating RedisVectorService
            redisVectorService.rebuildIndexForLineMatching();

            long processingTime = System.currentTimeMillis() - startTime;
            RebuildIndexResponse response = new RebuildIndexResponse(
                    "success",
                    "Index rebuilt successfully with line-level prefixes. Run /revectorize/listing-lines and /revectorize/profile-lines to rebuild vectors.",
                    processingTime
            );

            log.info("=== Index rebuild completed in {}ms ===", processingTime);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to rebuild index", e);
            long processingTime = System.currentTimeMillis() - startTime;
            RebuildIndexResponse response = new RebuildIndexResponse(
                    "error",
                    "Failed to rebuild index: " + e.getMessage(),
                    processingTime
            );
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Analyze jobs for market insights (skill extraction)
     * POST /api/job-search/admin/analyze-jobs
     */
    @Operation(
            summary = "Analyze jobs for market insights (Phase 1)",
            description = "Extracts skills from job descriptions using GPT-4o-mini for market intelligence. " +
                    "Processes unanalyzed jobs in batches and stores results in MySQL + Neo4j. " +
                    "This is the Phase 1 implementation of the Job Market Insights feature."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Analysis completed successfully",
                    content = @Content(schema = @Schema(implementation = JobAnalysisResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "500", description = "Analysis failed")
    })
    @PostMapping("/analyze-jobs")
    public ResponseEntity<JobAnalysisResponse> analyzeJobs(@Valid @RequestBody JobAnalysisRequest request) {
        log.info("Job analysis triggered - batchSize: {}, forceReanalyze: {}",
                request.getBatchSize(), request.getForceReanalyze());

        JobAnalysisResponse response = jobAnalysisService.batchAnalyzeJobs(request);

        if ("error".equals(response.getStatus())) {
            return ResponseEntity.status(500).body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint
     * GET /api/job-search/admin/health
     */
    @Operation(summary = "Health check", description = "Check if admin API is operational")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Admin API is healthy");
    }

    // Response DTOs

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduledCrawlResponse {
        private Integer keywordPairsGenerated;
        private Integer totalFetched;
        private Integer totalSaved;
        private Integer totalUpdated;
        private Integer totalDuplicates;
        private Integer totalFailed;
        private List<KeywordCrawlResult> results;
        private String message;
        private Long processingTimeMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeywordCrawlResult {
        private String keyword;
        private List<String> excludeKeywords;
        private Integer fetched;
        private Integer saved;
        private Integer updated;
        private Integer duplicates;
        private Integer failed;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevectorizeResponse {
        private String type; // "listings" or "profiles"
        private Integer total;
        private Integer processed;
        private Integer failed;
        private List<String> failedIds;
        private Long processingTimeMs;
        private String message;

        public RevectorizeResponse(String type, Integer total, Integer processed, Integer failed,
                                   List<String> failedIds, Long processingTimeMs) {
            this.type = type;
            this.total = total;
            this.processed = processed;
            this.failed = failed;
            this.failedIds = failedIds;
            this.processingTimeMs = processingTimeMs;
            this.message = String.format("Re-vectorized %d/%d %s successfully", processed, total, type);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineRevectorizeResponse {
        private Integer totalListings;
        private Integer processedListings;
        private Integer failedListings;
        private Integer totalLinesCreated;
        private List<String> failedIds;
        private Long processingTimeMs;
        private String message;

        public LineRevectorizeResponse(Integer totalListings, Integer processedListings, Integer failedListings,
                                       Integer totalLinesCreated, List<String> failedIds, Long processingTimeMs) {
            this.totalListings = totalListings;
            this.processedListings = processedListings;
            this.failedListings = failedListings;
            this.totalLinesCreated = totalLinesCreated;
            this.failedIds = failedIds;
            this.processingTimeMs = processingTimeMs;
            this.message = String.format("Parsed and vectorized %d lines from %d/%d listings successfully",
                    totalLinesCreated, processedListings, totalListings);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RebuildIndexResponse {
        private String status;
        private String message;
        private Long processingTimeMs;
    }
}
