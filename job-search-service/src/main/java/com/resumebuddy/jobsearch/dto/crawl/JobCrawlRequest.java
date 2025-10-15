package com.resumebuddy.jobsearch.dto.crawl;

import lombok.Data;

import java.util.List;

/**
 * DTO: Manual Job Crawl Request
 * Used for testing/debugging via admin endpoint
 * Note: maxDaysOld default is set via @Value in controller/scheduler
 */
@Data
public class JobCrawlRequest {

    private String source = "ADZUNA"; // Default source

    private String keywords; // e.g., "java developer"

    private String location; // e.g., "New York", "us"

    private Integer maxResults = 50; // Default: 50 jobs

    private Integer page = 1; // Pagination support

    private Boolean fullTimeOnly = false;

    private Boolean permanentOnly = false;

    private Integer minSalary; // Optional minimum salary filter

    private List<String> excludeKeywords; // Keywords to exclude (e.g., ["sales", "marketing"])

    private Integer maxDaysOld; // Jobs posted within last N days (configured in application.yml)
}
