package com.resumebuddy.jobsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobMatchResponse {

    private String matchId;
    private String profileId;
    private String listingId;
    private BigDecimal similarityScore;
    private String matchLevel; // "STRONG", "GOOD", "MODERATE"

    // Job listing details
    private String title;
    private String company;
    private String location;
    private String description;
    private String url;
    private String salaryRange;

    // Skill gap analysis
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private double skillMatchPercentage;
}
