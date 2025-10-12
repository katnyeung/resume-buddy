package com.resumebuddy.repository;

import com.resumebuddy.model.graph.Job;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Neo4j Repository for Job nodes.
 *
 * Provides CRUD operations and custom queries for Job graph entities.
 */
@Repository
public interface JobGraphRepository extends Neo4jRepository<Job, String> {

    /**
     * Find all jobs for a resume.
     *
     * @param resumeId Resume ID from MySQL
     * @return List of jobs
     */
    List<Job> findByResumeId(String resumeId);

    /**
     * Find job by experience ID.
     *
     * @param experienceId Experience ID from MySQL ResumeAnalysisExperience
     * @return Job if found
     */
    Optional<Job> findByExperienceId(String experienceId);

    /**
     * Get job with its occupation mapping.
     *
     * Custom query to fetch job and its primary occupation in one query.
     */
    @Query("""
        MATCH (j:Job {id: $jobId})-[m:MAPS_TO]->(o:Occupation)
        RETURN j, collect(m), collect(o)
        """)
    Optional<Job> findByIdWithOccupation(@Param("jobId") String jobId);

    /**
     * Get job with all relationships (occupation + skills).
     */
    @Query("""
        MATCH (j:Job {id: $jobId})
        OPTIONAL MATCH (j)-[m:MAPS_TO]->(o:Occupation)
        OPTIONAL MATCH (j)-[s:REQUIRES_SKILL]->(sk:Skill)
        RETURN j, collect(m), collect(o), collect(s), collect(sk)
        """)
    Optional<Job> findByIdWithAllRelationships(@Param("jobId") String jobId);

    /**
     * Delete all jobs for a resume.
     *
     * @param resumeId Resume ID
     */
    void deleteByResumeId(String resumeId);
}
