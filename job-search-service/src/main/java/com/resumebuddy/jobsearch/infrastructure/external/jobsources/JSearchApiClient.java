package com.resumebuddy.jobsearch.infrastructure.external.jobsources;

import com.resumebuddy.jobsearch.dto.adzuna.AdzunaJobDto;
import com.resumebuddy.jobsearch.dto.jsearch.JSearchJobDto;
import com.resumebuddy.jobsearch.dto.jsearch.JSearchSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Infrastructure: JSearch (RapidAPI) API Client
 * Fetches job listings from JSearch API (aggregates Google Jobs, Indeed, LinkedIn, etc.)
 *
 * JSearch API uses RapidAPI headers (X-RapidAPI-Key, X-RapidAPI-Host)
 * Documentation: https://rapidapi.com/letscrape-6bRBa3QguO5/api/jsearch
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JSearchApiClient implements JobSourceApiClient {

    private final RestClient restClient;

    @Value("${app.job-crawling.jsearch.api-key}")
    private String apiKey;

    @Value("${app.job-crawling.jsearch.base-url}")
    private String baseUrl;

    @Value("${app.job-crawling.jsearch.rapidapi-host}")
    private String rapidApiHost;

    @Override
    public List<AdzunaJobDto> searchJobs(Map<String, Object> params) {
        try {
            String keywords = (String) params.getOrDefault("keywords", "");
            String location = (String) params.getOrDefault("location", "");
            Integer page = (Integer) params.getOrDefault("page", 1);
            Integer maxResults = (Integer) params.getOrDefault("maxResults", 50);
            Integer maxDaysOld = (Integer) params.getOrDefault("maxDaysOld", 7);

            // Build query string: JSearch uses natural language queries
            // "java developer in london" vs separate keywords + location
            String query = buildQueryString(keywords, location);

            // Extract country code from location (e.g., "gb:London" -> "gb")
            String country = extractCountryCode(location);

            // Map maxDaysOld to JSearch date_posted parameter
            String datePosted = mapMaxDaysOldToDatePosted(maxDaysOld);

            log.info("Fetching jobs from JSearch - query: {}, country: {}, page: {}, date_posted: {}",
                    query, country, page, datePosted);

            // Build URL: https://jsearch.p.rapidapi.com/search
            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .pathSegment("search")
                    .queryParam("query", query)
                    .queryParam("page", page)
                    .queryParam("num_pages", 1); // Always fetch 1 page per request

            if (country != null && !country.isEmpty()) {
                urlBuilder.queryParam("country", country);
            }

            urlBuilder.queryParam("date_posted", datePosted);

            String finalUrl = urlBuilder.build().toUriString();
            log.debug("JSearch API URL: {}", finalUrl);

            // Make HTTP GET request with RapidAPI headers
            JSearchSearchResponse response = restClient.get()
                    .uri(finalUrl)
                    .header("X-RapidAPI-Key", apiKey)
                    .header("X-RapidAPI-Host", rapidApiHost)
                    .retrieve()
                    .body(JSearchSearchResponse.class);

            if (response == null || response.getData() == null) {
                log.warn("No results from JSearch API");
                return Collections.emptyList();
            }

            if (!"OK".equals(response.getStatus())) {
                log.error("JSearch API returned non-OK status: {}", response.getStatus());
                throw new RuntimeException("JSearch API error: " + response.getStatus());
            }

            log.info("Fetched {} jobs from JSearch (request_id: {})",
                    response.getData().size(), response.getRequestId());

            // Limit results to maxResults (JSearch doesn't support this parameter directly)
            List<JSearchJobDto> jobs = response.getData();
            if (jobs.size() > maxResults) {
                jobs = jobs.subList(0, maxResults);
                log.info("Trimmed results to {} jobs", maxResults);
            }

            // Convert JSearch jobs to Adzuna format (common DTO)
            return jobs.stream()
                    .map(this::convertToAdzunaFormat)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("CRITICAL: JSearch API request failed - stopping job search process. Error: {}", e.getMessage(), e);
            // Immediately stop the entire process by re-throwing
            throw new RuntimeException("JSearch API failure: " + e.getMessage(), e);
        }
    }

    @Override
    public String getSourceName() {
        return "JSEARCH";
    }

    /**
     * Build query string from keywords and location
     * JSearch uses natural language queries like "java developer in london"
     */
    private String buildQueryString(String keywords, String location) {
        if (keywords == null || keywords.isEmpty()) {
            return "";
        }

        // Extract city from location if in "countryCode:city" format
        String city = null;
        if (location != null && location.contains(":")) {
            city = location.split(":", 2)[1];
        } else if (location != null && !location.isEmpty()) {
            city = location;
        }

        // Build natural language query
        if (city != null && !city.isEmpty()) {
            return keywords + " in " + city;
        } else {
            return keywords;
        }
    }

    /**
     * Extract country code from location string
     * "gb:London" -> "gb"
     * "London" -> null (let JSearch infer)
     */
    private String extractCountryCode(String location) {
        if (location == null || location.isEmpty()) {
            return null;
        }

        if (location.contains(":")) {
            return location.split(":", 2)[0];
        }

        // If no country code, return null to let JSearch infer from query
        return null;
    }

    /**
     * Map maxDaysOld to JSearch date_posted parameter
     *
     * @param maxDaysOld Number of days
     * @return JSearch date_posted value: "today", "3days", "week", "month", "all"
     */
    private String mapMaxDaysOldToDatePosted(Integer maxDaysOld) {
        if (maxDaysOld == null) {
            return "all";
        }

        if (maxDaysOld <= 1) {
            return "today";
        } else if (maxDaysOld <= 3) {
            return "3days";
        } else if (maxDaysOld <= 7) {
            return "week";
        } else if (maxDaysOld <= 30) {
            return "month";
        } else {
            return "all";
        }
    }

    /**
     * Convert JSearch job DTO to Adzuna job DTO (common format)
     * This allows the rest of the system to work with a single DTO type
     */
    private AdzunaJobDto convertToAdzunaFormat(JSearchJobDto jsearchJob) {
        AdzunaJobDto adzunaJob = new AdzunaJobDto();

        // Basic fields
        adzunaJob.setId(jsearchJob.getJobId());
        adzunaJob.setTitle(jsearchJob.getJobTitle());
        adzunaJob.setDescription(jsearchJob.getJobDescription());

        // Convert ISO 8601 date string to ZonedDateTime
        if (jsearchJob.getJobPostedAtDatetimeUtc() != null) {
            try {
                ZonedDateTime zonedDateTime = ZonedDateTime.parse(
                        jsearchJob.getJobPostedAtDatetimeUtc(),
                        DateTimeFormatter.ISO_DATE_TIME
                );
                adzunaJob.setCreated(zonedDateTime);
            } catch (Exception e) {
                log.warn("Failed to parse JSearch date: {}", jsearchJob.getJobPostedAtDatetimeUtc(), e);
            }
        }

        // Apply link (prefer direct if available, otherwise use job_apply_link)
        if (jsearchJob.getJobApplyLink() != null) {
            adzunaJob.setRedirectUrl(jsearchJob.getJobApplyLink());
        } else if (jsearchJob.getJobGoogleLink() != null) {
            adzunaJob.setRedirectUrl(jsearchJob.getJobGoogleLink());
        }

        // Company
        if (jsearchJob.getEmployerName() != null) {
            AdzunaJobDto.AdzunaCompany company = new AdzunaJobDto.AdzunaCompany();
            company.setDisplayName(jsearchJob.getEmployerName());
            adzunaJob.setCompany(company);
        }

        // Location (combine city and country if available)
        if (jsearchJob.getJobLocation() != null) {
            AdzunaJobDto.AdzunaLocation location = new AdzunaJobDto.AdzunaLocation();
            location.setDisplayName(jsearchJob.getJobLocation());
            adzunaJob.setLocation(location);
        } else if (jsearchJob.getJobCity() != null || jsearchJob.getJobCountry() != null) {
            AdzunaJobDto.AdzunaLocation location = new AdzunaJobDto.AdzunaLocation();
            String displayName = jsearchJob.getJobCity() != null ? jsearchJob.getJobCity() : "";
            if (jsearchJob.getJobCountry() != null) {
                displayName += displayName.isEmpty() ? jsearchJob.getJobCountry() : ", " + jsearchJob.getJobCountry();
            }
            location.setDisplayName(displayName);
            adzunaJob.setLocation(location);
        }

        // Salary
        adzunaJob.setSalaryMin(jsearchJob.getJobMinSalary());
        adzunaJob.setSalaryMax(jsearchJob.getJobMaxSalary());

        // Contract type - map from job_employment_type
        if (jsearchJob.getJobEmploymentType() != null) {
            String employmentType = jsearchJob.getJobEmploymentType().toLowerCase();
            if (employmentType.contains("full")) {
                adzunaJob.setContractTime("full_time");
            } else if (employmentType.contains("part")) {
                adzunaJob.setContractTime("part_time");
            }

            if (employmentType.contains("permanent")) {
                adzunaJob.setContractType("permanent");
            } else if (employmentType.contains("contract")) {
                adzunaJob.setContractType("contract");
            }
        }

        // Latitude/Longitude
        adzunaJob.setLatitude(jsearchJob.getJobLatitude());
        adzunaJob.setLongitude(jsearchJob.getJobLongitude());

        return adzunaJob;
    }
}
