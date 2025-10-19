package com.resumebuddy.jobsearch.application.service;

import com.resumebuddy.jobsearch.domain.CrawlActivityLog;
import com.resumebuddy.jobsearch.domain.service.JobCrawlerService;
import com.resumebuddy.jobsearch.dto.crawl.JobCrawlRequest;
import com.resumebuddy.jobsearch.dto.crawl.JobCrawlResponse;
import com.resumebuddy.jobsearch.infrastructure.external.jobsources.AdzunaApiClient;
import com.resumebuddy.jobsearch.infrastructure.external.jobsources.ReedApiClient;
import com.resumebuddy.jobsearch.infrastructure.external.jobsources.JSearchApiClient;
import com.resumebuddy.jobsearch.infrastructure.external.jobsources.DevITJobsApiClient;
import com.resumebuddy.jobsearch.infrastructure.external.jobsources.FantasticJobsApiClient;
import com.resumebuddy.jobsearch.infrastructure.external.jobsources.TheirstackApiClient;
import com.resumebuddy.jobsearch.infrastructure.external.jobsources.JobSourceApiClient;
import com.resumebuddy.jobsearch.repository.CrawlActivityLogRepository;
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
    private final DevITJobsApiClient devITJobsApiClient;
    private final FantasticJobsApiClient fantasticJobsApiClient;
    private final TheirstackApiClient theirstackApiClient;
    private final CrawlActivityLogRepository crawlActivityLogRepository;

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

        CrawlActivityLog activityLog = new CrawlActivityLog();
        activityLog.setSource(request.getSource());
        activityLog.setKeywords(request.getKeywords());
        activityLog.setLocation(request.getLocation());

        try {
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

            // Log successful crawl activity
            activityLog.setTotalFetched(response.getTotalFetched());
            activityLog.setTotalSaved(response.getTotalSaved());
            activityLog.setDuplicatesUpdated(response.getDuplicatesUpdated());
            activityLog.setDuplicatesSkipped(response.getDuplicatesSkipped());
            activityLog.setFailedToProcess(response.getFailedToProcess());
            activityLog.setStatus("SUCCESS");
            activityLog.setProcessingTimeMs(processingTime);
            crawlActivityLogRepository.save(activityLog);

            log.info("Job crawl completed - Fetched: {}, Saved: {}, Updated: {}, Duplicates: {}, Failed: {}, Time: {}ms",
                    response.getTotalFetched(), response.getTotalSaved(),
                    response.getDuplicatesUpdated(), response.getDuplicatesSkipped(),
                    response.getFailedToProcess(), response.getProcessingTimeMs());

            return response;

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;

            // Log failed crawl activity
            activityLog.setStatus("FAILED");
            activityLog.setErrorMessage(e.getMessage());
            activityLog.setProcessingTimeMs(processingTime);
            activityLog.setTotalFetched(0);
            activityLog.setTotalSaved(0);
            activityLog.setDuplicatesUpdated(0);
            activityLog.setDuplicatesSkipped(0);
            activityLog.setFailedToProcess(0);
            crawlActivityLogRepository.save(activityLog);

            log.error("Job crawl FAILED - source: {}, keywords: {}, error: {}",
                    request.getSource(), request.getKeywords(), e.getMessage(), e);

            throw e; // Re-throw to maintain existing error handling behavior
        }
    }

    /**
     * Execute job crawling with custom parameters map (for enrichment features)
     *
     * @param source API source name
     * @param params Custom parameters map
     * @return Crawl statistics summary
     */
    @Transactional
    public JobCrawlResponse crawlJobsWithParams(String source, Map<String, Object> params) {
        long startTime = System.currentTimeMillis();

        String keywords = (String) params.get("keywords");
        String location = (String) params.get("location");

        log.info("Starting job crawl with custom params - source: {}, keywords: {}, location: {}",
                source, keywords, location);

        CrawlActivityLog activityLog = new CrawlActivityLog();
        activityLog.setSource(source);
        activityLog.setKeywords(keywords);
        activityLog.setLocation(location);

        try {
            // Select API client based on source
            JobSourceApiClient apiClient = getApiClient(source);

            // Process jobs with custom params - this will throw immediately on any API error
            Map<String, Integer> stats = jobCrawlerService.processJobs(apiClient, params);

            long processingTime = System.currentTimeMillis() - startTime;

            // Build response
            JobCrawlResponse response = new JobCrawlResponse(
                    source,
                    stats.get("totalFetched"),
                    stats.get("totalSaved"),
                    stats.get("duplicatesUpdated"),
                    stats.get("duplicatesSkipped"),
                    stats.get("failedToProcess"),
                    "Crawl completed successfully",
                    processingTime
            );

            // Log successful crawl activity
            activityLog.setTotalFetched(response.getTotalFetched());
            activityLog.setTotalSaved(response.getTotalSaved());
            activityLog.setDuplicatesUpdated(response.getDuplicatesUpdated());
            activityLog.setDuplicatesSkipped(response.getDuplicatesSkipped());
            activityLog.setFailedToProcess(response.getFailedToProcess());
            activityLog.setStatus("SUCCESS");
            activityLog.setProcessingTimeMs(processingTime);
            crawlActivityLogRepository.save(activityLog);

            log.info("Job crawl completed - Fetched: {}, Saved: {}, Updated: {}, Duplicates: {}, Failed: {}, Time: {}ms",
                    response.getTotalFetched(), response.getTotalSaved(),
                    response.getDuplicatesUpdated(), response.getDuplicatesSkipped(),
                    response.getFailedToProcess(), response.getProcessingTimeMs());

            return response;

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;

            // Log failed crawl activity
            activityLog.setStatus("FAILED");
            activityLog.setErrorMessage(e.getMessage());
            activityLog.setProcessingTimeMs(processingTime);
            activityLog.setTotalFetched(0);
            activityLog.setTotalSaved(0);
            activityLog.setDuplicatesUpdated(0);
            activityLog.setDuplicatesSkipped(0);
            activityLog.setFailedToProcess(0);
            crawlActivityLogRepository.save(activityLog);

            log.error("Job crawl FAILED - source: {}, keywords: {}, error: {}",
                    source, keywords, e.getMessage(), e);

            throw e; // Re-throw to maintain existing error handling behavior
        }
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
        if ("DEVITJOBS".equalsIgnoreCase(source)) {
            return devITJobsApiClient;
        }
        if ("FANTASTICJOBS".equalsIgnoreCase(source)) {
            return fantasticJobsApiClient;
        }
        if ("THEIRSTACK".equalsIgnoreCase(source)) {
            return theirstackApiClient;
        }
        throw new IllegalArgumentException("Unsupported job source: " + source);
    }
}
