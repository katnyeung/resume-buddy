package com.resumebuddy.jobsearch.dto.adzuna;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.Map;

/**
 * DTO: Individual Job from Adzuna API
 * Maps job listing fields from Adzuna search results
 */
@Data
public class AdzunaJobDto {

    @JsonProperty("id")
    private String id;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("created")
    private ZonedDateTime created;

    @JsonProperty("redirect_url")
    private String redirectUrl;

    @JsonProperty("company")
    private AdzunaCompany company;

    @JsonProperty("location")
    private AdzunaLocation location;

    @JsonProperty("category")
    private AdzunaCategory category;

    @JsonProperty("salary_min")
    private Double salaryMin;

    @JsonProperty("salary_max")
    private Double salaryMax;

    @JsonProperty("salary_is_predicted")
    private String salaryIsPredicted;

    @JsonProperty("contract_type")
    private String contractType; // "permanent", "contract"

    @JsonProperty("contract_time")
    private String contractTime; // "full_time", "part_time"

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("adref")
    private String adref; // Adzuna ad reference

    /**
     * Nested company object
     */
    @Data
    public static class AdzunaCompany {
        @JsonProperty("display_name")
        private String displayName;
    }

    /**
     * Nested location object
     */
    @Data
    public static class AdzunaLocation {
        @JsonProperty("area")
        private java.util.List<String> area;

        @JsonProperty("display_name")
        private String displayName;
    }

    /**
     * Nested category object
     */
    @Data
    public static class AdzunaCategory {
        @JsonProperty("label")
        private String label;

        @JsonProperty("tag")
        private String tag;
    }
}
