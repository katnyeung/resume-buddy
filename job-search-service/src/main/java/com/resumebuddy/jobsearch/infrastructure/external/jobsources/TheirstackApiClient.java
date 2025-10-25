package com.resumebuddy.jobsearch.infrastructure.external.jobsources;

import com.resumebuddy.jobsearch.dto.adzuna.AdzunaJobDto;
import com.resumebuddy.jobsearch.dto.theirstack.TheirstackJobDto;
import com.resumebuddy.jobsearch.dto.theirstack.TheirstackSearchRequest;
import com.resumebuddy.jobsearch.dto.theirstack.TheirstackSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Infrastructure: Theirstack API Client
 * Fetches job listings from Theirstack API (50M+ jobs, 195 countries, 16+ job boards)
 *
 * Theirstack API uses Bearer token authentication via JWT
 * Documentation: https://api.theirstack.com/
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TheirstackApiClient implements JobSourceApiClient {

    private final RestClient restClient;

    @Value("${app.job-crawling.theirstack.api-key}")
    private String apiKey;

    @Value("${app.job-crawling.theirstack.base-url}")
    private String baseUrl;

    @Override
    public List<AdzunaJobDto> searchJobs(Map<String, Object> params) {
        try {
            String keywords = (String) params.getOrDefault("keywords", "");
            String location = (String) params.getOrDefault("location", "");
            Integer page = (Integer) params.getOrDefault("page", 1);
            Integer resultsPerPage = (Integer) params.getOrDefault("resultsPerPage", 50);
            Integer maxDaysOld = (Integer) params.getOrDefault("maxDaysOld", 7);
            Boolean remoteJobsOnly = (Boolean) params.get("remoteJobsOnly");

            log.info("Fetching jobs from Theirstack - keywords: {}, location: {}, page: {}, resultsPerPage: {}, maxDaysOld: {}",
                    keywords, location, page, resultsPerPage, maxDaysOld);

            // Build POST request body
            TheirstackSearchRequest request = buildSearchRequest(
                    keywords, location, page, resultsPerPage, maxDaysOld, remoteJobsOnly
            );

            log.debug("Theirstack request body: page={}, limit={}, job_title_or={}, country_code={}, posted_within_days={}",
                    request.getPage(), request.getLimit(), request.getJobTitleOr(),
                    request.getCompanyCountryCodeOr(), request.getPostedAtMaxAgeDays());

            // POST request to Theirstack API
            TheirstackSearchResponse response = restClient.post()
                    .uri(baseUrl + "/jobs/search")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(request)
                    .retrieve()
                    .body(TheirstackSearchResponse.class);

            if (response == null || response.getData() == null) {
                log.warn("No results from Theirstack API");
                return Collections.emptyList();
            }

            log.info("Fetched {} jobs from Theirstack (total: {}, truncated: {})",
                    response.getData().size(),
                    response.getMetadata().getTotalResults(),
                    response.getMetadata().getTruncatedResults());

            // Convert to common Adzuna format
            return response.getData().stream()
                    .map(this::convertToAdzunaFormat)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("CRITICAL: Theirstack API request failed - stopping job search process. Error: {}", e.getMessage(), e);
            // Immediately stop the entire process by re-throwing
            throw new RuntimeException("Theirstack API failure: " + e.getMessage(), e);
        }
    }

    @Override
    public String getSourceName() {
        return "THEIRSTACK";
    }

    /**
     * Build Theirstack search request body from parameters
     */
    private TheirstackSearchRequest buildSearchRequest(String keywords, String location,
                                                        Integer page, Integer limit,
                                                        Integer maxDaysOld, Boolean remoteJobsOnly) {
        TheirstackSearchRequest request = new TheirstackSearchRequest();

        // Pagination (convert 1-indexed to 0-indexed)
        request.setPage(page - 1);
        request.setLimit(limit);

        // Job title search
        if (keywords != null && !keywords.isEmpty()) {
            request.setJobTitleOr(List.of(keywords));
        }

        // Date filter (posted within last N days)
        if (maxDaysOld != null) {
            request.setPostedAtMaxAgeDays(maxDaysOld);
        }

        // Location handling (country code only - Theirstack doesn't support city filtering via location_or)
        String countryCode = extractCountryCode(location);
        request.setCompanyCountryCodeOr(List.of(countryCode));

        // NOTE: Theirstack API rejects location_or field, so we only filter by country code
        // City-specific filtering is not supported by their API

        // Remote jobs filter
        if (remoteJobsOnly != null && remoteJobsOnly) {
            request.setRemote(true);
        }

        return request;
    }

    /**
     * Extract country code from location string
     * Format: "gb:London" -> "GB"
     * Format: "United Kingdom" -> "GB"
     */
    private String extractCountryCode(String location) {
        if (location == null || location.isEmpty()) {
            return "GB"; // Default to UK
        }

        // Handle "gb:London" format
        if (location.contains(":")) {
            String code = location.split(":")[0];
            return code.toUpperCase();
        }

        // Map common country names to codes
        String loc = location.toLowerCase().trim();
        if (loc.contains("united kingdom") || loc.equals("uk")) {
            return "GB";
        }
        if (loc.contains("united states") || loc.equals("us") || loc.equals("usa")) {
            return "US";
        }
        if (loc.contains("canada")) {
            return "CA";
        }
        if (loc.contains("australia")) {
            return "AU";
        }
        if (loc.contains("germany")) {
            return "DE";
        }
        if (loc.contains("france")) {
            return "FR";
        }

        // If 2-letter code provided, use it
        if (location.length() == 2) {
            return location.toUpperCase();
        }

        // Default
        return "GB";
    }

    /**
     * Convert Theirstack job to common Adzuna format
     */
    private AdzunaJobDto convertToAdzunaFormat(TheirstackJobDto theirstackJob) {
        AdzunaJobDto dto = new AdzunaJobDto();

        // Basic fields
        dto.setId(String.valueOf(theirstackJob.getId()));
        dto.setTitle(theirstackJob.getJobTitle());
        dto.setDescription(theirstackJob.getDescription());

        // URL: Prefer source_url (actual job board) over Theirstack aggregator URL
        String jobUrl = theirstackJob.getSourceUrl() != null ?
                theirstackJob.getSourceUrl() : theirstackJob.getUrl();
        dto.setRedirectUrl(jobUrl);

        // Company
        if (theirstackJob.getCompany() != null) {
            AdzunaJobDto.AdzunaCompany company = new AdzunaJobDto.AdzunaCompany();
            company.setDisplayName(theirstackJob.getCompany());
            dto.setCompany(company);
        }

        // Location
        if (theirstackJob.getLocation() != null) {
            AdzunaJobDto.AdzunaLocation location = new AdzunaJobDto.AdzunaLocation();
            location.setDisplayName(theirstackJob.getLocation());
            dto.setLocation(location);
        }

        // Coordinates
        dto.setLatitude(theirstackJob.getLatitude());
        dto.setLongitude(theirstackJob.getLongitude());

        // Date posted (convert "2025-10-18" to ZonedDateTime)
        if (theirstackJob.getDatePosted() != null) {
            try {
                String isoDate = theirstackJob.getDatePosted() + "T00:00:00Z";
                dto.setCreated(ZonedDateTime.parse(isoDate));
            } catch (Exception e) {
                log.warn("Failed to parse date: {}", theirstackJob.getDatePosted());
            }
        }

        // Salary
        if (theirstackJob.getMinAnnualSalary() != null) {
            dto.setSalaryMin(theirstackJob.getMinAnnualSalary());
        }
        if (theirstackJob.getMaxAnnualSalary() != null) {
            dto.setSalaryMax(theirstackJob.getMaxAnnualSalary());
        }

        // Contract type (infer from remote/hybrid flags)
        if (Boolean.TRUE.equals(theirstackJob.getRemote())) {
            dto.setContractTime("remote");
        } else if (Boolean.TRUE.equals(theirstackJob.getHybrid())) {
            dto.setContractTime("hybrid");
        } else {
            dto.setContractTime("full_time");
        }

        return dto;
    }
}
