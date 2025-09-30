package com.resumebuddy.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LineAnalysisDto {
    private Integer lineNumber;
    private String sectionType;
    private Integer groupId;
    private String groupType;
    private String analysisNotes;
}