package com.resumebuddy.repository;

import com.resumebuddy.model.ResumeAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysis, String> {

    Optional<ResumeAnalysis> findByResumeId(String resumeId);

    boolean existsByResumeId(String resumeId);
}
