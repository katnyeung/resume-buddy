package com.resumebuddy.jobsearch.infrastructure.external.jobsources;

import com.resumebuddy.jobsearch.dto.adzuna.AdzunaJobDto;
import com.resumebuddy.jobsearch.dto.devitjobs.DevITJobsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Infrastructure: DevITJobs.uk RSS Feed Client
 * Fetches job listings from DevITJobs.uk RSS/XML feed
 *
 * Feed URL: https://devitjobs.uk/job_feed.xml
 * Format: RSS 2.0 with job-specific extensions
 *
 * Key Features:
 * - XML/RSS feed parsing
 * - HTML stripping from descriptions
 * - Date filtering (only recent jobs)
 * - Deduplication via content hash
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DevITJobsApiClient implements JobSourceApiClient {

    private final RestClient restClient;

    @Value("${app.job-crawling.devitjobs.feed-url:https://devitjobs.uk/job_feed.xml}")
    private String feedUrl;

    // DevITJobs uses dd.MM.yyyy format (e.g., "04.10.2023")
    private static final DateTimeFormatter DEVITJOBS_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ENGLISH);

    @Override
    public List<AdzunaJobDto> searchJobs(Map<String, Object> params) {
        try {
            Integer maxDaysOld = (Integer) params.getOrDefault("maxDaysOld", 30);
            Integer maxResults = (Integer) params.getOrDefault("maxResults", 50);
            String keywords = (String) params.get("keywords");

            log.info("Fetching jobs from DevITJobs.uk RSS feed - maxDaysOld: {}, maxResults: {}",
                    maxDaysOld, maxResults);

            // Fetch XML feed
            String xmlContent = restClient.get()
                    .uri(feedUrl)
                    .retrieve()
                    .body(String.class);

            if (xmlContent == null || xmlContent.isEmpty()) {
                log.warn("Empty response from DevITJobs feed");
                return Collections.emptyList();
            }

            // Parse XML feed
            List<DevITJobsDto> allJobs = parseXmlFeed(xmlContent);
            log.info("Parsed {} total jobs from DevITJobs feed", allJobs.size());

            // Filter by date (only recent jobs)
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(maxDaysOld);
            List<DevITJobsDto> recentJobs = allJobs.stream()
                    .filter(job -> job.getPublishedDate() != null &&
                            job.getPublishedDate().isAfter(cutoffDate))
                    .collect(Collectors.toList());

            log.info("Filtered to {} jobs posted within last {} days", recentJobs.size(), maxDaysOld);

            // Filter by keywords if provided
            if (keywords != null && !keywords.isEmpty()) {
                String lowerKeywords = keywords.toLowerCase();
                recentJobs = recentJobs.stream()
                        .filter(job -> {
                            String searchText = (job.getTitle() + " " +
                                    job.getDescription() + " " +
                                    job.getCategory()).toLowerCase();
                            return searchText.contains(lowerKeywords);
                        })
                        .collect(Collectors.toList());
                log.info("Filtered to {} jobs matching keywords: {}", recentJobs.size(), keywords);
            }

            // Limit results
            if (recentJobs.size() > maxResults) {
                recentJobs = recentJobs.subList(0, maxResults);
                log.info("Limited to {} jobs", maxResults);
            }

            // Convert to common Adzuna format
            return recentJobs.stream()
                    .map(this::convertToAdzunaFormat)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("CRITICAL: DevITJobs RSS feed fetch failed - stopping process. Error: {}",
                    e.getMessage(), e);
            throw new RuntimeException("DevITJobs feed failure: " + e.getMessage(), e);
        }
    }

    @Override
    public String getSourceName() {
        return "DEVITJOBS";
    }

    /**
     * Parse XML/RSS feed into job DTOs
     */
    private List<DevITJobsDto> parseXmlFeed(String xmlContent) {
        List<DevITJobsDto> jobs = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xmlContent)));

            // Get all <job> elements (DevITJobs uses <job> not <item>)
            NodeList jobNodes = doc.getElementsByTagName("job");

            for (int i = 0; i < jobNodes.getLength(); i++) {
                Node jobNode = jobNodes.item(i);
                if (jobNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element jobElement = (Element) jobNode;
                    DevITJobsDto job = parseJobItem(jobElement);
                    if (job != null) {
                        jobs.add(job);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse XML feed", e);
            throw new RuntimeException("XML parsing failed", e);
        }

        return jobs;
    }

    /**
     * Parse individual <job> element
     */
    private DevITJobsDto parseJobItem(Element jobElement) {
        try {
            DevITJobsDto job = new DevITJobsDto();

            // Title (DevITJobs uses <name> or <title>)
            String title = getElementText(jobElement, "title");
            if (title == null) {
                title = getElementText(jobElement, "name");
            }
            job.setTitle(title);

            // Link (DevITJobs uses <link>, <url>, or <apply_url>)
            String link = getElementText(jobElement, "link");
            if (link == null) {
                link = getElementText(jobElement, "url");
            }
            if (link == null) {
                link = getElementText(jobElement, "apply_url");
            }
            job.setLink(link);

            // ID (DevITJobs uses <id> tag, fallback to link)
            String guid = getElementText(jobElement, "id");
            job.setGuid(guid != null ? guid : link);

            // Description (may contain HTML/CDATA)
            String description = getElementText(jobElement, "description");
            job.setDescription(description);
            job.setDescriptionText(stripHtml(description));

            // PubDate (DevITJobs uses <pubdate> with dd.MM.yyyy format)
            String pubDateStr = getElementText(jobElement, "pubdate");
            if (pubDateStr != null) {
                job.setPubDate(pubDateStr);
                job.setPublishedDate(parseDevITJobsDate(pubDateStr));
            }

            // Category
            job.setCategory(getElementText(jobElement, "category"));

            // Location (DevITJobs uses <location>, <city>, or <region>)
            String location = getElementText(jobElement, "location");
            if (location == null) {
                location = getElementText(jobElement, "city");
            }
            if (location == null) {
                location = getElementText(jobElement, "region");
            }
            job.setLocation(location);

            // Company (DevITJobs uses <company> or <company-name>)
            String company = getElementText(jobElement, "company");
            if (company == null) {
                company = getElementText(jobElement, "company-name");
            }
            job.setCompany(company);

            // Salary
            String salary = getElementText(jobElement, "salary");
            job.setSalary(salary);

            // Job Type (DevITJobs uses <jobtype> or <job-type>)
            String jobType = getElementText(jobElement, "jobtype");
            if (jobType == null) {
                jobType = getElementText(jobElement, "job-type");
            }
            if (jobType == null) {
                jobType = getElementText(jobElement, "job-status");
            }
            job.setJobType(jobType);

            // Employment Type (not present in DevITJobs feed)
            job.setEmploymentType(null);

            // Remote (not explicitly present in DevITJobs feed)
            job.setRemote(false);

            return job;

        } catch (Exception e) {
            log.error("Failed to parse job item", e);
            return null;
        }
    }

    /**
     * Get text content of an element
     */
    private String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            Node node = nodes.item(0);
            String text = node.getTextContent();
            return (text != null && !text.trim().isEmpty()) ? text.trim() : null;
        }
        return null;
    }

    /**
     * Parse DevITJobs date format (dd.MM.yyyy)
     * Example: "04.10.2023"
     */
    private LocalDateTime parseDevITJobsDate(String dateStr) {
        try {
            // DevITJobs uses dd.MM.yyyy format
            return LocalDateTime.parse(dateStr + " 00:00:00",
                DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.ENGLISH));
        } catch (Exception e) {
            log.debug("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    /**
     * Strip HTML tags from description
     */
    private String stripHtml(String html) {
        if (html == null) {
            return null;
        }

        // Remove HTML tags
        String text = html.replaceAll("<[^>]+>", " ");

        // Decode common HTML entities
        text = text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");

        // Normalize whitespace
        text = text.replaceAll("\\s+", " ").trim();

        return text;
    }

    /**
     * Convert DevITJobs DTO to Adzuna format (common DTO)
     */
    private AdzunaJobDto convertToAdzunaFormat(DevITJobsDto devitJob) {
        AdzunaJobDto adzunaJob = new AdzunaJobDto();

        // Basic fields
        adzunaJob.setId(devitJob.getGuid() != null ? devitJob.getGuid() : devitJob.getLink());
        adzunaJob.setTitle(devitJob.getTitle());

        // Use stripped HTML text for description (cleaner for parsing)
        adzunaJob.setDescription(devitJob.getDescriptionText() != null ?
                devitJob.getDescriptionText() : devitJob.getDescription());

        // Date
        if (devitJob.getPublishedDate() != null) {
            ZonedDateTime zonedDateTime = devitJob.getPublishedDate()
                    .atZone(java.time.ZoneId.systemDefault());
            adzunaJob.setCreated(zonedDateTime);
        }

        // URL
        adzunaJob.setRedirectUrl(devitJob.getLink());

        // Company
        if (devitJob.getCompany() != null) {
            AdzunaJobDto.AdzunaCompany company = new AdzunaJobDto.AdzunaCompany();
            company.setDisplayName(devitJob.getCompany());
            adzunaJob.setCompany(company);
        }

        // Location
        if (devitJob.getLocation() != null) {
            AdzunaJobDto.AdzunaLocation location = new AdzunaJobDto.AdzunaLocation();
            location.setDisplayName(devitJob.getLocation());
            adzunaJob.setLocation(location);
        }

        // Parse salary if present
        if (devitJob.getSalary() != null) {
            parseSalary(devitJob.getSalary(), adzunaJob);
        }

        // Contract type
        if (devitJob.getJobType() != null) {
            String jobType = devitJob.getJobType().toLowerCase();
            if (jobType.contains("permanent")) {
                adzunaJob.setContractType("permanent");
            } else if (jobType.contains("contract")) {
                adzunaJob.setContractType("contract");
            } else if (jobType.contains("temporary") || jobType.contains("temp")) {
                adzunaJob.setContractType("temporary");
            }
        }

        // Employment type
        if (devitJob.getEmploymentType() != null) {
            String empType = devitJob.getEmploymentType().toLowerCase();
            if (empType.contains("full")) {
                adzunaJob.setContractTime("full_time");
            } else if (empType.contains("part")) {
                adzunaJob.setContractTime("part_time");
            }
        }

        // Category
        if (devitJob.getCategory() != null) {
            AdzunaJobDto.AdzunaCategory category = new AdzunaJobDto.AdzunaCategory();
            category.setLabel(devitJob.getCategory());
            adzunaJob.setCategory(category);
        }

        return adzunaJob;
    }

    /**
     * Parse salary string to min/max values
     * Examples: "£50,000 - £70,000", "£60k", "$80k - $100k"
     */
    private void parseSalary(String salaryStr, AdzunaJobDto job) {
        try {
            // Remove currency symbols and commas
            String cleaned = salaryStr.replaceAll("[£$€,]", "");

            // Pattern: "50000 - 70000" or "50k - 70k"
            Pattern rangePattern = Pattern.compile("(\\d+(?:\\.\\d+)?)(k)?\\s*-\\s*(\\d+(?:\\.\\d+)?)(k)?");
            Matcher matcher = rangePattern.matcher(cleaned);

            if (matcher.find()) {
                double min = Double.parseDouble(matcher.group(1));
                if ("k".equalsIgnoreCase(matcher.group(2))) {
                    min *= 1000;
                }

                double max = Double.parseDouble(matcher.group(3));
                if ("k".equalsIgnoreCase(matcher.group(4))) {
                    max *= 1000;
                }

                job.setSalaryMin(min);
                job.setSalaryMax(max);
            } else {
                // Single value: "60000" or "60k"
                Pattern singlePattern = Pattern.compile("(\\d+(?:\\.\\d+)?)(k)?");
                Matcher singleMatcher = singlePattern.matcher(cleaned);
                if (singleMatcher.find()) {
                    double salary = Double.parseDouble(singleMatcher.group(1));
                    if ("k".equalsIgnoreCase(singleMatcher.group(2))) {
                        salary *= 1000;
                    }
                    job.setSalaryMin(salary);
                    job.setSalaryMax(salary);
                }
            }

        } catch (Exception e) {
            log.debug("Failed to parse salary: {}", salaryStr);
        }
    }
}
