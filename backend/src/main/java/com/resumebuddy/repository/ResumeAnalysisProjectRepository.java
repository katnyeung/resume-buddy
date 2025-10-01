package com.resumebuddy.repository;

import com.resumebuddy.model.ResumeAnalysisProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResumeAnalysisProjectRepository extends JpaRepository<ResumeAnalysisProject, String> {

    List<ResumeAnalysisProject> findByAnalysisId(String analysisId);
}
