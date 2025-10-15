package com.resumebuddy.jobsearch.application.service;

import com.resumebuddy.jobsearch.domain.service.JobCrawlerService;
import com.resumebuddy.jobsearch.dto.crawl.JobCrawlRequest;
import com.resumebuddy.jobsearch.dto.crawl.JobCrawlResponse;
import com.resumebuddy.jobsearch.infrastructure.external.jobsources.AdzunaApiClient;
import com.resumebuddy.jobsearch.infrastructure.external.jobsources.ReedApiClient;
import com.resumebuddy.jobsearch.infrastructure.external.jobsources.JSearchApiClient;
import com.resumebuddy.jobsearch.infrastructure.external.jobsources.JobSourceApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Application Service: Job Crawling Orchestration
 * Manages transaction boundaries and error handling for crawling operations
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobCrawlingApplicationService {

    private final JobCrawlerService jobCrawlerService;
    private final AdzunaApiClient adzunaApiClient;
    private final ReedApiClient reedApiClient;
    private final JSearchApiClient jSearchApiClient;

    /**
     * Execute job crawling operation
     *
     * @param request Crawl request parameters
     * @return Crawl statistics summary
     */
    @Transactional
    public JobCrawlResponse crawlJobs(JobCrawlRequest request) {
        long startTime = System.currentTimeMillis();

        log.info("Starting job crawl - source: {}, keywords: {}, location: {}, maxResults: {}",
                request.getSource(), request.getKeywords(), request.getLocation(), request.getMaxResults());

        // Select API client based on source
        JobSourceApiClient apiClient = getApiClient(request.getSource());

        // Build search parameters
        Map<String, Object> params = new HashMap<>();
        params.put("keywords", request.getKeywords());
        params.put("location", request.getLocation());
        params.put("page", request.getPage());
        params.put("resultsPerPage", request.getMaxResults());
        params.put("maxResults", request.getMaxResults()); // For JSearch
        params.put("fullTimeOnly", request.getFullTimeOnly());
        params.put("permanentOnly", request.getPermanentOnly());
        params.put("minSalary", request.getMinSalary());
        params.put("excludeKeywords", request.getExcludeKeywords());
        params.put("maxDaysOld", request.getMaxDaysOld());

        // Process jobs - this will throw immediately on any API error
        Map<String, Integer> stats = jobCrawlerService.processJobs(apiClient, params);

        long processingTime = System.currentTimeMillis() - startTime;

        // Build response
        JobCrawlResponse response = new JobCrawlResponse(
                request.getSource(),
                stats.get("totalFetched"),
                stats.get("totalSaved"),
                stats.get("duplicatesUpdated"),
                stats.get("duplicatesSkipped"),
                stats.get("failedToProcess"),
                "Crawl completed successfully",
                processingTime
        );

        log.info("Job crawl completed - Fetched: {}, Saved: {}, Updated: {}, Duplicates: {}, Failed: {}, Time: {}ms",
                response.getTotalFetched(), response.getTotalSaved(),
                response.getDuplicatesUpdated(), response.getDuplicatesSkipped(),
                response.getFailedToProcess(), response.getProcessingTimeMs());

        return response;
    }

    /**
     * Get API client by source name
     */
    private JobSourceApiClient getApiClient(String source) {
        if ("ADZUNA".equalsIgnoreCase(source)) {
            return adzunaApiClient;
        }
        if ("REED".equalsIgnoreCase(source)) {
            return reedApiClient;
        }
        if ("JSEARCH".equalsIgnoreCase(source)) {
            return jSearchApiClient;
        }
        throw new IllegalArgumentException("Unsupported job source: " + source);
    }
}
