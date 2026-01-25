package com.resumebuddy.jobsearch.repository;

import com.resumebuddy.jobsearch.domain.JobSearchProfileVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobSearchProfileVersionRepository extends JpaRepository<JobSearchProfileVersion, String> {

    /**
     * Get all versions for a profile, ordered by version number
     */
    List<JobSearchProfileVersion> findByProfileIdOrderByVersionNumberAsc(String profileId);

    /**
     * Get the active version for a profile
     */
    Optional<JobSearchProfileVersion> findByProfileIdAndIsActiveTrue(String profileId);

    /**
     * Get a specific version by profile ID and version number
     */
    Optional<JobSearchProfileVersion> findByProfileIdAndVersionNumber(String profileId, Integer versionNumber);

    /**
     * Count versions for a profile
     */
    int countByProfileId(String profileId);

    /**
     * Delete a specific version
     */
    @Modifying
    void deleteByProfileIdAndVersionNumber(String profileId, Integer versionNumber);

    /**
     * Find the oldest non-active version (for deletion when at max versions)
     */
    @Query("SELECT v FROM JobSearchProfileVersion v WHERE v.profileId = :profileId AND v.isActive = false ORDER BY v.createdAt ASC")
    List<JobSearchProfileVersion> findOldestNonActiveVersions(@Param("profileId") String profileId);

    /**
     * Deactivate all versions for a profile
     */
    @Modifying
    @Query("UPDATE JobSearchProfileVersion v SET v.isActive = false WHERE v.profileId = :profileId")
    void deactivateAllVersions(@Param("profileId") String profileId);

    /**
     * Get the max version number for a profile
     */
    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM JobSearchProfileVersion v WHERE v.profileId = :profileId")
    int getMaxVersionNumber(@Param("profileId") String profileId);
}
