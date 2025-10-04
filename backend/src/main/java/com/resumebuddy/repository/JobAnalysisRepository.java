package com.resumebuddy.repository;

import com.resumebuddy.model.JobAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JobAnalysisRepository extends JpaRepository<JobAnalysis, String> {

    /**
     * Find job analysis by resume ID and experience ID
     */
    Optional<JobAnalysis> findByResumeIdAndExperienceId(String resumeId, String experienceId);

    /**
     * Check if job analysis exists for given resume and experience
     */
    boolean existsByResumeIdAndExperienceId(String resumeId, String experienceId);

    /**
     * Delete job analysis by resume ID and experience ID
     */
    void deleteByResumeIdAndExperienceId(String resumeId, String experienceId);
}
