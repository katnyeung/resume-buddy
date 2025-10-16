package com.resumebuddy.jobsearch.repository;

import com.resumebuddy.jobsearch.domain.JobMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // Find saved matches for a profile
    List<JobMatch> findByProfileIdAndIsSavedTrue(String profileId);

    // Find applied matches for a profile
    List<JobMatch> findByProfileIdAndIsAppliedTrue(String profileId);

    // Delete only non-saved matches for a profile (for force refresh)
    @Modifying
    @Query("DELETE FROM JobMatch m WHERE m.profileId = :profileId AND m.isSaved = false")
    void deleteNonSavedByProfileId(@Param("profileId") String profileId);

    // Find existing match by profile and listing
    JobMatch findByProfileIdAndListingId(String profileId, String listingId);

    // Delete expired matches based on retention policy (7/14/20 days)
    @Modifying
    @Query("DELETE FROM JobMatch m WHERE m.profileId = :profileId AND " +
           "((m.isSaved = true AND m.flaggedAt < :savedCutoff) OR " +
           "(m.isApplied = true AND m.flaggedAt < :appliedCutoff) OR " +
           "(m.isRedflag = true AND m.flaggedAt < :redflagCutoff))")
    void deleteExpiredMatches(@Param("profileId") String profileId,
                              @Param("savedCutoff") java.time.LocalDateTime savedCutoff,
                              @Param("appliedCutoff") java.time.LocalDateTime appliedCutoff,
                              @Param("redflagCutoff") java.time.LocalDateTime redflagCutoff);
}
