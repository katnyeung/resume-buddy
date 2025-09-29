package com.resumebuddy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    @Value("${app.file.upload-dir:./uploads}")
    private String uploadDir;

    public String storeFile(MultipartFile file, String resumeId) throws IOException {
        // Create uploads directory if it doesn't exist
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            log.info("Created upload directory: {}", uploadPath.toAbsolutePath());
        }

        // Generate unique filename with resume ID
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        String storedFilename = resumeId + "_" + UUID.randomUUID().toString() + fileExtension;

        Path targetLocation = uploadPath.resolve(storedFilename);

        // Copy file to target location
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        log.info("Stored file: {} -> {}", originalFilename, targetLocation.toAbsolutePath());

        // Return relative path from upload directory
        return storedFilename;
    }

    public byte[] loadFileAsBytes(String filePath) throws IOException {
        Path fullPath = Paths.get(uploadDir).resolve(filePath);

        if (!Files.exists(fullPath)) {
            throw new IOException("File not found: " + filePath);
        }

        return Files.readAllBytes(fullPath);
    }

    public Path getFilePath(String filePath) {
        return Paths.get(uploadDir).resolve(filePath);
    }

    public boolean deleteFile(String filePath) {
        try {
            Path fullPath = Paths.get(uploadDir).resolve(filePath);
            boolean deleted = Files.deleteIfExists(fullPath);
            if (deleted) {
                log.info("Deleted file: {}", fullPath.toAbsolutePath());
            }
            return deleted;
        } catch (IOException e) {
            log.error("Error deleting file: {}", filePath, e);
            return false;
        }
    }

    public boolean fileExists(String filePath) {
        Path fullPath = Paths.get(uploadDir).resolve(filePath);
        return Files.exists(fullPath);
    }

    public long getFileSize(String filePath) throws IOException {
        Path fullPath = Paths.get(uploadDir).resolve(filePath);
        return Files.size(fullPath);
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex) : "";
    }
}