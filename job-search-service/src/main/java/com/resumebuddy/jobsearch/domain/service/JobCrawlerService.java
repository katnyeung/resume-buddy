package com.resumebuddy.jobsearch.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.jobsearch.domain.JobListing;
import com.resumebuddy.jobsearch.domain.JobListingLine;
import com.resumebuddy.jobsearch.dto.adzuna.AdzunaJobDto;
import com.resumebuddy.jobsearch.infrastructure.external.jobsources.JobSourceApiClient;
import com.resumebuddy.jobsearch.repository.JobListingLineRepository;
import com.resumebuddy.jobsearch.repository.JobListingRepository;
import com.resumebuddy.jobsearch.service.VectorEmbeddingService;
import com.resumebuddy.jobsearch.service.RedisVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Domain Service: Job Crawler
 * Core crawling logic - fetch, dedupe, extract skills, vectorize, store
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobCrawlerService {

    private final JobListingRepository jobListingRepository;
    private final JobListingLineRepository jobListingLineRepository;
    private final VectorEmbeddingService vectorEmbeddingService;
    private final RedisVectorService redisVectorService;
    private final JobDescriptionParser jobDescriptionParser;
    private final ObjectMapper objectMapper;

    /**
     * Process jobs from external API client
     *
     * @param apiClient The job source API client
     * @param params Search parameters
     * @return Map with statistics (totalFetched, totalSaved, duplicatesSkipped, duplicatesUpdated, failedToProcess)
     */
    public Map<String, Integer> processJobs(JobSourceApiClient apiClient, Map<String, Object> params) {
        int totalFetched = 0;
        int totalSaved = 0;
        int duplicatesSkipped = 0;
        int duplicatesUpdated = 0;
        int failedToProcess = 0;

        // 1. Fetch jobs from API - this will throw immediately on any error
        List<AdzunaJobDto> jobs = apiClient.searchJobs(params);
        totalFetched = jobs.size();
        log.info("Fetched {} jobs from {}", totalFetched, apiClient.getSourceName());

        try {

            // 2. Fast pass: Save to MySQL without vectorization
            List<JobListing> savedListings = new ArrayList<>();
            List<String> descriptions = new ArrayList<>();

            for (AdzunaJobDto job : jobs) {
                try {
                    // Filter out non-technical roles (post-processing)
                    if (!isRelevantJob(job)) {
                        failedToProcess++;
                        continue;
                    }

                    // Map to JobListing entity
                    JobListing listing = mapToJobListing(job, apiClient.getSourceName());

                    // Generate content hash for duplicate detection
                    String contentHash = generateContentHash(listing.getCompany(), listing.getTitle(), listing.getLocation());
                    listing.setContentHash(contentHash);

                    // Check for duplicates by content hash
                    Optional<JobListing> existingJob = jobListingRepository.findByContentHash(contentHash);
                    if (existingJob.isPresent()) {
                        // Update existing job instead of skipping
                        JobListing existing = existingJob.get();
                        if (shouldUpdateExistingJob(existing, listing)) {
                            updateExistingJob(existing, listing);
                            jobListingRepository.save(existing);
                            duplicatesUpdated++;
                            log.debug("Updated existing job: {} at {}", existing.getTitle(), existing.getCompany());
                        } else {
                            duplicatesSkipped++;
                            log.debug("Skipping unchanged duplicate job: {}", listing.getTitle());
                        }
                        continue;
                    }

                    // Set lastSeenAt for new jobs
                    listing.setLastSeenAt(LocalDateTime.now());

                    // Save to MySQL WITHOUT vectorization (fast!)
                    listing = jobListingRepository.save(listing);
                    savedListings.add(listing);
                    descriptions.add(job.getDescription());

                    totalSaved++;
                    log.debug("Saved new job listing (without vector): {} at {}", listing.getTitle(), listing.getCompany());

                } catch (Exception e) {
                    failedToProcess++;
                    log.error("Failed to process job: {}", job.getTitle(), e);
                }
            }

            log.info("Fast pass complete - Saved {} jobs to MySQL. Starting batch vectorization...", totalSaved);

            // 3. Batch vectorization (async, after MySQL save)
            if (!savedListings.isEmpty()) {
                batchVectorizeListings(savedListings, descriptions);
            }

            log.info("Processing complete - Fetched: {}, Saved: {}, Updated: {}, Duplicates: {}, Failed: {}",
                    totalFetched, totalSaved, duplicatesUpdated, duplicatesSkipped, failedToProcess);

        } catch (Exception e) {
            log.error("CRITICAL: Job processing failed after fetching {} jobs. Saved: {}, Error: {}",
                    totalFetched, totalSaved, e.getMessage(), e);
            throw new RuntimeException("Job processing failed: " + e.getMessage(), e);
        }

        Map<String, Integer> stats = new HashMap<>();
        stats.put("totalFetched", totalFetched);
        stats.put("totalSaved", totalSaved);
        stats.put("duplicatesUpdated", duplicatesUpdated);
        stats.put("duplicatesSkipped", duplicatesSkipped);
        stats.put("failedToProcess", failedToProcess);
        return stats;
    }

    /**
     * Batch vectorize job listing lines using OpenAI batch API
     * Only creates line-level vectors (no full-doc vectors)
     */
    private void batchVectorizeListings(List<JobListing> listings, List<String> descriptions) {
        try {
            log.info("Starting batch vectorization for {} listings (lines only)", listings.size());
            long startTime = System.currentTimeMillis();

            // Phase 1: Parse descriptions into lines
            log.info("Phase 1: Parsing descriptions into lines");
            List<JobListingLine> allLines = new ArrayList<>();
            List<String> allLineContents = new ArrayList<>();

            for (JobListing listing : listings) {
                try {
                    // Parse description into lines
                    List<String> parsedLines = jobDescriptionParser.parseDescription(listing.getDescription());

                    // Create JobListingLine entities
                    for (int lineNum = 0; lineNum < parsedLines.size(); lineNum++) {
                        JobListingLine line = new JobListingLine();
                        line.setListingId(listing.getId());
                        line.setLineNumber(lineNum + 1);
                        line.setLineContent(parsedLines.get(lineNum));

                        allLines.add(line);
                        allLineContents.add(parsedLines.get(lineNum));
                    }

                    log.debug("Parsed {} lines for listing: {}", parsedLines.size(), listing.getTitle());

                } catch (Exception e) {
                    log.error("Failed to parse description for listing: {}", listing.getId(), e);
                }
            }

            log.info("Phase 1 complete: Parsed {} total lines from {} listings", allLines.size(), listings.size());

            // Phase 2: Save lines to MySQL to get IDs
            if (!allLines.isEmpty()) {
                List<JobListingLine> savedLines = jobListingLineRepository.saveAll(allLines);
                log.info("Phase 2: Saved {} lines to MySQL, now generating embeddings", savedLines.size());

                // Phase 3: Batch vectorize all lines
                List<float[]> lineEmbeddings = vectorEmbeddingService.generateEmbeddings(allLineContents);

                if (lineEmbeddings.size() != savedLines.size()) {
                    log.error("Mismatch: {} lines but {} embeddings", savedLines.size(), lineEmbeddings.size());
                } else {
                    // Store line vectors in Redis using database IDs
                    for (int i = 0; i < savedLines.size(); i++) {
                        try {
                            JobListingLine line = savedLines.get(i);
                            float[] embedding = lineEmbeddings.get(i);

                            // CRITICAL: Use database ID in Redis key so matching can find the line
                            String redisKey = "listing:line:" + line.getId();
                            redisVectorService.storeVector(redisKey, embedding);

                            // Update line with Redis key
                            line.setRedisVectorKey(redisKey);

                        } catch (Exception e) {
                            log.error("Failed to store line vector: {}", savedLines.get(i).getLineContent(), e);
                        }
                    }

                    // Batch update redis keys
                    jobListingLineRepository.saveAll(savedLines);
                    log.info("Phase 3 complete: Line vectors stored");
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Batch vectorization complete for {} listings ({} lines) in {}ms (avg: {}ms per listing)",
                    listings.size(), allLines.size(), duration, duration / listings.size());

        } catch (Exception e) {
            log.error("Batch vectorization failed", e);
            // Don't throw - vectorization is optional, jobs are already saved
        }
    }

    /**
     * Check if job is relevant (filter out non-technical roles)
     */
    private boolean isRelevantJob(AdzunaJobDto job) {
        if (job == null) {
            return false;
        }

        String title = (job.getTitle() != null ? job.getTitle() : "").toLowerCase();
        String description = (job.getDescription() != null ? job.getDescription() : "").toLowerCase();
        String combined = title + " " + description;

        // Filter out obvious non-technical roles
        String[] excludePatterns = {
                "sales representative", "sales executive", "business development",
                "account manager", "marketing manager", "recruitment consultant",
                "estate agent", "letting agent", "insurance", "financial advisor"
        };

        for (String pattern : excludePatterns) {
            if (title.contains(pattern)) {
                log.debug("Filtered out non-technical job: {}", job.getTitle());
                return false;
            }
        }

        return true;
    }

    /**
     * Map Adzuna job DTO to JobListing entity
     */
    private JobListing mapToJobListing(AdzunaJobDto job, String source) throws Exception {
        JobListing listing = new JobListing();
        listing.setSource(source);
        listing.setTitle(job.getTitle());
        listing.setCompany(job.getCompany() != null ? job.getCompany().getDisplayName() : null);
        listing.setLocation(job.getLocation() != null ? job.getLocation().getDisplayName() : null);
        listing.setDescription(job.getDescription());
        listing.setUrl(job.getRedirectUrl());

        // Salary range
        if (job.getSalaryMin() != null && job.getSalaryMax() != null) {
            listing.setSalaryRange(String.format("$%.0f - $%.0f", job.getSalaryMin(), job.getSalaryMax()));
        } else if (job.getSalaryMin() != null) {
            listing.setSalaryRange(String.format("From $%.0f", job.getSalaryMin()));
        } else if (job.getSalaryMax() != null) {
            listing.setSalaryRange(String.format("Up to $%.0f", job.getSalaryMax()));
        }

        // Contract type (combine contract_type and contract_time)
        String contractType = job.getContractType();
        String contractTime = job.getContractTime();
        if (contractType != null && contractTime != null) {
            listing.setContractType(contractType + ", " + contractTime);
        } else if (contractType != null) {
            listing.setContractType(contractType);
        } else if (contractTime != null) {
            listing.setContractType(contractTime);
        }

        // Posted date
        if (job.getCreated() != null) {
            listing.setPostedDate(LocalDateTime.ofInstant(
                    job.getCreated().toInstant(), ZoneId.systemDefault()));
        }

        // Store complete raw data as JSON
        listing.setRawData(objectMapper.writeValueAsString(job));

        return listing;
    }

    /**
     * Generate content hash for duplicate detection
     * Hash = SHA-256(company + title + location)
     */
    private String generateContentHash(String company, String title, String location) {
        try {
            String content = String.format("%s|%s|%s",
                    company != null ? company.toLowerCase().trim() : "",
                    title != null ? title.toLowerCase().trim() : "",
                    location != null ? location.toLowerCase().trim() : "");

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (Exception e) {
            log.error("Failed to generate content hash", e);
            // Fallback to simple concatenation
            return String.format("%s_%s_%s", company, title, location).replaceAll("[^a-zA-Z0-9_]", "");
        }
    }

    /**
     * Check if existing job should be updated
     * Update if: description changed, salary changed, or expires date updated
     */
    private boolean shouldUpdateExistingJob(JobListing existing, JobListing newJob) {
        // Update if description changed significantly
        boolean descriptionChanged = !Objects.equals(existing.getDescription(), newJob.getDescription());

        // Update if salary changed
        boolean salaryChanged = !Objects.equals(existing.getSalaryRange(), newJob.getSalaryRange());

        // Update if expires date changed
        boolean expiresChanged = !Objects.equals(existing.getExpiresDate(), newJob.getExpiresDate());

        // Update if URL changed (same job, new posting URL)
        boolean urlChanged = !Objects.equals(existing.getUrl(), newJob.getUrl());

        return descriptionChanged || salaryChanged || expiresChanged || urlChanged;
    }

    /**
     * Update existing job listing with new data
     */
    private void updateExistingJob(JobListing existing, JobListing newJob) {
        // Update fields that may change
        existing.setDescription(newJob.getDescription());
        existing.setSalaryRange(newJob.getSalaryRange());
        existing.setContractType(newJob.getContractType());
        existing.setUrl(newJob.getUrl()); // Update URL if it changed
        existing.setExpiresDate(newJob.getExpiresDate());
        existing.setRawData(newJob.getRawData()); // Update raw data
        existing.setLastSeenAt(LocalDateTime.now()); // Track when we last saw this job

        log.debug("Updated job fields: description, salary, contract, url, expires, lastSeenAt");
    }
}
