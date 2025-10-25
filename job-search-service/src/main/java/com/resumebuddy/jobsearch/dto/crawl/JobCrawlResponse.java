package com.resumebuddy.jobsearch.dto.crawl;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO: Job Crawl Summary Response
 * Returns statistics from crawling operation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobCrawlResponse {

    private String source;

    private Integer totalFetched; // Jobs fetched from API

    private Integer totalSaved; // New jobs saved to database

    private Integer duplicatesUpdated; // Existing jobs updated (same company+title+location, but changed details)

    private Integer duplicatesSkipped; // Duplicate jobs skipped (no changes detected)

    private Integer failedToProcess; // Jobs that failed during processing

    private String message;

    private Long processingTimeMs; // Time taken in milliseconds
}
