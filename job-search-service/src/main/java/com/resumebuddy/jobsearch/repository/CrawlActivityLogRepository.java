package com.resumebuddy.jobsearch.repository;

import com.resumebuddy.jobsearch.domain.CrawlActivityLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository: Crawl Activity Log
 */
@Repository
public interface CrawlActivityLogRepository extends JpaRepository<CrawlActivityLog, String> {

    /**
     * Find recent crawl activities, most recent first
     * Use Pageable to control the limit
     */
    List<CrawlActivityLog> findAllByOrderByCrawledAtDesc(Pageable pageable);
}
