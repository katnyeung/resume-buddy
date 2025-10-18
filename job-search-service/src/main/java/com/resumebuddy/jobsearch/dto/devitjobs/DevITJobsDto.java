package com.resumebuddy.jobsearch.dto.devitjobs;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO: Individual Job from DevITJobs.uk RSS Feed
 * Parses XML/RSS job feed from https://devitjobs.uk/job_feed.xml
 *
 * Common RSS job feed structure:
 * <item>
 *   <title>Job Title</title>
 *   <link>https://devitjobs.uk/jobs/123</link>
 *   <description><![CDATA[Full job description HTML]]></description>
 *   <pubDate>Mon, 15 Jan 2025 10:00:00 GMT</pubDate>
 *   <guid>https://devitjobs.uk/jobs/123</guid>
 *   <category>Technology</category>
 *   <location>London, UK</location>
 *   <company>Company Name</company>
 *   <salary>£50,000 - £70,000</salary>
 *   <jobType>Permanent</jobType>
 * </item>
 */
@Data
public class DevITJobsDto {

    /**
     * Job title from <title> tag
     */
    private String title;

    /**
     * Job URL from <link> tag
     */
    private String link;

    /**
     * Full job description from <description> tag
     * Usually contains HTML/CDATA
     */
    private String description;

    /**
     * Publication date from <pubDate> tag
     * RFC 822 format: "Mon, 15 Jan 2025 10:00:00 GMT"
     */
    private String pubDate;

    /**
     * Parsed publication date
     */
    private LocalDateTime publishedDate;

    /**
     * Unique identifier from <guid> tag
     * Often same as link
     */
    private String guid;

    /**
     * Job category from <category> tag
     * e.g., "Software Development", "DevOps"
     */
    private String category;

    /**
     * Location from <location> or <job:location> tag
     * e.g., "London, UK", "Remote"
     */
    private String location;

    /**
     * Company name from <company> or <source> tag
     */
    private String company;

    /**
     * Salary range from <salary> tag
     * e.g., "£50,000 - £70,000", "£60k"
     */
    private String salary;

    /**
     * Job type from <jobType> or <type> tag
     * e.g., "Permanent", "Contract", "Full-time"
     */
    private String jobType;

    /**
     * Employment type from <employmentType> tag
     * e.g., "Full-time", "Part-time"
     */
    private String employmentType;

    /**
     * Remote work option from <remote> tag
     */
    private Boolean remote;

    /**
     * Clean description text (HTML stripped)
     */
    private String descriptionText;
}
