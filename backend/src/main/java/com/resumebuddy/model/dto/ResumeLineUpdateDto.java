package com.resumebuddy.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeLineUpdateDto {

    @NotNull(message = "Line number is required")
    private Integer lineNumber;

    @NotNull(message = "Content is required")
    private String content;
}