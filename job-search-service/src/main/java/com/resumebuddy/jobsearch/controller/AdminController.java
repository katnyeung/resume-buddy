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
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
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
// @PreAuthorize("hasRole('ADMIN')") // Disabled for MVP - admin endpoints are public (see SecurityConfig.java line 50)
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
     * Crawl Reed.co.uk jobs with sensible defaults
     * POST /api/job-search/admin/crawl/reed
     */
    @Operation(
            summary = "Crawl Reed.co.uk jobs (UK, full descriptions)",
            description = "Fetches UK tech jobs from Reed.co.uk with full descriptions via details API. " +
                    "Default parameters optimized for UK job search. Full descriptions take ~30-40 seconds for 50 jobs."
    )
    @PostMapping("/crawl/reed")
    public ResponseEntity<JobCrawlResponse> crawlReed(
            @Parameter(description = "Job keywords/title") @RequestParam(defaultValue = "software engineer") String keywords,
            @Parameter(description = "UK location") @RequestParam(defaultValue = "London") String location,
            @Parameter(description = "Max jobs to fetch") @RequestParam(defaultValue = "50") Integer maxResults,
            @Parameter(description = "Jobs from last N days") @RequestParam(defaultValue = "7") Integer maxDaysOld) {

        log.info("Reed crawl triggered - keywords: {}, location: {}, maxResults: {}", keywords, location, maxResults);

        JobCrawlRequest request = new JobCrawlRequest();
        request.setSource("REED");
        request.setKeywords(keywords);
        request.setLocation(location);
        request.setMaxResults(maxResults);
        request.setMaxDaysOld(maxDaysOld);
        request.setPage(1);

        JobCrawlResponse response = jobCrawlingService.crawlJobs(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Crawl Adzuna jobs with web scraping for full descriptions
     * POST /api/job-search/admin/crawl/adzuna
     */
    @Operation(
            summary = "Crawl Adzuna jobs (UK multi-board aggregator with web scraping)",
            description = "Fetches jobs from Adzuna API with web scraping for full descriptions. " +
                    "Adzuna aggregates from nijobs, cv-library, totaljobs, reed, and others. " +
                    "Uses two-stage fetch: API search + scraping redirect URLs. " +
                    "Default parameters optimized for UK job search. Full descriptions take ~40-50 seconds for 50 jobs."
    )
    @PostMapping("/crawl/adzuna")
    public ResponseEntity<JobCrawlResponse> crawlAdzuna(
            @Parameter(description = "Job keywords/title") @RequestParam(defaultValue = "java developer") String keywords,
            @Parameter(description = "Location (format: 'gb:London' or 'London')") @RequestParam(defaultValue = "gb:London") String location,
            @Parameter(description = "Max jobs to fetch") @RequestParam(defaultValue = "50") Integer maxResults,
            @Parameter(description = "Jobs from last N days") @RequestParam(defaultValue = "7") Integer maxDaysOld) {

        log.info("Adzuna crawl triggered - keywords: {}, location: {}, maxResults: {}", keywords, location, maxResults);

        JobCrawlRequest request = new JobCrawlRequest();
        request.setSource("ADZUNA");
        request.setKeywords(keywords);
        request.setLocation(location);
        request.setMaxResults(maxResults);
        request.setMaxDaysOld(maxDaysOld);
        request.setPage(1);

        JobCrawlResponse response = jobCrawlingService.crawlJobs(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Crawl DevITJobs.uk RSS feed
     * POST /api/job-search/admin/crawl/devitjobs
     */
    @Operation(
            summary = "Crawl DevITJobs.uk RSS feed (UK tech, fast)",
            description = "Fetches UK tech jobs from DevITJobs.uk RSS feed. " +
                    "Fast crawl (~3 seconds for 50 jobs). Filters by date to avoid old jobs. No API key required."
    )
    @PostMapping("/crawl/devitjobs")
    public ResponseEntity<JobCrawlResponse> crawlDevITJobs(
            @Parameter(description = "Job keywords (optional)") @RequestParam(required = false) String keywords,
            @Parameter(description = "Max jobs to fetch") @RequestParam(defaultValue = "50") Integer maxResults,
            @Parameter(description = "Jobs from last N days") @RequestParam(defaultValue = "30") Integer maxDaysOld) {

        log.info("DevITJobs crawl triggered - keywords: {}, maxResults: {}, maxDaysOld: {}",
                keywords, maxResults, maxDaysOld);

        JobCrawlRequest request = new JobCrawlRequest();
        request.setSource("DEVITJOBS");
        request.setKeywords(keywords);
        request.setMaxResults(maxResults);
        request.setMaxDaysOld(maxDaysOld);

        JobCrawlResponse response = jobCrawlingService.crawlJobs(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Crawl JSearch API (global multi-source)
     * POST /api/job-search/admin/crawl/jsearch
     */
    @Operation(
            summary = "Crawl JSearch API (global, multi-source aggregator)",
            description = "Fetches jobs from JSearch (aggregates Google Jobs, Indeed, LinkedIn, etc.). " +
                    "Global coverage with advanced filters: remote, employment type, experience level. " +
                    "Fast (~10-15 seconds for 50 jobs)."
    )
    @PostMapping("/crawl/jsearch")
    public ResponseEntity<JobCrawlResponse> crawlJSearch(
            @Parameter(description = "Job keywords/title") @RequestParam(defaultValue = "software engineer") String keywords,
            @Parameter(description = "Location (city or country code)") @RequestParam(defaultValue = "London") String location,
            @Parameter(description = "Max jobs to fetch") @RequestParam(defaultValue = "50") Integer maxResults,
            @Parameter(description = "Jobs from last N days") @RequestParam(defaultValue = "7") Integer maxDaysOld,
            @Parameter(description = "Remote jobs only") @RequestParam(required = false) Boolean remoteJobsOnly,
            @Parameter(description = "Employment types (FULLTIME,PARTTIME,CONTRACTOR,INTERN)") @RequestParam(required = false) String employmentTypes,
            @Parameter(description = "Job requirements (under_3_years_experience,no_degree,etc)") @RequestParam(required = false) String jobRequirements) {

        log.info("JSearch crawl triggered - keywords: {}, location: {}, remote: {}, types: {}",
                keywords, location, remoteJobsOnly, employmentTypes);

        JobCrawlRequest request = new JobCrawlRequest();
        request.setSource("JSEARCH");
        request.setKeywords(keywords);
        request.setLocation(location);
        request.setMaxResults(maxResults);
        request.setMaxDaysOld(maxDaysOld);
        request.setPage(1);

        // Add to params map for JSearch-specific parameters
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("keywords", keywords);
        params.put("location", location);
        params.put("maxResults", maxResults);
        params.put("maxDaysOld", maxDaysOld);
        params.put("page", 1);

        if (remoteJobsOnly != null) {
            params.put("remoteJobsOnly", remoteJobsOnly);
        }
        if (employmentTypes != null) {
            params.put("employmentTypes", employmentTypes);
        }
        if (jobRequirements != null) {
            params.put("jobRequirements", jobRequirements);
        }

        // Use service directly with params map for enrichment
        JobCrawlResponse response = jobCrawlingService.crawlJobsWithParams(request.getSource(), params);
        return ResponseEntity.ok(response);
    }

    /**
     * Crawl Fantastic Jobs LinkedIn API
     * POST /api/job-search/admin/crawl/fantasticjobs
     */
    @Operation(
            summary = "Crawl Fantastic Jobs LinkedIn API (LinkedIn data, verified)",
            description = "Fetches jobs from LinkedIn via Fantastic Jobs API. " +
                    "Rich company data (followers, industry, specialties). " +
                    "Global coverage with seniority level filters. (~2-4 seconds for 50 jobs). " +
                    "Requires RapidAPI key."
    )
    @PostMapping("/crawl/fantasticjobs")
    public ResponseEntity<JobCrawlResponse> crawlFantasticJobs(
            @Parameter(description = "Job keywords/title") @RequestParam(defaultValue = "software engineer") String keywords,
            @Parameter(description = "Location (city or country)") @RequestParam(defaultValue = "London") String location,
            @Parameter(description = "Max jobs to fetch") @RequestParam(defaultValue = "50") Integer maxResults,
            @Parameter(description = "Jobs from last N days") @RequestParam(defaultValue = "7") Integer maxDaysOld,
            @Parameter(description = "Remote jobs only") @RequestParam(required = false) Boolean remoteJobsOnly,
            @Parameter(description = "Employment types (FULLTIME,PARTTIME,CONTRACTOR,INTERN)") @RequestParam(required = false) String employmentTypes,
            @Parameter(description = "Experience level (Entry,Mid,Senior)") @RequestParam(required = false) String experienceLevel) {

        log.info("Fantastic Jobs crawl triggered - keywords: {}, location: {}, remote: {}, exp: {}",
                keywords, location, remoteJobsOnly, experienceLevel);

        JobCrawlRequest request = new JobCrawlRequest();
        request.setSource("FANTASTICJOBS");
        request.setKeywords(keywords);
        request.setLocation(location);
        request.setMaxResults(maxResults);
        request.setMaxDaysOld(maxDaysOld);
        request.setPage(1);

        // Add to params map for FantasticJobs-specific parameters
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("keywords", keywords);
        params.put("location", location);
        params.put("maxResults", maxResults);
        params.put("maxDaysOld", maxDaysOld);
        params.put("page", 1);

        if (remoteJobsOnly != null) {
            params.put("remoteJobsOnly", remoteJobsOnly);
        }
        if (employmentTypes != null) {
            params.put("employmentTypes", employmentTypes);
        }
        if (experienceLevel != null) {
            params.put("experienceLevel", experienceLevel);
        }

        // Use service directly with params map for enrichment
        JobCrawlResponse response = jobCrawlingService.crawlJobsWithParams(request.getSource(), params);
        return ResponseEntity.ok(response);
    }

    /**
     * Crawl Theirstack API (50M+ jobs, 195 countries, 16+ job boards)
     * POST /api/job-search/admin/crawl/theirstack
     */
    @Operation(
            summary = "Crawl Theirstack API (50M+ jobs, multi-source aggregator)",
            description = "Fetches jobs from Theirstack (aggregates LinkedIn, Indeed, Adzuna + 13 more sources). " +
                    "Massive 50M+ job database covering 195 countries with full job descriptions. " +
                    "Advanced filters: remote, hybrid, country codes. " +
                    "Fast (~5-10 seconds for 50 jobs). Uses Bearer token authentication."
    )
    @PostMapping("/crawl/theirstack")
    public ResponseEntity<JobCrawlResponse> crawlTheirstack(
            @Parameter(description = "Job keywords/title") @RequestParam(defaultValue = "software engineer") String keywords,
            @Parameter(description = "Location (format: gb:United Kingdom or country code)") @RequestParam(defaultValue = "gb:United Kingdom") String location,
            @Parameter(description = "Max jobs to fetch") @RequestParam(defaultValue = "50") Integer maxResults,
            @Parameter(description = "Jobs from last N days") @RequestParam(defaultValue = "7") Integer maxDaysOld,
            @Parameter(description = "Remote jobs only") @RequestParam(required = false) Boolean remoteJobsOnly) {

        log.info("Theirstack crawl triggered - keywords: {}, location: {}, remote: {}",
                keywords, location, remoteJobsOnly);

        JobCrawlRequest request = new JobCrawlRequest();
        request.setSource("THEIRSTACK");
        request.setKeywords(keywords);
        request.setLocation(location);
        request.setMaxResults(maxResults);
        request.setMaxDaysOld(maxDaysOld);
        request.setPage(1);

        // Add to params map for Theirstack-specific parameters
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("keywords", keywords);
        params.put("location", location);
        params.put("resultsPerPage", maxResults);
        params.put("maxDaysOld", maxDaysOld);
        params.put("page", 1);

        if (remoteJobsOnly != null) {
            params.put("remoteJobsOnly", remoteJobsOnly);
        }

        // Use service directly with params map
        JobCrawlResponse response = jobCrawlingService.crawlJobsWithParams(request.getSource(), params);
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
            // NOTE: Any Reed API error will throw and stop the entire crawl
            for (KeywordGenerationService.KeywordPair pair : keywordPairs) {
                log.info("Step 2.{}: Crawling jobs for '{}' in {}/{} (excluding: {})",
                        results.size() + 1, pair.getKeyword(),
                        pair.getTargetCountryCode(), pair.getTargetCityRegion(),
                        pair.getExclude());

                JobCrawlRequest request = new JobCrawlRequest();
                request.setSource("REED");
                request.setKeywords(pair.getKeyword());
                // Use LLM-generated location instead of parameter
                request.setLocation(pair.getTargetCountryCode() + ":" + pair.getTargetCityRegion());
                request.setMaxResults(maxResultsPerKeyword);
                request.setPage(1);
                request.setExcludeKeywords(pair.getExclude());
                request.setMaxDaysOld(maxDaysOld); // Configured in application.yml (same as scheduler)

                // This will throw immediately on any Reed API error, stopping the crawl
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
     * Rebuild Redis index with line-level prefixes
     * POST /api/job-search/admin/rebuild-index
     */
    @Operation(
            summary = "Rebuild Redis vector index (DESTRUCTIVE)",
            description = "Drops existing Redis vector index and recreates it with correct line-level prefixes " +
                    "(profile:line:, listing:line:). This will make all vectors temporarily unsearchable. " +
                    "After rebuilding the index, re-vectorize recent data: POST /admin/revectorize/listing-lines?daysBack=14"
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
                    "Index rebuilt successfully with line-level prefixes. Re-vectorize recent data: POST /admin/revectorize/listing-lines?daysBack=14",
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
     * Re-vectorize job listing lines (disaster recovery after Redis failure)
     * POST /api/job-search/admin/revectorize/listing-lines
     */
    @Operation(
            summary = "Re-vectorize job listing lines",
            description = "Regenerates vector embeddings for job listing lines using OpenAI API. " +
                    "Used for disaster recovery after Redis data loss. " +
                    "Filters by days back to limit OpenAI costs (~$0.10 for 14 days). " +
                    "Processes lines in batches to avoid rate limits."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Re-vectorization completed",
                    content = @Content(schema = @Schema(implementation = RevectorizeResponse.class))),
            @ApiResponse(responseCode = "500", description = "Re-vectorization failed")
    })
    @PostMapping("/revectorize/listing-lines")
    public ResponseEntity<RevectorizeResponse> revectorizeListingLines(
            @Parameter(description = "Only re-vectorize listings from last N days (default: 14)")
            @RequestParam(defaultValue = "14") int daysBack,
            @Parameter(description = "Batch size for OpenAI API calls (default: 50)")
            @RequestParam(defaultValue = "50") int batchSize) {

        log.info("=== Starting re-vectorization of listing lines (daysBack: {}, batchSize: {}) ===", daysBack, batchSize);
        long startTime = System.currentTimeMillis();

        try {
            // Get all listing lines from the last N days
            java.time.LocalDateTime cutoffDate = java.time.LocalDateTime.now().minusDays(daysBack);
            log.info("Fetching listing lines created after {}", cutoffDate);

            // Get all lines (we'll filter by listing date later via join)
            List<JobListingLine> allLines = jobListingLineRepository.findAll();
            log.info("Found {} total listing lines in database", allLines.size());

            // Filter by listing fetched date
            List<JobListingLine> recentLines = allLines.stream()
                    .filter(line -> {
                        try {
                            JobListing listing = jobListingRepository.findById(line.getListingId()).orElse(null);
                            return listing != null && listing.getFetchedAt() != null &&
                                   listing.getFetchedAt().isAfter(cutoffDate);
                        } catch (Exception e) {
                            log.warn("Failed to check listing date for line {}: {}", line.getId(), e.getMessage());
                            return false;
                        }
                    })
                    .toList();

            log.info("Filtered to {} lines from last {} days", recentLines.size(), daysBack);

            if (recentLines.isEmpty()) {
                RevectorizeResponse response = new RevectorizeResponse(
                        "listing-lines", 0, 0, 0, new ArrayList<>(), 0L,
                        "No listing lines found from last " + daysBack + " days"
                );
                return ResponseEntity.ok(response);
            }

            int processed = 0;
            int failed = 0;
            List<String> failedIds = new ArrayList<>();

            // Process in batches
            for (int i = 0; i < recentLines.size(); i += batchSize) {
                int end = Math.min(i + batchSize, recentLines.size());
                List<JobListingLine> batch = recentLines.subList(i, end);

                log.info("Processing batch {}/{} ({} lines)", (i / batchSize) + 1,
                        (recentLines.size() + batchSize - 1) / batchSize, batch.size());

                // Collect texts for batch embedding
                List<String> texts = batch.stream()
                        .map(JobListingLine::getLineContent)
                        .toList();

                try {
                    // Generate embeddings in batch
                    List<float[]> embeddings = vectorEmbeddingService.generateEmbeddings(texts);

                    // Store each embedding
                    for (int j = 0; j < batch.size(); j++) {
                        JobListingLine line = batch.get(j);
                        float[] embedding = embeddings.get(j);

                        try {
                            redisVectorService.storeJobListingLineVector(line.getId(), embedding);
                            processed++;
                        } catch (Exception e) {
                            log.error("Failed to store vector for line {}: {}", line.getId(), e.getMessage());
                            failed++;
                            failedIds.add(line.getId());
                        }
                    }

                    // Rate limiting: sleep 1 second between batches
                    if (end < recentLines.size()) {
                        Thread.sleep(1000);
                    }

                } catch (Exception e) {
                    log.error("Failed to generate embeddings for batch: {}", e.getMessage());
                    failed += batch.size();
                    batch.forEach(line -> failedIds.add(line.getId()));
                }
            }

            long processingTime = System.currentTimeMillis() - startTime;
            RevectorizeResponse response = new RevectorizeResponse(
                    "listing-lines", recentLines.size(), processed, failed, failedIds, processingTime
            );

            log.info("=== Re-vectorization completed - Processed: {}/{}, Failed: {}, Time: {}ms ===",
                    processed, recentLines.size(), failed, processingTime);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Re-vectorization failed: {}", e.getMessage(), e);

            RevectorizeResponse response = new RevectorizeResponse(
                    "listing-lines", 0, 0, 0, new ArrayList<>(), processingTime,
                    "Failed: " + e.getMessage()
            );
            return ResponseEntity.status(500).body(response);
        }
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

    /**
     * TEMPORARY: Strip HTML from existing job_listing_line records
     * POST /api/job-search/admin/strip-html-from-lines
     */
    @Operation(
            summary = "Strip HTML from existing job listing lines (TEMPORARY)",
            description = "Updates all existing job_listing_line records to remove HTML tags. " +
                    "Uses the same stripHtml() logic as JobDescriptionParser. " +
                    "NOTE: This is a one-time cleanup operation."
    )
    @PostMapping("/strip-html-from-lines")
    public ResponseEntity<StripHtmlResponse> stripHtmlFromLines() {
        long startTime = System.currentTimeMillis();

        log.info("Starting HTML stripping for all job_listing_line records");

        // Fetch ALL job listing lines
        List<JobListingLine> allLines = jobListingLineRepository.findAll();
        log.info("Found {} job listing lines to process", allLines.size());

        int updatedCount = 0;
        int unchangedCount = 0;

        for (JobListingLine line : allLines) {
            String originalContent = line.getLineContent();
            if (originalContent == null || originalContent.isEmpty()) {
                unchangedCount++;
                continue;
            }

            // Strip HTML using the parser's method
            String cleanedContent = stripHtml(originalContent);

            // Only update if content changed
            if (!cleanedContent.equals(originalContent)) {
                line.setLineContent(cleanedContent);
                jobListingLineRepository.save(line);
                updatedCount++;

                if (updatedCount % 100 == 0) {
                    log.info("Processed {} lines...", updatedCount);
                }
            } else {
                unchangedCount++;
            }
        }

        long processingTime = System.currentTimeMillis() - startTime;

        log.info("HTML stripping complete - Updated: {}, Unchanged: {}, Total: {}, Time: {}ms",
                updatedCount, unchangedCount, allLines.size(), processingTime);

        return ResponseEntity.ok(new StripHtmlResponse(
                allLines.size(),
                updatedCount,
                unchangedCount,
                processingTime,
                "HTML stripping completed successfully"
        ));
    }

    /**
     * Helper method: Strip HTML tags (same logic as JobDescriptionParser)
     */
    private String stripHtml(String html) {
        if (html == null) {
            return "";
        }

        // Replace block-level HTML tags with newlines
        html = html.replaceAll("</p>", "\n");
        html = html.replaceAll("</div>", "\n");
        html = html.replaceAll("</li>", "\n");
        html = html.replaceAll("<br\\s*/?>", "\n");
        html = html.replaceAll("</h[1-6]>", "\n");

        // Remove ALL remaining HTML tags
        html = html.replaceAll("<[^>]+>", " ");

        // Decode common HTML entities
        html = html.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–")
                .replace("&bull;", "•")
                .replace("&middot;", "·")
                .replace("&hellip;", "…");

        // Normalize whitespace
        html = html.replaceAll("[ \\t]+", " ");
        html = html.replaceAll("\\n{3,}", "\n\n");

        return html.trim();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripHtmlResponse {
        private Integer totalLines;
        private Integer updatedLines;
        private Integer unchangedLines;
        private Long processingTimeMs;
        private String message;
    }
}
