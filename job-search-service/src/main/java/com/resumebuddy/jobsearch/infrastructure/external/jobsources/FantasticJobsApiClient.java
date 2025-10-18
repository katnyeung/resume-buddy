package com.resumebuddy.jobsearch.infrastructure.external.jobsources;

import com.resumebuddy.jobsearch.dto.adzuna.AdzunaJobDto;
import com.resumebuddy.jobsearch.dto.fantasticjobs.FantasticJobsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Infrastructure: Fantastic Jobs LinkedIn API Client (RapidAPI)
 * Fetches job listings from LinkedIn via Fantastic Jobs API
 *
 * API: https://rapidapi.com/fantastic-jobs-fantastic-jobs-default/api/linkedin-job-search-api
 * Features:
 * - 20,000+ jobs indexed hourly from LinkedIn
 * - Jobs from last 7 days (2M+ jobs/week)
 * - Full job descriptions
 * - Company details (industry, followers, specialties)
 * - Up to 100 jobs per request
 *
 * Similar to JSearch but different provider with LinkedIn focus
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FantasticJobsApiClient implements JobSourceApiClient {

    private final RestClient restClient;

    @Value("${app.job-crawling.fantasticjobs.api-key}")
    private String apiKey;

    @Value("${app.job-crawling.fantasticjobs.base-url}")
    private String baseUrl;

    @Value("${app.job-crawling.fantasticjobs.rapidapi-host}")
    private String rapidApiHost;

    @Override
    public List<AdzunaJobDto> searchJobs(Map<String, Object> params) {
        try {
            String keywords = (String) params.getOrDefault("keywords", "");
            String location = (String) params.getOrDefault("location", "");
            Integer page = (Integer) params.getOrDefault("page", 1);
            Integer maxResults = (Integer) params.getOrDefault("maxResults", 50);
            Integer maxDaysOld = (Integer) params.getOrDefault("maxDaysOld", 7);
            Boolean remoteJobsOnly = (Boolean) params.get("remoteJobsOnly");
            String employmentTypes = (String) params.get("employmentTypes");
            String experienceLevel = (String) params.get("experienceLevel");

            log.info("Fetching jobs from Fantastic Jobs (LinkedIn) - keywords: {}, location: {}, page: {}, remote: {}, types: {}",
                    keywords, location, page, remoteJobsOnly, employmentTypes);

            // Build URL: https://linkedin-job-search-api.p.rapidapi.com/active-jb-24h
            // Note: Using active-jb-24h endpoint (jobs posted in last 24 hours)
            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .pathSegment("active-jb-24h");

            // Limit (max results per request)
            urlBuilder.queryParam("limit", Math.min(maxResults, 100));

            // Offset (pagination)
            int offset = (page - 1) * Math.min(maxResults, 100);
            if (offset > 0) {
                urlBuilder.queryParam("offset", offset);
            }

            // Title filter (keywords wrapped in quotes for exact match)
            if (keywords != null && !keywords.isEmpty()) {
                String titleFilter = String.format("\"%s\"", keywords);
                urlBuilder.queryParam("title_filter", titleFilter);
            }

            // Location filter (wrapped in quotes)
            if (location != null && !location.isEmpty()) {
                // Extract city from "countryCode:city" format if present
                String cleanLocation = location;
                if (location.contains(":")) {
                    cleanLocation = location.split(":", 2)[1];
                }
                String locationFilter = String.format("\"%s\"", cleanLocation);
                urlBuilder.queryParam("location_filter", locationFilter);
            }

            // Description type (text or html)
            urlBuilder.queryParam("description_type", "text");

            String finalUrl = urlBuilder.build().toUriString();
            log.debug("Fantastic Jobs API URL: {}", finalUrl);

            // Make HTTP GET request with RapidAPI headers
            // Note: API returns a JSON array directly, not an object wrapper
            FantasticJobsDto[] responseArray = restClient.get()
                    .uri(finalUrl)
                    .header("X-RapidAPI-Key", apiKey)
                    .header("X-RapidAPI-Host", rapidApiHost)
                    .retrieve()
                    .body(FantasticJobsDto[].class);

            if (responseArray == null || responseArray.length == 0) {
                log.warn("No results from Fantastic Jobs API");
                return Collections.emptyList();
            }

            log.info("Fetched {} jobs from Fantastic Jobs (LinkedIn)", responseArray.length);

            // Convert array to list and limit results
            List<FantasticJobsDto> jobs = java.util.Arrays.asList(responseArray);
            if (jobs.size() > maxResults) {
                jobs = jobs.subList(0, maxResults);
                log.info("Trimmed results to {} jobs", maxResults);
            }

            // Convert to Adzuna format (common DTO)
            return jobs.stream()
                    .map(this::convertToAdzunaFormat)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("CRITICAL: Fantastic Jobs API request failed - stopping process. Error: {}",
                    e.getMessage(), e);
            throw new RuntimeException("Fantastic Jobs API failure: " + e.getMessage(), e);
        }
    }

    @Override
    public String getSourceName() {
        return "FANTASTICJOBS";
    }

    /**
     * Map common employment type formats to LinkedIn format
     * Input: "FULLTIME,CONTRACTOR" (JSearch format)
     * Output: "Full-time,Contract" (LinkedIn format)
     */
    private String mapEmploymentTypes(String employmentTypes) {
        if (employmentTypes == null || employmentTypes.isEmpty()) {
            return null;
        }

        return employmentTypes
                .replace("FULLTIME", "Full-time")
                .replace("PARTTIME", "Part-time")
                .replace("CONTRACTOR", "Contract")
                .replace("INTERN", "Internship")
                .replace("TEMPORARY", "Temporary");
    }

    /**
     * Convert Fantastic Jobs DTO to Adzuna format (common DTO)
     */
    private AdzunaJobDto convertToAdzunaFormat(FantasticJobsDto fjJob) {
        AdzunaJobDto adzunaJob = new AdzunaJobDto();

        // Basic fields
        adzunaJob.setId(fjJob.getJobId());
        adzunaJob.setTitle(fjJob.getTitle());
        adzunaJob.setDescription(fjJob.getDescription());

        // Date - handle both ISO string and Unix timestamp
        if (fjJob.getPostedDate() != null) {
            try {
                ZonedDateTime zonedDateTime = ZonedDateTime.parse(
                        fjJob.getPostedDate(),
                        DateTimeFormatter.ISO_DATE_TIME
                );
                adzunaJob.setCreated(zonedDateTime);
            } catch (Exception e) {
                log.debug("Failed to parse ISO date: {}", fjJob.getPostedDate());
            }
        } else if (fjJob.getPostedAt() != null) {
            try {
                // date_created is also ISO format (e.g., "2025-10-18T13:35:23.722815")
                ZonedDateTime zonedDateTime = ZonedDateTime.parse(fjJob.getPostedAt());
                adzunaJob.setCreated(zonedDateTime);
            } catch (Exception e) {
                log.debug("Failed to parse date_created ISO date: {}", fjJob.getPostedAt());
            }
        }

        // Apply link (prefer direct apply, fallback to job URL)
        if (fjJob.getApplyLink() != null) {
            adzunaJob.setRedirectUrl(fjJob.getApplyLink());
        } else if (fjJob.getJobUrl() != null) {
            adzunaJob.setRedirectUrl(fjJob.getJobUrl());
        }

        // Company
        if (fjJob.getCompany() != null) {
            AdzunaJobDto.AdzunaCompany company = new AdzunaJobDto.AdzunaCompany();
            company.setDisplayName(fjJob.getCompany());
            adzunaJob.setCompany(company);
        }

        // Location - locations_derived is an array, take first element
        if (fjJob.getLocation() != null && !fjJob.getLocation().isEmpty()) {
            AdzunaJobDto.AdzunaLocation location = new AdzunaJobDto.AdzunaLocation();
            location.setDisplayName(fjJob.getLocation().get(0)); // Take first location
            adzunaJob.setLocation(location);
        } else {
            // Build location from components
            StringBuilder locationBuilder = new StringBuilder();
            if (fjJob.getCity() != null) {
                locationBuilder.append(fjJob.getCity());
            }
            if (fjJob.getState() != null) {
                if (locationBuilder.length() > 0) locationBuilder.append(", ");
                locationBuilder.append(fjJob.getState());
            }
            if (fjJob.getCountry() != null) {
                if (locationBuilder.length() > 0) locationBuilder.append(", ");
                locationBuilder.append(fjJob.getCountry());
            }
            if (locationBuilder.length() > 0) {
                AdzunaJobDto.AdzunaLocation location = new AdzunaJobDto.AdzunaLocation();
                location.setDisplayName(locationBuilder.toString());
                adzunaJob.setLocation(location);
            }
        }

        // Salary
        adzunaJob.setSalaryMin(fjJob.getSalaryMin());
        adzunaJob.setSalaryMax(fjJob.getSalaryMax());

        // Contract type - employment_type is an array, take first element
        if (fjJob.getEmploymentType() != null && !fjJob.getEmploymentType().isEmpty()) {
            String empType = fjJob.getEmploymentType().get(0).toLowerCase(); // Take first type
            if (empType.contains("full")) {
                adzunaJob.setContractTime("full_time");
                adzunaJob.setContractType("permanent");
            } else if (empType.contains("part")) {
                adzunaJob.setContractTime("part_time");
            } else if (empType.contains("contract")) {
                adzunaJob.setContractType("contract");
            } else if (empType.contains("temporary") || empType.contains("temp")) {
                adzunaJob.setContractType("temporary");
            } else if (empType.contains("intern")) {
                adzunaJob.setContractType("internship");
            }
        }

        // Job type fallback
        if (fjJob.getJobType() != null && adzunaJob.getContractType() == null) {
            String jobType = fjJob.getJobType().toLowerCase();
            if (jobType.contains("permanent")) {
                adzunaJob.setContractType("permanent");
            } else if (jobType.contains("contract")) {
                adzunaJob.setContractType("contract");
            }
        }

        // Latitude/Longitude
        adzunaJob.setLatitude(fjJob.getLatitude());
        adzunaJob.setLongitude(fjJob.getLongitude());

        // Category (use industry or job function)
        if (fjJob.getIndustry() != null) {
            AdzunaJobDto.AdzunaCategory category = new AdzunaJobDto.AdzunaCategory();
            category.setLabel(fjJob.getIndustry());
            adzunaJob.setCategory(category);
        } else if (fjJob.getJobFunction() != null) {
            AdzunaJobDto.AdzunaCategory category = new AdzunaJobDto.AdzunaCategory();
            category.setLabel(fjJob.getJobFunction());
            adzunaJob.setCategory(category);
        }

        return adzunaJob;
    }
}
