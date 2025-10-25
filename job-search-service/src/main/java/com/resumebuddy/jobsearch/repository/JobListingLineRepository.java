package com.resumebuddy.jobsearch.repository;

import com.resumebuddy.jobsearch.domain.JobListingLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository: Job Listing Line
 * Manages parsed job description lines
 */
@Repository
public interface JobListingLineRepository extends JpaRepository<JobListingLine, String> {

    /**
     * Find all lines for a specific job listing
     */
    List<JobListingLine> findByListingIdOrderByLineNumber(String listingId);

    /**
     * Delete all lines for a specific job listing
     */
    void deleteByListingId(String listingId);

    /**
     * Count lines for a specific job listing
     */
    long countByListingId(String listingId);
}
