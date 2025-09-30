package com.resumebuddy.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDto {
    private String id;
    private String projectName;
    private String description;
    private String technologiesUsed;
    private String projectUrl;
}
