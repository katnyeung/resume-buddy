package com.resumebuddy.controller;

import com.resumebuddy.model.ResumeLine;
import com.resumebuddy.model.dto.ResumeLineUpdateDto;
import com.resumebuddy.service.ResumeLineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/resumes")
@CrossOrigin(origins = "http://localhost:3000")
@RequiredArgsConstructor
@Tag(name = "Resume Lines Management", description = "Debug and manage resume lines for TipTap integration")
public class ResumeLineController {

    private final ResumeLineService resumeLineService;

    @GetMapping("/{id}/lines")
    @Operation(summary = "Get resume lines", description = "Get all lines for a resume ordered by line number")
    public ResponseEntity<List<ResumeLine>> getResumeLines(@PathVariable String id) {
        log.info("Getting lines for resume ID: {}", id);

        try {
            List<ResumeLine> lines = resumeLineService.getResumeLines(id);
            return ResponseEntity.ok(lines);
        } catch (Exception e) {
            log.error("Error getting lines for resume ID: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/process-lines")
    @Operation(summary = "Process resume lines", description = "Manually trigger line processing from parsed content")
    public ResponseEntity<Map<String, Object>> processResumeLines(@PathVariable String id) {
        log.info("Manually processing lines for resume ID: {}", id);

        try {
            resumeLineService.processResumeLines(id);
            long lineCount = resumeLineService.getLineCount(id);

            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Lines processed successfully",
                "lineCount", lineCount
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing lines for resume ID: {}", id, e);

            Map<String, Object> response = Map.of(
                "success", false,
                "message", "Error processing lines: " + e.getMessage()
            );

            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PutMapping("/{id}/lines/{lineNumber}")
    @Operation(summary = "Update resume line", description = "Update content of a specific line")
    public ResponseEntity<ResumeLine> updateLine(
            @PathVariable String id,
            @PathVariable Integer lineNumber,
            @RequestBody Map<String, String> request) {
        log.info("Updating line {} for resume ID: {}", lineNumber, id);

        String newContent = request.get("content");
        if (newContent == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            ResumeLine updatedLine = resumeLineService.updateLine(id, lineNumber, newContent);
            return ResponseEntity.ok(updatedLine);
        } catch (Exception e) {
            log.error("Error updating line {} for resume ID: {}", lineNumber, id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/lines")
    @Operation(summary = "Insert resume line", description = "Insert a new line at specified position")
    public ResponseEntity<ResumeLine> insertLine(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        log.info("Inserting line for resume ID: {}", id);

        Integer lineNumber = (Integer) request.get("lineNumber");
        String content = (String) request.get("content");

        if (lineNumber == null || content == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            ResumeLine newLine = resumeLineService.insertLine(id, lineNumber, content);
            return ResponseEntity.ok(newLine);
        } catch (Exception e) {
            log.error("Error inserting line at position {} for resume ID: {}", lineNumber, id, e);
            return ResponseEntity.internalServerError().build();
        }
    }


    @GetMapping("/{id}/lines/count")
    @Operation(summary = "Get line count", description = "Get total number of lines for a resume")
    public ResponseEntity<Map<String, Object>> getLineCount(@PathVariable String id) {
        log.info("Getting line count for resume ID: {}", id);

        try {
            long lineCount = resumeLineService.getLineCount(id);

            Map<String, Object> response = Map.of(
                "resumeId", id,
                "lineCount", lineCount
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting line count for resume ID: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{id}/lines/batch")
    @Operation(summary = "Batch update resume lines", description = "Update multiple lines in a single request")
    public ResponseEntity<Map<String, Object>> updateMultipleLines(
            @PathVariable String id,
            @Valid @RequestBody List<ResumeLineUpdateDto> updates) {
        log.info("Batch updating {} lines for resume ID: {}", updates.size(), id);

        if (updates == null || updates.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Updates list cannot be empty"
            ));
        }

        try {
            List<ResumeLine> updatedLines = resumeLineService.updateMultipleLines(id, updates);

            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Lines updated successfully",
                "updatedCount", updatedLines.size(),
                "updatedLines", updatedLines
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error batch updating lines for resume ID: {}", id, e);

            Map<String, Object> response = Map.of(
                "success", false,
                "message", "Error updating lines: " + e.getMessage()
            );

            return ResponseEntity.internalServerError().body(response);
        }
    }
}