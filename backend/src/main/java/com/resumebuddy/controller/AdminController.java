package com.resumebuddy.controller;

import com.resumebuddy.service.FileCleanupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "http://localhost:3000")
@RequiredArgsConstructor
@Tag(name = "Admin Operations", description = "Administrative endpoints for system maintenance")
public class AdminController {

    private final FileCleanupService fileCleanupService;

    @PostMapping("/cleanup-files")
    @Operation(
        summary = "Trigger file cleanup",
        description = "Manually trigger cleanup of old resume files to free disk space"
    )
    public ResponseEntity<?> triggerCleanup() {
        log.info("Manual file cleanup triggered");
        fileCleanupService.cleanupOldFiles();
        return ResponseEntity.ok(Map.of(
            "message", "File cleanup completed",
            "status", "success"
        ));
    }

    @GetMapping("/cleanup-stats")
    @Operation(
        summary = "Get cleanup statistics",
        description = "Get information about files eligible for cleanup"
    )
    public ResponseEntity<?> getCleanupStats() {
        FileCleanupService.CleanupStats stats = fileCleanupService.getCleanupStats();
        return ResponseEntity.ok(Map.of(
            "fileCount", stats.fileCount,
            "totalBytes", stats.totalBytes,
            "totalMB", stats.totalMB,
            "message", stats.fileCount + " files (" + stats.totalMB + " MB) eligible for cleanup"
        ));
    }
}
