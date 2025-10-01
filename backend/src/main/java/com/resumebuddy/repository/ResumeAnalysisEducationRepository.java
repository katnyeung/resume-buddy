package com.resumebuddy.repository;

import com.resumebuddy.model.ResumeAnalysisEducation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResumeAnalysisEducationRepository extends JpaRepository<ResumeAnalysisEducation, String> {

    List<ResumeAnalysisEducation> findByAnalysisId(String analysisId);
}
