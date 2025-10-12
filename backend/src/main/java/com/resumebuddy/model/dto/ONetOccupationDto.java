package com.resumebuddy.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Simplified DTO for O*NET occupation data
 * Used for search results and related occupations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ONetOccupationDto {
    private String code;
    private String title;
}
