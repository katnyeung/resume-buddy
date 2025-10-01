package com.resumebuddy.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResultDto {
    private String resumeId;
    private LocalDateTime analyzedAt;
    private Integer totalLines;
    private Integer analyzedLines;
    private List<LineAnalysisDto> lineAnalyses;
}