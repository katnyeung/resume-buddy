package com.resumebuddy.jobsearch.repository;

import com.resumebuddy.jobsearch.domain.JobListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobListingRepository extends JpaRepository<JobListing, String> {

    List<JobListing> findBySource(String source);

    List<JobListing> findByCompany(String company);
}
