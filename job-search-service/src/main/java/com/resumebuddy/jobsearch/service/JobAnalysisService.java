package com.resumebuddy.jobsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.jobsearch.domain.JobListing;
import com.resumebuddy.jobsearch.dto.analysis.JobAnalysisRequest;
import com.resumebuddy.jobsearch.dto.analysis.JobAnalysisResponse;
import com.resumebuddy.jobsearch.repository.JobListingRepository;
import com.resumebuddy.jobsearch.repository.JobListingLineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service: Job Analysis (Market Insights Phase 1)
 * Extracts skills from job descriptions using keyword matching against Neo4j vocabulary
 * No LLM required - fast and cost-effective
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobAnalysisService {

    private final JobListingRepository listingRepository;
    private final JobListingLineRepository listingLineRepository;
    private final KeywordSkillMatcher keywordSkillMatcher;
    private final ObjectMapper objectMapper;
    private final Neo4jJobListingService neo4jJobListingService;

    @Value("${app.market-insights.enabled:true}")
    private boolean marketInsightsEnabled;

    /**
     * Batch analyze unanalyzed jobs
     * Main entry point for skill extraction
     */
    public JobAnalysisResponse batchAnalyzeJobs(JobAnalysisRequest request) {
        if (!marketInsightsEnabled) {
            log.warn("Market insights feature is disabled");
            return createErrorResponse("Market insights feature is disabled");
        }

        log.info("Starting batch job analysis - batchSize: {}, forceReanalyze: {}, maxDaysOld: {}",
                request.getBatchSize(), request.getForceReanalyze(), request.getMaxDaysOld());

        long startTime = System.currentTimeMillis();

        try {
            // 1. Fetch jobs to analyze
            List<JobListing> jobsToAnalyze = fetchJobsForAnalysis(request);

            if (jobsToAnalyze.isEmpty()) {
                log.info("No jobs found for analysis");
                return new JobAnalysisResponse(
                        0, 0, 0, 0, 0,
                        System.currentTimeMillis() - startTime, "$0.000000"
                );
            }

            log.info("Found {} jobs for analysis", jobsToAnalyze.size());

            // 2. Process in batches
            int batchSize = request.getBatchSize();
            int totalAnalyzed = 0;
            int totalSkipped = 0;
            int totalErrors = 0;
            int totalSkillsExtracted = 0;
            double totalCost = 0.0;
            List<String> failedJobIds = new ArrayList<>();

            for (int i = 0; i < jobsToAnalyze.size(); i += batchSize) {
                int end = Math.min(i + batchSize, jobsToAnalyze.size());
                List<JobListing> batch = jobsToAnalyze.subList(i, end);

                log.info("Processing batch {}/{} ({} jobs)",
                        (i / batchSize) + 1,
                        (jobsToAnalyze.size() + batchSize - 1) / batchSize,
                        batch.size());

                try {
                    BatchResult result = processBatch(batch);
                    totalAnalyzed += result.analyzed;
                    totalSkipped += result.skipped;
                    totalErrors += result.errors;
                    totalSkillsExtracted += result.skillsExtracted;
                    totalCost += result.estimatedCost;
                    failedJobIds.addAll(result.failedIds);

                } catch (Exception e) {
                    log.error("Failed to process batch {}", (i / batchSize) + 1, e);
                    totalErrors += batch.size();
                    batch.forEach(job -> failedJobIds.add(job.getId()));
                }
            }

            long processingTime = System.currentTimeMillis() - startTime;
            JobAnalysisResponse response = new JobAnalysisResponse(
                    jobsToAnalyze.size(),
                    totalAnalyzed,
                    totalSkipped,
                    totalErrors,
                    totalSkillsExtracted,
                    processingTime,
                    String.format("$%.6f", totalCost)
            );
            response.setFailedJobIds(failedJobIds);

            log.info("Batch analysis completed - Analyzed: {}, Skipped: {}, Errors: {}, Skills: {}, Cost: ${}, Time: {}ms",
                    totalAnalyzed, totalSkipped, totalErrors, totalSkillsExtracted, String.format("%.6f", totalCost), processingTime);

            return response;

        } catch (Exception e) {
            log.error("Batch job analysis failed", e);
            return createErrorResponse("Batch analysis failed: " + e.getMessage());
        }
    }

    /**
     * Fetch jobs that need analysis based on request parameters
     */
    private List<JobListing> fetchJobsForAnalysis(JobAnalysisRequest request) {
        List<JobListing> jobs;

        if (request.getForceReanalyze()) {
            // Get all jobs (newest first)
            if (request.getMaxDaysOld() != null) {
                LocalDateTime cutoff = LocalDateTime.now().minusDays(request.getMaxDaysOld());
                jobs = listingRepository.findAll().stream()
                        .filter(j -> j.getFetchedAt().isAfter(cutoff))
                        .sorted((j1, j2) -> j2.getFetchedAt().compareTo(j1.getFetchedAt()))
                        .toList();
            } else {
                jobs = listingRepository.findAll().stream()
                        .sorted((j1, j2) -> j2.getFetchedAt().compareTo(j1.getFetchedAt()))
                        .toList();
            }
        } else {
            // Get only unanalyzed jobs (newest first)
            if (request.getMaxDaysOld() != null) {
                LocalDateTime cutoff = LocalDateTime.now().minusDays(request.getMaxDaysOld());
                jobs = listingRepository.findByAnalyzedAtIsNullAndFetchedAtAfterOrderByFetchedAtDesc(cutoff);
            } else {
                jobs = listingRepository.findByAnalyzedAtIsNullOrderByFetchedAtDesc();
            }
        }

        // Apply maxJobs limit if specified
        if (request.getMaxJobs() != null && jobs.size() > request.getMaxJobs()) {
            log.info("Limiting analysis to {} jobs (found {} total)", request.getMaxJobs(), jobs.size());
            jobs = jobs.subList(0, request.getMaxJobs());
        }

        return jobs;
    }

    /**
     * Process a batch of jobs: extract skills using keyword matching, update MySQL, index in Neo4j
     */
    private BatchResult processBatch(List<JobListing> batch) {
        BatchResult result = new BatchResult();

        try {
            // Process each job individually using keyword matching
            for (JobListing job : batch) {
                try {
                    // 1. Get full description from job_listing_line table
                    List<com.resumebuddy.jobsearch.domain.JobListingLine> lines =
                        listingLineRepository.findByListingIdOrderByLineNumber(job.getId());

                    String fullDescription;
                    if (!lines.isEmpty()) {
                        // Concatenate all lines to get full description
                        StringBuilder sb = new StringBuilder();
                        for (com.resumebuddy.jobsearch.domain.JobListingLine line : lines) {
                            sb.append(line.getLineContent()).append("\n");
                        }
                        fullDescription = sb.toString();
                    } else {
                        // Fallback to description from job_listing table
                        fullDescription = job.getDescription() != null ? job.getDescription() : "";
                    }

                    // 2. Extract skills using keyword matching (no LLM call)
                    List<String> skills = keywordSkillMatcher.extractSkills(fullDescription);

                    if (skills.isEmpty()) {
                        log.debug("No skills matched for job: {} - {}", job.getId(), job.getTitle());
                        result.skipped++;
                        continue;
                    }

                    // 3. Update MySQL
                    job.setExtractedSkills(objectMapper.writeValueAsString(skills));
                    job.setAnalyzedAt(LocalDateTime.now());
                    listingRepository.save(job);

                    // 4. Index in Neo4j
                    neo4jJobListingService.indexJobWithSkills(job.getId(), job.getTitle(), skills);

                    result.analyzed++;
                    result.skillsExtracted += skills.size();

                    log.debug("Analyzed job {} - extracted {} skills", job.getTitle(), skills.size());

                } catch (Exception e) {
                    log.error("Failed to process job {}", job.getId(), e);
                    result.errors++;
                    result.failedIds.add(job.getId());
                }
            }

            // No LLM costs!
            result.estimatedCost = 0.0;

        } catch (Exception e) {
            log.error("Failed to process batch", e);
            result.errors = batch.size();
            batch.forEach(job -> result.failedIds.add(job.getId()));
        }

        return result;
    }

    /**
     * Create error response
     */
    private JobAnalysisResponse createErrorResponse(String message) {
        JobAnalysisResponse response = new JobAnalysisResponse();
        response.setStatus("error");
        response.setMessage(message);
        response.setProcessingTimeMs(0L);
        return response;
    }

    /**
     * Inner class for batch processing results
     */
    private static class BatchResult {
        int analyzed = 0;
        int skipped = 0;
        int errors = 0;
        int skillsExtracted = 0;
        double estimatedCost = 0.0;
        List<String> failedIds = new ArrayList<>();
    }
}
