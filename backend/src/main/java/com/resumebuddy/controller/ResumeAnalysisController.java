package com.resumebuddy.controller;

import com.resumebuddy.model.dto.AnalysisResultDto;
import com.resumebuddy.model.dto.LineAnalysisDto;
import com.resumebuddy.model.dto.ResumeAnalysisDto;
import com.resumebuddy.service.ResumeAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/resumes")
@CrossOrigin(origins = "http://localhost:3000")
@RequiredArgsConstructor
@Tag(name = "Resume Analysis", description = "AI-powered resume analysis and structured data extraction")
public class ResumeAnalysisController {

    private final ResumeAnalysisService resumeAnalysisService;

    @PostMapping("/{id}/analyze")
    @Operation(summary = "Analyze resume with AI", description = "Perform line-by-line AI analysis and extract structured data in a single operation")
    public ResponseEntity<AnalysisResultDto> analyzeResume(@PathVariable String id) {
        log.info("Received request to analyze resume ID: {}", id);

        try {
            List<LineAnalysisDto> analyses = resumeAnalysisService.analyzeResume(id);

            if (analyses.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            AnalysisResultDto result = new AnalysisResultDto();
            result.setResumeId(id);
            result.setAnalyzedAt(LocalDateTime.now());
            result.setTotalLines(analyses.size());
            result.setAnalyzedLines(analyses.size());
            result.setLineAnalyses(analyses);

            log.info("Successfully analyzed resume ID: {} with {} lines", id, analyses.size());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error analyzing resume ID: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/structured-analysis")
    @Operation(summary = "Get structured analysis for a resume", description = "Returns structured resume analysis including contact info, experiences, skills, education, certifications, and projects")
    public ResponseEntity<ResumeAnalysisDto> getStructuredAnalysis(@PathVariable String id) {
        log.info("Getting structured analysis for resume ID: {}", id);

        ResumeAnalysisDto analysis = resumeAnalysisService.getStructuredAnalysis(id);

        if (analysis == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(analysis);
    }

    @GetMapping("/{id}/analysis-exists")
    @Operation(summary = "Check if structured analysis exists", description = "Returns true if the resume has structured analysis data")
    public ResponseEntity<Boolean> analysisExists(@PathVariable String id) {
        boolean exists = resumeAnalysisService.analysisExists(id);
        return ResponseEntity.ok(exists);
    }
}
