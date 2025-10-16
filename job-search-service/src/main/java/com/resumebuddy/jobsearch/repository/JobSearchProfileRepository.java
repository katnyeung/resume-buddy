package com.resumebuddy.jobsearch.repository;

import com.resumebuddy.jobsearch.domain.JobSearchProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobSearchProfileRepository extends JpaRepository<JobSearchProfile, String> {

    List<JobSearchProfile> findByResumeIdOrderByCreatedAtDesc(String resumeId);

    List<JobSearchProfile> findByResumeIdAndUserIdOrderByCreatedAtDesc(String resumeId, String userId);

    Optional<JobSearchProfile> findFirstByResumeIdOrderByCreatedAtDesc(String resumeId);

    /**
     * Find profiles that have been visited after the given date
     * Used to determine active profiles for LLM keyword generation (e.g., last 7 days)
     */
    List<JobSearchProfile> findByLastVisitDateAfter(LocalDateTime cutoffDate);
}
