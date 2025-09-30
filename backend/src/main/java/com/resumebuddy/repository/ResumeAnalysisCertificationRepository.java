package com.resumebuddy.repository;

import com.resumebuddy.model.ResumeAnalysisCertification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResumeAnalysisCertificationRepository extends JpaRepository<ResumeAnalysisCertification, String> {

    List<ResumeAnalysisCertification> findByAnalysisId(String analysisId);
}
