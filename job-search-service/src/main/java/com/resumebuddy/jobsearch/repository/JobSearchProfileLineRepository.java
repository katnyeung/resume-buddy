package com.resumebuddy.jobsearch.repository;

import com.resumebuddy.jobsearch.domain.JobSearchProfileLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobSearchProfileLineRepository extends JpaRepository<JobSearchProfileLine, String> {

    List<JobSearchProfileLine> findByProfileId(String profileId);

    void deleteByProfileId(String profileId);
}
