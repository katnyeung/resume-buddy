package com.resumebuddy.jobsearch.repository;

import com.resumebuddy.jobsearch.domain.JobSearchProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobSearchProfileRepository extends JpaRepository<JobSearchProfile, String> {

    List<JobSearchProfile> findByResumeIdOrderByCreatedAtDesc(String resumeId);

    Optional<JobSearchProfile> findFirstByResumeIdOrderByCreatedAtDesc(String resumeId);
}
