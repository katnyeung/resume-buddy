package com.resumebuddy.jobsearch.dto.reed;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DTO: Reed.co.uk API Search Response Wrapper
 * Maps the top-level response from Reed job search API
 */
@Data
public class ReedSearchResponse {

    @JsonProperty("results")
    private List<ReedJobDto> results;

    @JsonProperty("totalResults")
    private Integer totalResults;

    @JsonProperty("ambiguousLocations")
    private List<AmbiguousLocation> ambiguousLocations;

    /**
     * Nested ambiguous location object
     */
    @Data
    public static class AmbiguousLocation {
        @JsonProperty("locationId")
        private Long locationId;

        @JsonProperty("locationName")
        private String locationName;
    }
}
