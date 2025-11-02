package com.resumebuddy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.model.Resume;
import com.resumebuddy.model.ResumeStatus;
import com.resumebuddy.model.dto.ParsedResume;
import com.resumebuddy.repository.ResumeRepository;
import com.resumebuddy.service.DoclingHttpService;
import com.resumebuddy.service.FileStorageService;
import com.resumebuddy.service.Neo4jGraphService;
import com.resumebuddy.service.ResumeLineService;
import com.resumebuddy.service.UserCreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/resumes")
@CrossOrigin(origins = "http://localhost:3000")
@RequiredArgsConstructor
@Tag(name = "Resume Management", description = "Resume upload and basic operations")
public class ResumeController {

    private final ResumeRepository resumeRepository;
    private final DoclingHttpService doclingHttpService;
    private final FileStorageService fileStorageService;
    private final ResumeLineService resumeLineService;
    private final Neo4jGraphService neo4jGraphService;
    private final UserCreditService userCreditService;
    private final com.resumebuddy.service.EditorStateService editorStateService;

    @Value("${app.token-costs.resume-upload}")
    private int resumeUploadCost;

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if the service is running")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Resume Buddy API is running!");
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @Operation(summary = "Upload resume", description = "Upload and parse a resume file (costs 50 credits)")
    public ResponseEntity<?> uploadResume(
            @RequestParam("file")
            @io.swagger.v3.oas.annotations.Parameter(
                description = "Resume file to upload (PDF, DOCX, TXT, or image files)",
                content = @io.swagger.v3.oas.annotations.media.Content(
                    mediaType = "multipart/form-data"
                )
            ) MultipartFile file,
            Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        log.info("User {} uploading file: {} ({})", userId, file.getOriginalFilename(), file.getContentType());

        // Check if user has enough credits
        try {
            var userCredit = userCreditService.getUserCredit(userId);
            if (userCredit.getAvailableCredits().compareTo(BigDecimal.valueOf(resumeUploadCost)) < 0) {
                log.warn("User {} has insufficient credits: {} < {}", userId, userCredit.getAvailableCredits(), resumeUploadCost);
                return ResponseEntity.status(402) // Payment Required
                        .body(java.util.Map.of(
                                "error", "Insufficient credits",
                                "message", "You need " + resumeUploadCost + " credits to upload a resume. Available: " + userCredit.getAvailableCredits().intValue(),
                                "required", resumeUploadCost,
                                "available", userCredit.getAvailableCredits().intValue()
                        ));
            }
        } catch (Exception e) {
            log.error("Error checking user credits for user {}: ", userId, e);
            return ResponseEntity.status(500)
                    .body(java.util.Map.of("error", "Failed to check credits"));
        }

        // Validate file
        if (file.isEmpty()) {
            log.warn("Empty file received");
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "Empty file"));
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("application/pdf")
                && !contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                && !contentType.equals("text/plain")
                && !contentType.startsWith("image/"))) {
            log.warn("Unsupported file type: {}", contentType);
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "Unsupported file type"));
        }

        try {
            // Generate temporary ID for file storage
            String tempId = java.util.UUID.randomUUID().toString();

            // Deduct credits BEFORE processing (jobId, userId, amount, description)
            userCreditService.deductCredits(userId, BigDecimal.valueOf(resumeUploadCost), "upload-" + tempId, "Resume upload: " + file.getOriginalFilename());
            log.info("Deducted {} credits from user {} for resume upload", resumeUploadCost, userId);

            // Store file first using temp ID
            String filePath = fileStorageService.storeFile(file, tempId);

            // Create resume entity with file path
            Resume resume = new Resume();
            resume.setFilename(file.getOriginalFilename());
            resume.setContentType(file.getContentType());
            resume.setFileSize(file.getSize());
            resume.setFilePath(filePath);
            resume.setStatus(ResumeStatus.UPLOADED.name());
            resume.setUserId(userId); // Link to authenticated user
            // createdAt and updatedAt will be set automatically by JPA

            // Save resume with file path
            Resume savedResume = resumeRepository.save(resume);
            log.info("Resume uploaded with ID: {} and status: {}", savedResume.getId(), savedResume.getStatus());

            // Create response without file path for security
            Resume responseResume = new Resume();
            responseResume.setId(savedResume.getId());
            responseResume.setFilename(savedResume.getFilename());
            responseResume.setContentType(savedResume.getContentType());
            responseResume.setFileSize(savedResume.getFileSize());
            responseResume.setStatus(savedResume.getStatus());
            responseResume.setCreatedAt(savedResume.getCreatedAt());
            responseResume.setUpdatedAt(savedResume.getUpdatedAt());
            // Note: filePath and parsedContent are excluded for security

            return ResponseEntity.ok(responseResume);
        } catch (Exception e) {
            log.error("Error uploading resume: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get resume", description = "Get resume by ID")
    public ResponseEntity<Resume> getResume(@PathVariable String id) {
        log.info("Getting resume with ID: {}", id);

        Optional<Resume> resume = resumeRepository.findById(id);
        return resume.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/parsed")
    @Operation(summary = "Get parsed resume", description = "Get structured resume data by ID")
    public ResponseEntity<ParsedResume> getParsedResume(@PathVariable String id) {
        log.info("Getting parsed resume with ID: {}", id);

        Optional<Resume> resume = resumeRepository.findById(id);
        if (resume.isPresent() && resume.get().getParsedContent() != null) {
            try {
                ParsedResume parsedResume = new ObjectMapper().readValue(
                    resume.get().getParsedContent(),
                    ParsedResume.class
                );
                return ResponseEntity.ok(parsedResume);
            } catch (Exception e) {
                log.error("Error parsing JSON content for resume {}: ", id, e);
                return ResponseEntity.internalServerError().build();
            }
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/file")
    @Operation(summary = "Get resume file", description = "Download the original resume file")
    public ResponseEntity<byte[]> getResumeFile(@PathVariable String id) {
        log.info("Getting file for resume ID: {}", id);

        Optional<Resume> resume = resumeRepository.findById(id);
        if (resume.isPresent()) {
            Resume resumeEntity = resume.get();

            try {
                // Load file from disk
                byte[] fileBytes = fileStorageService.loadFileAsBytes(resumeEntity.getFilePath());

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType(resumeEntity.getContentType()));
                headers.setContentDispositionFormData("attachment", resumeEntity.getFilename());
                headers.setContentLength(fileBytes.length);

                return ResponseEntity.ok()
                        .headers(headers)
                        .body(fileBytes);

            } catch (Exception e) {
                log.error("Error loading file for resume {}: ", id, e);
                return ResponseEntity.internalServerError().build();
            }
        }

        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/parse")
    @Operation(summary = "Parse resume", description = "Start parsing an uploaded resume")
    public ResponseEntity<Resume> parseResume(@PathVariable String id) {
        log.info("Starting parsing for resume ID: {}", id);

        Optional<Resume> resumeOpt = resumeRepository.findById(id);
        if (resumeOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Resume resume = resumeOpt.get();

        // Allow parsing from any status (commented out status check)
        // if (!ResumeStatus.UPLOADED.name().equals(resume.getStatus())) {
        //     log.warn("Resume {} is not in UPLOADED status. Current status: {}", id, resume.getStatus());
        //     return ResponseEntity.badRequest().build();
        // }

        try {
            // Set status to PARSING
            resume.setStatus(ResumeStatus.PARSING.name());
            // updatedAt will be set automatically by JPA
            resumeRepository.save(resume);

            // Load file from disk
            byte[] fileContent = fileStorageService.loadFileAsBytes(resume.getFilePath());
            if (fileContent == null || fileContent.length == 0) {
                throw new RuntimeException("Resume file content is empty or could not be loaded");
            }

            // Parse with Docling HTTP service (delegates to RunPod with base64)
            ParsedResume parsedResume = doclingHttpService.parseResumeFromBytes(
                fileContent,
                resume.getFilename(),
                resume.getContentType(),
                resume.getId()
            );

            // Store the parsed content as JSON and update status
            resume.setParsedContent(doclingHttpService.convertToJson(parsedResume));
            resume.setStatus(ResumeStatus.PARSED.name());
            // updatedAt will be set automatically by JPA

            Resume savedResume = resumeRepository.save(resume);
            log.info("Resume parsing completed for ID: {} with status: {}", savedResume.getId(), savedResume.getStatus());

            // Process resume lines for TipTap integration
            try {
                resumeLineService.processResumeLines(savedResume.getId());
                log.info("Successfully processed resume lines for ID: {}", savedResume.getId());
            } catch (Exception lineProcessingError) {
                log.error("Error processing resume lines for ID: {}", savedResume.getId(), lineProcessingError);
                // Don't fail the whole parsing process if line processing fails
            }

            return ResponseEntity.ok(savedResume);
        } catch (Exception e) {
            log.error("Error parsing resume: ", e);

            // Set status to FAILED
            resume.setStatus(ResumeStatus.FAILED.name());
            // updatedAt will be set automatically by JPA
            resumeRepository.save(resume);

            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping
    @Operation(summary = "List resumes", description = "Get all resumes for authenticated user")
    public ResponseEntity<List<Resume>> listResumes(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        log.info("Listing resumes for user: {}", userId);

        List<Resume> resumes = resumeRepository.findByUserId(userId);
        return ResponseEntity.ok(resumes);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete resume", description = "Delete resume by ID")
    public ResponseEntity<Void> deleteResume(@PathVariable String id) {
        log.info("Deleting resume with ID: {}", id);

        Optional<Resume> resume = resumeRepository.findById(id);
        if (resume.isPresent()) {
            Resume resumeEntity = resume.get();

            // Delete file from disk first
            if (resumeEntity.getFilePath() != null) {
                boolean fileDeleted = fileStorageService.deleteFile(resumeEntity.getFilePath());
                if (!fileDeleted) {
                    log.warn("Failed to delete file: {}", resumeEntity.getFilePath());
                }
            }

            // Delete from Neo4j graph database
            try {
                neo4jGraphService.deleteResumeFromGraph(id);
                log.info("Successfully deleted graph data for resume {}", id);
            } catch (Exception e) {
                log.error("Error deleting graph data for resume {}: {}", id, e.getMessage());
                // Continue with deletion even if graph cleanup fails
            }

            // Delete from database (this will cascade to related tables via JPA)
            resumeRepository.deleteById(id);
            log.info("Successfully deleted resume {} and associated file", id);

            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/editor-state")
    @Operation(summary = "Save editor state", description = "Save Lexical editor state as JSON and update resume_lines")
    public ResponseEntity<Resume> saveEditorState(
            @PathVariable String id,
            @RequestBody String editorState) {
        log.info("Saving editor state for resume ID: {}", id);

        Optional<Resume> resumeOpt = resumeRepository.findById(id);
        if (resumeOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            Resume resume = resumeOpt.get();
            resume.setEditorState(editorState);
            Resume savedResume = resumeRepository.save(resume);

            // Parse editor state and update resume_lines table
            editorStateService.updateResumeLinesFromEditorState(savedResume, editorState);

            log.info("Successfully saved editor state and updated resume lines for resume ID: {}", id);
            return ResponseEntity.ok(savedResume);
        } catch (Exception e) {
            log.error("Error saving editor state for resume ID: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/editor-state")
    @Operation(summary = "Get editor state", description = "Get Lexical editor state JSON")
    public ResponseEntity<String> getEditorState(@PathVariable String id) {
        log.info("Getting editor state for resume ID: {}", id);

        Optional<Resume> resumeOpt = resumeRepository.findById(id);
        if (resumeOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Resume resume = resumeOpt.get();
        if (resume.getEditorState() != null) {
            return ResponseEntity.ok(resume.getEditorState());
        } else {
            // Return empty editor state if not yet saved
            return ResponseEntity.ok("null");
        }
    }
}