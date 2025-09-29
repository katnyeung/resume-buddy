package com.resumebuddy.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParsedResume {
    private String id;
    private String filename;
    private String contentType;
    private String originalText;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}