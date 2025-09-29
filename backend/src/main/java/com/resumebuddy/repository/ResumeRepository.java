package com.resumebuddy.repository;

import com.resumebuddy.model.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, String> {

    Optional<Resume> findByFilename(String filename);

    List<Resume> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT r FROM Resume r LEFT JOIN FETCH r.lines WHERE r.id = :id")
    Optional<Resume> findByIdWithLines(@Param("id") String id);

    @Query("SELECT r FROM Resume r LEFT JOIN FETCH r.lines LEFT JOIN FETCH r.suggestions WHERE r.id = :id")
    Optional<Resume> findByIdWithLinesAndSuggestions(@Param("id") String id);
}