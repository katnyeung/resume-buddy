package com.resumebuddy.jobsearch.repository;

import com.resumebuddy.jobsearch.domain.JobSearchProfileSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Find all skills for the given list of profile IDs
     * Used to aggregate skills from active profiles for LLM keyword generation
     */
    @Query("SELECT s FROM JobSearchProfileSkill s WHERE s.profileId IN :profileIds")
    List<JobSearchProfileSkill> findByProfileIdIn(@Param("profileIds") List<String> profileIds);
}
