package com.resumebuddy.jobsearch.scheduler;

import com.resumebuddy.jobsearch.application.service.JobCrawlingApplicationService;
import com.resumebuddy.jobsearch.domain.service.KeywordGenerationService;
import com.resumebuddy.jobsearch.dto.crawl.JobCrawlRequest;
import com.resumebuddy.jobsearch.dto.crawl.JobCrawlResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler: Automated Job Crawling with User-Driven LLM Keywords
 * Uses Grok LLM to generate COMPLETE job titles based on active user profiles (last 7 days)
 * Analyzes desired job titles + high-proficiency skills to determine appropriate keywords
 * Example: If users want "Java Developer" with high Java proficiency → "Senior Java Developer"
 * Disabled by default (enable via app.job-crawling.enabled=true)
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.job-crawling.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
@RequiredArgsConstructor
public class JobCrawlScheduler {

    private final JobCrawlingApplicationService jobCrawlingService;
    private final KeywordGenerationService keywordGenerationService;

    @Value("${app.job-crawling.max-days-old:7}")
    private int maxDaysOld;

    /**
     * Scheduled crawl with user-driven LLM keywords
     * Default: Daily at 2 AM (0 0 2 * * *)
     * Generates 30 COMPLETE job titles based on active user profiles
     */
    @Scheduled(cron = "${app.job-crawling.schedule:0 0 2 * * *}")
    public void scheduledCrawl() {
        log.info("=== Scheduled Job Crawl Started (User-Driven LLM Keywords) ===");

        try {
            // 1. Generate optimal keywords using Grok LLM based on active user profiles
            log.info("Generating 30 complete job title keywords using LLM (based on active user profiles)...");
            List<KeywordGenerationService.KeywordPair> keywordPairs =
                    keywordGenerationService.generateSearchKeywords(30); // 30 complete job titles

            log.info("Generated {} complete job title keywords for crawling", keywordPairs.size());

            int totalFetched = 0;
            int totalSaved = 0;

            // 2. Crawl jobs for each keyword pair (with LLM-generated locations)
            // NOTE: Any Reed API error will throw and stop the entire crawl
            for (KeywordGenerationService.KeywordPair pair : keywordPairs) {
                log.info("Crawling jobs for: '{}' in {}/{} (excluding: {})",
                        pair.getKeyword(), pair.getTargetCountryCode(),
                        pair.getTargetCityRegion(), pair.getExclude());

                JobCrawlRequest request = new JobCrawlRequest();
                request.setSource("REED");
                request.setKeywords(pair.getKeyword());
                request.setLocation(pair.getTargetCountryCode() + ":" + pair.getTargetCityRegion()); // Format: "gb:London"
                request.setMaxResults(50); // 50 jobs per keyword
                request.setPage(1);
                request.setExcludeKeywords(pair.getExclude());
                request.setMaxDaysOld(maxDaysOld); // Configured in application.yml

                // This will throw immediately on any Reed API error, stopping the crawl
                JobCrawlResponse response = jobCrawlingService.crawlJobs(request);

                totalFetched += response.getTotalFetched();
                totalSaved += response.getTotalSaved();

                log.info("Completed crawl for '{}': Fetched={}, Saved={}, Duplicates={}",
                        pair.getKeyword(), response.getTotalFetched(),
                        response.getTotalSaved(), response.getDuplicatesSkipped());

                // Rate limiting: sleep 2 seconds between requests
                Thread.sleep(2000);
            }

            log.info("=== Scheduled Crawl Completed - Total Fetched: {}, Total Saved: {} ===",
                    totalFetched, totalSaved);

        } catch (Exception e) {
            log.error("CRITICAL: Scheduled crawl STOPPED due to error: {}", e.getMessage(), e);
            // Re-throw to ensure Spring scheduler sees the failure
            throw new RuntimeException("Scheduled crawl failed: " + e.getMessage(), e);
        }
    }

    /**
     * Optional: Multiple scheduled crawls for different job types
     * Uncomment and customize as needed
     */
    /*
    @Scheduled(cron = "0 0 3 * * *") // Daily at 3 AM
    public void crawlDataEngineeringJobs() {
        log.info("Starting scheduled crawl for Data Engineering jobs");
        JobCrawlRequest request = new JobCrawlRequest();
        request.setSource("ADZUNA");
        request.setKeywords("data engineer");
        request.setLocation("us");
        request.setMaxResults(100);
        jobCrawlingService.crawlJobs(request);
    }
    */
}
