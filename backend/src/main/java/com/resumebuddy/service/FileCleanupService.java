package com.resumebuddy.service;

import com.resumebuddy.model.Resume;
import com.resumebuddy.model.ResumeStatus;
import com.resumebuddy.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service: File Cleanup
 *
 * Automatically deletes uploaded resume files from disk after successful parsing
 * to save storage space. Parsed content is stored in PostgreSQL, so original
 * files are not needed after PARSED or ANALYZED status is reached.
 *
 * Cleanup strategy:
 * 1. Keep files in UPLOADED/PARSING status (parsing may fail and need retry)
 * 2. Delete files in PARSED/ANALYZED status after retention period (default: 7 days)
 * 3. Keep files in FAILED status for debugging (manual cleanup)
 *
 * Runs once per day by default.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileCleanupService {

    private final ResumeRepository resumeRepository;
    private final FileStorageService fileStorageService;

    @Value("${app.file.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    @Value("${app.file.cleanup.retention-days:7}")
    private int retentionDays;

    /**
     * Scheduled cleanup task
     * Runs once per day (86400000 ms = 24 hours)
     */
    @Scheduled(fixedRate = 86400000, initialDelay = 3600000) // 24 hours, start after 1 hour
    @Transactional
    public void cleanupOldFiles() {
        if (!cleanupEnabled) {
            log.debug("File cleanup is disabled via configuration");
            return;
        }

        log.info("Starting scheduled file cleanup (retention: {} days)...", retentionDays);

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);

        // Find PARSED and ANALYZED resumes older than retention period
        List<Resume> parsedResumes = resumeRepository.findByStatusAndUpdatedAtBefore(
            ResumeStatus.PARSED.name(),
            cutoffDate
        );

        List<Resume> analyzedResumes = resumeRepository.findByStatusAndUpdatedAtBefore(
            ResumeStatus.ANALYZED.name(),
            cutoffDate
        );

        // Combine both lists
        List<Resume> resumesToCleanup = new java.util.ArrayList<>(parsedResumes);
        resumesToCleanup.addAll(analyzedResumes);

        int deletedCount = 0;
        int errorCount = 0;
        long totalBytesFreed = 0;

        for (Resume resume : resumesToCleanup) {
            if (resume.getFilePath() == null) {
                continue; // Already cleaned up or never had a file
            }

            try {
                // Get file size before deletion
                long fileSize = fileStorageService.getFileSize(resume.getFilePath());

                // Delete file from disk
                boolean deleted = fileStorageService.deleteFile(resume.getFilePath());

                if (deleted) {
                    // Update database to clear file path
                    resume.setFilePath(null);
                    resumeRepository.save(resume);

                    deletedCount++;
                    totalBytesFreed += fileSize;

                    log.debug("Deleted file for resume {}: {} ({} bytes)",
                        resume.getId(),
                        resume.getFilename(),
                        fileSize
                    );
                } else {
                    log.warn("Failed to delete file for resume {}: {}",
                        resume.getId(),
                        resume.getFilePath()
                    );
                    errorCount++;
                }
            } catch (Exception e) {
                log.error("Error deleting file for resume {}: ",
                    resume.getId(),
                    e
                );
                errorCount++;
            }
        }

        if (deletedCount > 0 || errorCount > 0) {
            log.info("File cleanup completed: {} files deleted ({} MB freed), {} errors",
                deletedCount,
                totalBytesFreed / 1024 / 1024,
                errorCount
            );
        } else {
            log.debug("File cleanup completed: no files to delete");
        }
    }

    /**
     * Manual cleanup for a specific resume
     * Called after successful parsing to immediately free disk space
     */
    @Transactional
    public boolean cleanupResumeFile(String resumeId) {
        Resume resume = resumeRepository.findById(resumeId).orElse(null);

        if (resume == null) {
            log.warn("Cannot cleanup file: resume {} not found", resumeId);
            return false;
        }

        if (resume.getFilePath() == null) {
            log.debug("Resume {} has no file to cleanup", resumeId);
            return false;
        }

        // Only cleanup if status is PARSED or ANALYZED (content is stored in database)
        if (!ResumeStatus.PARSED.name().equals(resume.getStatus())
            && !ResumeStatus.ANALYZED.name().equals(resume.getStatus())) {
            log.debug("Resume {} is in {} status, skipping cleanup",
                resumeId,
                resume.getStatus()
            );
            return false;
        }

        try {
            long fileSize = fileStorageService.getFileSize(resume.getFilePath());
            boolean deleted = fileStorageService.deleteFile(resume.getFilePath());

            if (deleted) {
                resume.setFilePath(null);
                resumeRepository.save(resume);

                log.info("Cleaned up file for resume {}: {} ({} KB freed)",
                    resumeId,
                    resume.getFilename(),
                    fileSize / 1024
                );
                return true;
            }
        } catch (Exception e) {
            log.error("Error cleaning up file for resume {}: ", resumeId, e);
        }

        return false;
    }

    /**
     * Get statistics about cleanable files
     */
    public CleanupStats getCleanupStats() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);

        List<Resume> parsedResumes = resumeRepository.findByStatusAndUpdatedAtBefore(
            ResumeStatus.PARSED.name(),
            cutoffDate
        );

        List<Resume> analyzedResumes = resumeRepository.findByStatusAndUpdatedAtBefore(
            ResumeStatus.ANALYZED.name(),
            cutoffDate
        );

        // Combine both lists
        List<Resume> resumesToCleanup = new java.util.ArrayList<>(parsedResumes);
        resumesToCleanup.addAll(analyzedResumes);

        long totalSize = 0;
        int fileCount = 0;

        for (Resume resume : resumesToCleanup) {
            if (resume.getFilePath() != null) {
                try {
                    totalSize += fileStorageService.getFileSize(resume.getFilePath());
                    fileCount++;
                } catch (Exception e) {
                    log.debug("Cannot get size for file: {}", resume.getFilePath());
                }
            }
        }

        return new CleanupStats(fileCount, totalSize);
    }

    /**
     * Statistics class
     */
    public static class CleanupStats {
        public final int fileCount;
        public final long totalBytes;
        public final long totalMB;

        public CleanupStats(int fileCount, long totalBytes) {
            this.fileCount = fileCount;
            this.totalBytes = totalBytes;
            this.totalMB = totalBytes / 1024 / 1024;
        }
    }
}
