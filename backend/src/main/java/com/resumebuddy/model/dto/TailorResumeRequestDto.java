package com.resumebuddy.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TailorResumeRequestDto {

    @NotBlank(message = "Job description is required")
    @Size(min = 50, max = 20000, message = "Job description must be between 50 and 20000 characters")
    private String jobDescription;

    @Size(max = 500, message = "Guidelines must be under 500 characters")
    private String guidelines;
}
