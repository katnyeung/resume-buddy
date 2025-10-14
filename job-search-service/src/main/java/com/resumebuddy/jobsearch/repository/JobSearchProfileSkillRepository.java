package com.resumebuddy.jobsearch.repository;

import com.resumebuddy.jobsearch.domain.JobSearchProfileSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository: Job Search Profile Skill
 */
@Repository
public interface JobSearchProfileSkillRepository extends JpaRepository<JobSearchProfileSkill, String> {

    List<JobSearchProfileSkill> findByProfileId(String profileId);

    void deleteByProfileId(String profileId);

    boolean existsByProfileIdAndSkillName(String profileId, String skillName);
}
