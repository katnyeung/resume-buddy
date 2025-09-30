package com.resumebuddy.repository;

import com.resumebuddy.model.ResumeAnalysisExperience;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResumeAnalysisExperienceRepository extends JpaRepository<ResumeAnalysisExperience, String> {

    List<ResumeAnalysisExperience> findByAnalysisId(String analysisId);
}
