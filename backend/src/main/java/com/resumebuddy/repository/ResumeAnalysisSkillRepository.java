package com.resumebuddy.repository;

import com.resumebuddy.model.ResumeAnalysisSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResumeAnalysisSkillRepository extends JpaRepository<ResumeAnalysisSkill, String> {

    List<ResumeAnalysisSkill> findByAnalysisId(String analysisId);
}
