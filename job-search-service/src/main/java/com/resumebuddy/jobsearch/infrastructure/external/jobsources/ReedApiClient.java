package com.resumebuddy.jobsearch.infrastructure.external.jobsources;

import com.resumebuddy.jobsearch.dto.adzuna.AdzunaJobDto;
import com.resumebuddy.jobsearch.dto.reed.ReedJobDto;
import com.resumebuddy.jobsearch.dto.reed.ReedSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Infrastructure: Reed.co.uk API Client
 * Fetches job listings from Reed UK job board API
 *
 * Reed API uses Basic Auth with API key as username (password blank)
 * Documentation: https://www.reed.co.uk/developers/jobseeker
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReedApiClient implements JobSourceApiClient {

    private final RestClient restClient;

    @Value("${app.job-crawling.reed.api-key}")
    private String apiKey;

    @Value("${app.job-crawling.reed.base-url}")
    private String baseUrl;

    @Override
    public List<AdzunaJobDto> searchJobs(Map<String, Object> params) {
        try {
            String keywords = (String) params.getOrDefault("keywords", "");
            String location = (String) params.getOrDefault("location", "");
            Integer page = (Integer) params.getOrDefault("page", 1);
            Integer resultsPerPage = (Integer) params.getOrDefault("resultsPerPage", 50);
            Boolean fullTimeOnly = (Boolean) params.getOrDefault("fullTimeOnly", false);
            Boolean permanentOnly = (Boolean) params.getOrDefault("permanentOnly", false);
            Integer minSalary = (Integer) params.get("minSalary");
            Integer maxDaysOld = (Integer) params.getOrDefault("maxDaysOld", 7);

            log.info("Fetching jobs from Reed.co.uk - keywords: {}, location: {}, resultsPerPage: {}, maxDaysOld: {}",
                    keywords, location, resultsPerPage, maxDaysOld);

            // Build URL: https://www.reed.co.uk/api/1.0/search
            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .pathSegment("search")
                    .queryParam("keywords", keywords)
                    .queryParam("resultsToTake", resultsPerPage)
                    .queryParam("resultsToSkip", (page - 1) * resultsPerPage);

            // Location parsing for Reed
            // Format: "gb:London" → use "London"
            // Format: "London" → use "London"
            String cleanLocation = location;
            if (location != null && location.contains(":")) {
                cleanLocation = location.split(":", 2)[1];
            }
            if (cleanLocation != null && !cleanLocation.isEmpty()) {
                urlBuilder.queryParam("locationName", cleanLocation);
            }

            // Optional filters
            if (fullTimeOnly) {
                urlBuilder.queryParam("fullTime", true);
            }
            if (permanentOnly) {
                urlBuilder.queryParam("permanent", true);
            }
            if (minSalary != null) {
                urlBuilder.queryParam("minimumSalary", minSalary);
            }

            // Distance from location (miles) - default to 10 miles
            urlBuilder.queryParam("distanceFromLocation", 10);

            String finalUrl = urlBuilder.build().toUriString();
            log.debug("Reed API URL: {}", finalUrl);

            // Make HTTP GET request with Basic Auth (API key as username, blank password)
            ReedSearchResponse response = restClient.get()
                    .uri(finalUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " +
                            java.util.Base64.getEncoder().encodeToString((apiKey + ":").getBytes()))
                    .retrieve()
                    .body(ReedSearchResponse.class);

            if (response == null || response.getResults() == null) {
                log.warn("No results from Reed API");
                return Collections.emptyList();
            }

            log.info("Fetched {} jobs from Reed.co.uk (total count: {})",
                    response.getResults().size(), response.getTotalResults());

            // Convert Reed jobs to Adzuna format (common DTO)
            return response.getResults().stream()
                    .map(this::convertToAdzunaFormat)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("CRITICAL: Reed API request failed - stopping job search process. Error: {}", e.getMessage(), e);
            // Immediately stop the entire process by re-throwing
            throw new RuntimeException("Reed API failure: " + e.getMessage(), e);
        }
    }

    @Override
    public String getSourceName() {
        return "REED";
    }

    /**
     * Convert Reed job DTO to Adzuna job DTO (common format)
     * This allows the rest of the system to work with a single DTO type
     */
    private AdzunaJobDto convertToAdzunaFormat(ReedJobDto reedJob) {
        AdzunaJobDto adzunaJob = new AdzunaJobDto();

        // Basic fields
        adzunaJob.setId(String.valueOf(reedJob.getJobId()));
        adzunaJob.setTitle(reedJob.getJobTitle());
        adzunaJob.setDescription(reedJob.getJobDescription());

        // Convert Reed date string (dd/MM/yyyy) to ZonedDateTime
        if (reedJob.getDate() != null) {
            try {
                java.time.format.DateTimeFormatter formatter =
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
                java.time.LocalDate localDate = java.time.LocalDate.parse(reedJob.getDate(), formatter);
                adzunaJob.setCreated(localDate.atStartOfDay(java.time.ZoneId.systemDefault()));
            } catch (Exception e) {
                log.warn("Failed to parse Reed date: {}", reedJob.getDate(), e);
            }
        }

        adzunaJob.setRedirectUrl(reedJob.getJobUrl());

        // Company
        if (reedJob.getEmployerName() != null) {
            AdzunaJobDto.AdzunaCompany company = new AdzunaJobDto.AdzunaCompany();
            company.setDisplayName(reedJob.getEmployerName());
            adzunaJob.setCompany(company);
        }

        // Location
        if (reedJob.getLocationName() != null) {
            AdzunaJobDto.AdzunaLocation location = new AdzunaJobDto.AdzunaLocation();
            location.setDisplayName(reedJob.getLocationName());
            adzunaJob.setLocation(location);
        }

        // Salary
        adzunaJob.setSalaryMin(reedJob.getMinimumSalary());
        adzunaJob.setSalaryMax(reedJob.getMaximumSalary());

        // Contract type - Reed doesn't provide this in search results, determine from full_time/part_time
        if (Boolean.TRUE.equals(reedJob.getFullTime()) || Boolean.TRUE.equals(reedJob.getPartTime())) {
            adzunaJob.setContractType("permanent"); // Assume permanent if not specified
        }

        // Contract time (full_time vs part_time)
        if (Boolean.TRUE.equals(reedJob.getFullTime())) {
            adzunaJob.setContractTime("full_time");
        } else if (Boolean.TRUE.equals(reedJob.getPartTime())) {
            adzunaJob.setContractTime("part_time");
        }

        return adzunaJob;
    }
}
