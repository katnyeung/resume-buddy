package com.resumebuddy.jobsearch.infrastructure.external.jobsources;

import com.resumebuddy.jobsearch.dto.adzuna.AdzunaJobDto;
import com.resumebuddy.jobsearch.dto.adzuna.AdzunaSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Infrastructure: Adzuna API Client
 * Fetches job listings from Adzuna job board API with web scraping for full descriptions
 *
 * Two-stage fetch (same pattern as Reed):
 * 1. API search - Get job list with truncated descriptions
 * 2. Web scraping - Follow redirect_url to partner sites for full descriptions
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AdzunaApiClient implements JobSourceApiClient {

    private final RestClient restClient;
    private final AdzunaWebScraperService scraperService;

    @Value("${app.job-crawling.adzuna.app-id}")
    private String appId;

    @Value("${app.job-crawling.adzuna.app-key}")
    private String appKey;

    @Value("${app.job-crawling.adzuna.base-url}")
    private String baseUrl;

    @Value("${app.job-crawling.adzuna.default-country:us}")
    private String defaultCountry;

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
            Integer maxDaysOld = (Integer) params.getOrDefault("maxDaysOld", 7); // Default to 7 days
            @SuppressWarnings("unchecked")
            java.util.List<String> excludeKeywords = (java.util.List<String>) params.get("excludeKeywords");

            // Parse location format: "gb:London" → countryCode="gb", cityRegion="London"
            String countryCode;
            String cityRegion = "";

            if (location != null && location.contains(":")) {
                // Format: "gb:London" - split into country code and city
                String[] parts = location.split(":", 2);
                countryCode = parts[0];
                cityRegion = parts.length > 1 ? parts[1] : "";
            } else if (location != null && location.length() == 2) {
                // Format: "gb" - country code only, no city
                countryCode = location;
                cityRegion = ""; // Don't use country code in 'where' parameter
            } else {
                // Format: "London" or "London, UK" - determine country and use location as city
                countryCode = determineCountryCode(location);
                // Clean up location for city name (remove country codes)
                cityRegion = location != null ? cleanCityName(location) : "";
            }

            log.info("Fetching jobs from Adzuna - keywords: {}, countryCode: {}, cityRegion: {}, page: {}, resultsPerPage: {}, maxDaysOld: {}",
                    keywords, countryCode, cityRegion, page, resultsPerPage, maxDaysOld);

            // Build URL: https://api.adzuna.com/v1/api/jobs/{country}/search/{page}
            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .pathSegment(countryCode, "search", String.valueOf(page))
                    .queryParam("app_id", appId)
                    .queryParam("app_key", appKey)
                    .queryParam("results_per_page", resultsPerPage)
                    .queryParam("what", keywords)
                    .queryParam("content-type", "application/json");

            // Only add 'where' parameter if we have a specific city/region
            if (!cityRegion.isEmpty()) {
                urlBuilder.queryParam("where", cityRegion);
            }

            String url = urlBuilder.build().toUriString();

            // Add optional filters
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            if (fullTimeOnly) {
                builder.queryParam("full_time", 1);
            }
            if (permanentOnly) {
                builder.queryParam("permanent", 1);
            }
            if (minSalary != null) {
                builder.queryParam("salary_min", minSalary);
            }
            if (excludeKeywords != null && !excludeKeywords.isEmpty()) {
                // Adzuna what_exclude format: space-separated keywords
                builder.queryParam("what_exclude", String.join(" ", excludeKeywords));
            }
            if (maxDaysOld != null) {
                builder.queryParam("max_days_old", maxDaysOld);
            }

            String finalUrl = builder.build().toUriString();
            log.debug("Adzuna API URL: {}", finalUrl);

            // Make HTTP GET request
            AdzunaSearchResponse response = restClient.get()
                    .uri(finalUrl)
                    .retrieve()
                    .body(AdzunaSearchResponse.class);

            if (response == null || response.getResults() == null) {
                log.warn("No results from Adzuna API");
                return Collections.emptyList();
            }

            log.info("Fetched {} jobs from Adzuna (total count: {})",
                    response.getResults().size(), response.getCount());

            // Two-stage fetch: Scrape full descriptions from redirect URLs (same pattern as Reed)
            List<AdzunaJobDto> enrichedJobs = new java.util.ArrayList<>();
            int jobCount = 0;
            int successfulScrapes = 0;

            for (AdzunaJobDto job : response.getResults()) {
                try {
                    jobCount++;
                    log.debug("Scraping full description {}/{}: {} (ID: {})",
                            jobCount, response.getResults().size(), job.getTitle(), job.getId());

                    // Scrape full description from partner site
                    String fullDescription = scraperService.scrapeJobDescription(job.getRedirectUrl());

                    if (fullDescription != null && !fullDescription.isEmpty()) {
                        String originalDesc = job.getDescription();
                        job.setDescription(fullDescription);
                        successfulScrapes++;
                        log.debug("Replaced truncated description ({} chars) with full description ({} chars)",
                                originalDesc != null ? originalDesc.length() : 0, fullDescription.length());
                    } else {
                        log.warn("Scraping failed for job {}, keeping API description", job.getId());
                    }

                    enrichedJobs.add(job);

                    // Rate limiting: 800ms delay between scrapes (same as Reed's 500ms)
                    if (jobCount < response.getResults().size()) {
                        Thread.sleep(800);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Scraping interrupted for job {}", job.getId());
                    enrichedJobs.add(job); // Keep job with API description
                    break;
                } catch (Exception e) {
                    log.error("Failed to scrape job {}: {}", job.getId(), e.getMessage());
                    enrichedJobs.add(job); // Keep job with API description
                }
            }

            log.info("Successfully scraped full descriptions for {}/{} Adzuna jobs",
                    successfulScrapes, response.getResults().size());

            return enrichedJobs;

        } catch (Exception e) {
            log.error("CRITICAL: Adzuna API request failed - stopping job search process. Error: {}", e.getMessage(), e);
            // Immediately stop the entire process by re-throwing
            throw new RuntimeException("Adzuna API failure: " + e.getMessage(), e);
        }
    }

    @Override
    public String getSourceName() {
        return "ADZUNA";
    }

    /**
     * Determine Adzuna country code from location string
     * Adzuna uses 2-letter country codes in URL path (gb, us, au, ca, etc.)
     */
    private String determineCountryCode(String location) {
        if (location == null || location.isEmpty()) {
            return defaultCountry;
        }

        String loc = location.toLowerCase().trim();

        // Direct country code mapping
        if (loc.equals("gb") || loc.equals("uk") || loc.contains("united kingdom") || loc.contains("britain")) {
            return "gb";
        }
        if (loc.equals("us") || loc.equals("usa") || loc.contains("united states") || loc.contains("america")) {
            return "us";
        }
        if (loc.equals("au") || loc.equals("australia") || loc.contains("australia")) {
            return "au";
        }
        if (loc.equals("ca") || loc.equals("canada") || loc.contains("canada")) {
            return "ca";
        }
        if (loc.equals("de") || loc.equals("germany") || loc.contains("germany")) {
            return "de";
        }
        if (loc.equals("fr") || loc.equals("france") || loc.contains("france")) {
            return "fr";
        }
        if (loc.equals("nl") || loc.equals("netherlands") || loc.contains("netherlands")) {
            return "nl";
        }
        if (loc.equals("in") || loc.equals("india") || loc.contains("india")) {
            return "in";
        }
        if (loc.equals("nz") || loc.equals("new zealand") || loc.contains("new zealand")) {
            return "nz";
        }
        if (loc.equals("za") || loc.equals("south africa") || loc.contains("south africa")) {
            return "za";
        }
        if (loc.equals("sg") || loc.equals("singapore") || loc.contains("singapore")) {
            return "sg";
        }
        if (loc.equals("ie") || loc.equals("ireland") || loc.contains("ireland")) {
            return "ie";
        }

        // If it's a 2-letter code we don't recognize, assume it's a valid country code
        if (loc.length() == 2) {
            return loc;
        }

        // Default fallback
        log.warn("Could not determine country code for location: {}, using default: {}", location, defaultCountry);
        return defaultCountry;
    }

    /**
     * Clean city name for 'where' parameter
     * Removes country suffixes and cleans up the city name
     */
    private String cleanCityName(String location) {
        if (location == null || location.isEmpty()) {
            return "";
        }

        String cleaned = location.trim();

        // Remove country name suffixes
        cleaned = cleaned.replaceAll("(?i),\\s*(uk|united kingdom|gb|great britain)$", "");
        cleaned = cleaned.replaceAll("(?i),\\s*(usa|us|united states)$", "");
        cleaned = cleaned.replaceAll("(?i),\\s*(australia|au)$", "");
        cleaned = cleaned.replaceAll("(?i),\\s*(canada|ca)$", "");

        // Remove country codes if they appear at the start
        if (cleaned.matches("^(gb|us|au|ca|de|fr|nl|in|nz|za|sg|ie)\\s+.*")) {
            cleaned = cleaned.substring(2).trim();
        }

        return cleaned.trim();
    }
}
