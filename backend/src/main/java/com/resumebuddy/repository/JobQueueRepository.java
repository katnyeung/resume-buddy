package com.resumebuddy.repository;

import com.resumebuddy.model.JobQueueEntry;
import com.resumebuddy.model.JobQueueEntry.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobQueueRepository extends JpaRepository<JobQueueEntry, String> {

    @Query("SELECT j FROM JobQueueEntry j WHERE j.status = :status ORDER BY j.priority ASC, j.queuedAt ASC")
    List<JobQueueEntry> findTopByStatusOrderByPriorityAscQueuedAtAsc(@Param("status") JobStatus status, org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT j FROM JobQueueEntry j WHERE j.id = :id")
    Optional<JobQueueEntry> findByIdWithLock(@Param("id") String id);

    List<JobQueueEntry> findByUserIdOrderByQueuedAtDesc(String userId);

    @Query("SELECT COUNT(j) FROM JobQueueEntry j WHERE j.userId = :userId AND j.status = 'PROCESSING'")
    int countProcessingJobsByUser(@Param("userId") String userId);

    @Query("SELECT COUNT(j) FROM JobQueueEntry j WHERE j.status = 'PROCESSING'")
    int countAllProcessingJobs();

    @Query("SELECT COUNT(j) FROM JobQueueEntry j WHERE j.status = 'QUEUED' AND j.queuedAt < (SELECT jq.queuedAt FROM JobQueueEntry jq WHERE jq.id = :jobId)")
    int countQueuedJobsBefore(@Param("jobId") String jobId);
}
