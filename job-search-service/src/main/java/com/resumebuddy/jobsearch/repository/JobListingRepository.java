package com.resumebuddy.jobsearch.repository;

import com.resumebuddy.jobsearch.domain.JobListing;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobListingRepository extends JpaRepository<JobListing, String> {

    List<JobListing> findBySource(String source);

    List<JobListing> findByCompany(String company);

    boolean existsByUrl(String url);

    Optional<JobListing> findByContentHash(String contentHash);

    boolean existsByContentHash(String contentHash);

    // Market Insights: Find unanalyzed jobs (ordered by newest first)
    List<JobListing> findByAnalyzedAtIsNullAndFetchedAtAfterOrderByFetchedAtDesc(LocalDateTime cutoff);

    // Market Insights: Find all unanalyzed jobs (ordered by newest first)
    List<JobListing> findByAnalyzedAtIsNullOrderByFetchedAtDesc();

    // Market Insights: Find jobs analyzed in a date range (for monthly aggregation)
    List<JobListing> findByAnalyzedAtBetween(LocalDateTime start, LocalDateTime end);

    // Market Insights: Count unanalyzed jobs
    long countByAnalyzedAtIsNull();

    // Market Insights: Count analyzed jobs
    long countByAnalyzedAtIsNotNull();

    // OPTIMIZED: Pageable-based queries (limit at database level)
    List<JobListing> findByAnalyzedAtIsNullAndFetchedAtAfterOrderByFetchedAtDesc(LocalDateTime cutoff, Pageable pageable);

    List<JobListing> findByAnalyzedAtIsNullOrderByFetchedAtDesc(Pageable pageable);

    List<JobListing> findByFetchedAtAfterOrderByFetchedAtDesc(LocalDateTime cutoff, Pageable pageable);

    List<JobListing> findAllByOrderByFetchedAtDesc(Pageable pageable);

    // Skill Drilldown: Fetch jobs by list of IDs (from Neo4j queries)
    List<JobListing> findByIdIn(List<String> ids);
}
