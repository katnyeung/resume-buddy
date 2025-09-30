package com.resumebuddy.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EducationDto {
    private String id;
    private String degree;
    private String institution;
    private String graduationDate;
    private String description;
}
