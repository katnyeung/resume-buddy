package com.resumebuddy.jobsearch.repository;

import com.resumebuddy.jobsearch.domain.JobMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobMatchRepository extends JpaRepository<JobMatch, String> {

    List<JobMatch> findByProfileIdOrderBySimilarityScoreDesc(String profileId);

    @Query("SELECT m FROM JobMatch m WHERE m.profileId = :profileId AND m.similarityScore >= :minScore ORDER BY m.similarityScore DESC")
    List<JobMatch> findTopMatchesByProfileId(@Param("profileId") String profileId, @Param("minScore") double minScore);

    void deleteByProfileId(String profileId);
}
