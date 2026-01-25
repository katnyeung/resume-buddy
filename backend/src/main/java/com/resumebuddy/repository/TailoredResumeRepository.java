package com.resumebuddy.repository;

import com.resumebuddy.model.TailoredResume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TailoredResumeRepository extends JpaRepository<TailoredResume, String> {

    /**
     * Find all tailored resumes for a specific resume, ordered by newest first
     */
    List<TailoredResume> findByResumeIdAndUserIdOrderByCreatedAtDesc(String resumeId, String userId);

    /**
     * Find all tailored resumes for a user, ordered by newest first
     */
    List<TailoredResume> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Find a specific tailored resume by ID and verify ownership
     */
    Optional<TailoredResume> findByIdAndUserId(String id, String userId);

    /**
     * Count tailored resumes for a user (for analytics)
     */
    long countByUserId(String userId);
}
