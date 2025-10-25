package com.resumebuddy.jobsearch.infrastructure.external.jobsources;

import com.resumebuddy.jobsearch.dto.adzuna.AdzunaJobDto;

import java.util.List;
import java.util.Map;

/**
 * Interface: Job Source API Client
 * Common contract for all external job board API clients
 */
public interface JobSourceApiClient {

    /**
     * Search for jobs from the external job board
     *
     * @param params Search parameters (keywords, location, etc.)
     * @return List of jobs from the source
     */
    List<AdzunaJobDto> searchJobs(Map<String, Object> params);

    /**
     * Get the source name
     *
     * @return Source identifier (e.g., "ADZUNA", "REED")
     */
    String getSourceName();
}
