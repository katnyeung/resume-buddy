package com.resumebuddy.repository;

import com.resumebuddy.model.ResumeLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeLineRepository extends JpaRepository<ResumeLine, String> {

    @Query("SELECT rl FROM ResumeLine rl WHERE rl.resume.id = :resumeId ORDER BY rl.lineNumber ASC")
    List<ResumeLine> findByResumeIdOrderByLineNumber(@Param("resumeId") String resumeId);

    @Query("SELECT rl FROM ResumeLine rl WHERE rl.resume.id = :resumeId AND rl.lineNumber = :lineNumber")
    Optional<ResumeLine> findByResumeIdAndLineNumber(@Param("resumeId") String resumeId, @Param("lineNumber") Integer lineNumber);

    @Query("SELECT COUNT(rl) FROM ResumeLine rl WHERE rl.resume.id = :resumeId")
    long countByResumeId(@Param("resumeId") String resumeId);

    @Modifying
    @Query("DELETE FROM ResumeLine rl WHERE rl.resume.id = :resumeId")
    void deleteByResumeId(@Param("resumeId") String resumeId);

    @Query("SELECT MAX(rl.lineNumber) FROM ResumeLine rl WHERE rl.resume.id = :resumeId")
    Optional<Integer> findMaxLineNumberByResumeId(@Param("resumeId") String resumeId);
}