package com.resumebuddy.controller;

import com.resumebuddy.model.dto.TailorResumeRequestDto;
import com.resumebuddy.model.dto.TailoredResumeResponseDto;
import com.resumebuddy.service.DocxGeneratorService;
import com.resumebuddy.service.ResumeTailoringService;
import com.resumebuddy.service.UserCreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/resumes")
@CrossOrigin(origins = "http://localhost:3000")
@RequiredArgsConstructor
@Tag(name = "Resume Tailoring", description = "Tailor resumes for specific job descriptions")
public class ResumeTailoringController {

    private final ResumeTailoringService resumeTailoringService;
    private final DocxGeneratorService docxGeneratorService;

    @PostMapping("/{resumeId}/tailor")
    @Operation(summary = "Tailor resume for job", description = "Generate a tailored resume optimized for a specific job description (costs 30 credits)")
    public ResponseEntity<?> tailorResume(
            @PathVariable String resumeId,
            @Valid @RequestBody TailorResumeRequestDto request,
            Authentication authentication) {

        String userId = (String) authentication.getPrincipal();
        log.info("User {} requesting resume tailoring for resume {}", userId, resumeId);

        try {
            TailoredResumeResponseDto response = resumeTailoringService.tailorResume(resumeId, userId, request);
            return ResponseEntity.ok(response);

        } catch (UserCreditService.InsufficientCreditsException e) {
            log.warn("User {} has insufficient credits for tailoring", userId);
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(Map.of(
                    "error", "Insufficient credits",
                    "message", e.getMessage(),
                    "required", resumeTailoringService.getTailoringCost()
                ));

        } catch (RuntimeException e) {
            log.error("Error tailoring resume {} for user {}: {}", resumeId, userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{resumeId}/tailor/download")
    @Operation(summary = "Download tailored resume as DOCX", description = "Convert already-tailored resume to Word document (no LLM call, no credits)")
    public ResponseEntity<?> downloadTailoredResume(
            @PathVariable String resumeId,
            @Valid @RequestBody TailoredResumeResponseDto tailoredResume,
            Authentication authentication) {

        String userId = (String) authentication.getPrincipal();
        log.info("User {} downloading DOCX for resume {} (no LLM call)", userId, resumeId);

        try {
            // Set resumeId from path (frontend may not include it)
            tailoredResume.setResumeId(resumeId);

            // Convert to DOCX - no LLM call, no credit deduction
            byte[] docxBytes = docxGeneratorService.generateDocx(tailoredResume);

            // Use LLM-suggested filename or fallback
            String baseFilename = tailoredResume.getSuggestedFilename();
            if (baseFilename == null || baseFilename.isBlank()) {
                baseFilename = "tailored_resume_" + resumeId.substring(0, 8);
            }
            // Sanitize filename - remove any unsafe characters
            baseFilename = baseFilename.replaceAll("[^a-zA-Z0-9_-]", "_");
            String filename = baseFilename + ".docx";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(docxBytes.length);

            return ResponseEntity.ok()
                .headers(headers)
                .body(docxBytes);

        } catch (Exception e) {
            log.error("Error generating DOCX for resume {} for user {}: {}", resumeId, userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to generate DOCX: " + e.getMessage()));
        }
    }

    @GetMapping("/tailor/cost")
    @Operation(summary = "Get tailoring cost", description = "Get the credit cost for resume tailoring")
    public ResponseEntity<Map<String, Integer>> getTailoringCost() {
        return ResponseEntity.ok(Map.of("cost", resumeTailoringService.getTailoringCost()));
    }

    @GetMapping("/{resumeId}/tailored")
    @Operation(summary = "Get tailored resume history", description = "Get all saved tailored resumes for a specific resume (no credits)")
    public ResponseEntity<?> getTailoredHistory(
            @PathVariable String resumeId,
            Authentication authentication) {

        String userId = (String) authentication.getPrincipal();
        log.info("User {} getting tailored history for resume {}", userId, resumeId);

        try {
            List<TailoredResumeResponseDto> history = resumeTailoringService.getTailoredHistory(resumeId, userId);
            return ResponseEntity.ok(history);
        } catch (RuntimeException e) {
            log.error("Error getting tailored history for resume {}: {}", resumeId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{resumeId}/tailored/{tailoredId}")
    @Operation(summary = "Get saved tailored resume", description = "Retrieve a previously saved tailored resume (no credits)")
    public ResponseEntity<?> getSavedTailoredResume(
            @PathVariable String resumeId,
            @PathVariable String tailoredId,
            Authentication authentication) {

        String userId = (String) authentication.getPrincipal();
        log.info("User {} getting saved tailored resume {}", userId, tailoredId);

        try {
            TailoredResumeResponseDto result = resumeTailoringService.getSavedTailoredResume(tailoredId, userId);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            log.error("Error getting tailored resume {}: {}", tailoredId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Tailored resume not found"));
        }
    }

    @GetMapping("/tailored/all")
    @Operation(summary = "Get all user's tailored resumes", description = "Get all saved tailored resumes for the current user (no credits)")
    public ResponseEntity<?> getAllUserTailoredResumes(Authentication authentication) {

        String userId = (String) authentication.getPrincipal();
        log.info("User {} getting all tailored resumes", userId);

        try {
            List<TailoredResumeResponseDto> history = resumeTailoringService.getAllUserTailoredResumes(userId);
            return ResponseEntity.ok(history);
        } catch (RuntimeException e) {
            log.error("Error getting all tailored resumes for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
}
