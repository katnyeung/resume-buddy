package com.resumebuddy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.model.Resume;
import com.resumebuddy.model.ResumeLine;
import com.resumebuddy.model.dto.ParsedResume;
import com.resumebuddy.model.dto.ResumeLineUpdateDto;
import com.resumebuddy.repository.ResumeLineRepository;
import com.resumebuddy.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeLineService {

    private final ResumeLineRepository resumeLineRepository;
    private final ResumeRepository resumeRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processResumeLines(String resumeId) {
        log.info("Processing resume lines for resume ID: {}", resumeId);

        Optional<Resume> resumeOpt = resumeRepository.findById(resumeId);
        if (resumeOpt.isEmpty()) {
            log.warn("Resume not found with ID: {}", resumeId);
            return;
        }

        Resume resume = resumeOpt.get();
        String parsedContent = resume.getParsedContent();

        if (parsedContent == null || parsedContent.trim().isEmpty()) {
            log.warn("No parsed content found for resume ID: {}", resumeId);
            return;
        }

        try {
            // Parse the JSON content to extract originalText
            ParsedResume parsedResume = objectMapper.readValue(parsedContent, ParsedResume.class);
            String originalText = parsedResume.getOriginalText();

            if (originalText == null || originalText.trim().isEmpty()) {
                log.warn("No original text found in parsed content for resume ID: {}", resumeId);
                return;
            }

            // Clear existing lines for this resume
            resumeLineRepository.deleteByResumeId(resumeId);
            log.info("Cleared existing lines for resume ID: {}", resumeId);

            // Split text into lines and create ResumeLine entities
            String[] lines = originalText.split("\n");
            List<ResumeLine> resumeLines = new ArrayList<>();
            int lineNumber = 1;

            for (String lineContent : lines) {
                // Skip completely empty lines but preserve lines with whitespace
                if (lineContent != null) {
                    ResumeLine resumeLine = new ResumeLine();
                    resumeLine.setResume(resume);
                    resumeLine.setLineNumber(lineNumber);
                    resumeLine.setContent(lineContent);
                    resumeLines.add(resumeLine);
                    lineNumber++;
                }
            }

            // Batch insert all lines
            resumeLineRepository.saveAll(resumeLines);

            log.info("Successfully processed {} lines for resume ID: {}", resumeLines.size(), resumeId);

        } catch (Exception e) {
            log.error("Error processing resume lines for resume ID: {}", resumeId, e);
            throw new RuntimeException("Failed to process resume lines", e);
        }
    }

    public List<ResumeLine> getResumeLines(String resumeId) {
        log.info("Getting resume lines for resume ID: {}", resumeId);
        return resumeLineRepository.findByResumeIdOrderByLineNumber(resumeId);
    }

    @Transactional
    public ResumeLine updateLine(String resumeId, Integer lineNumber, String newContent) {
        log.info("Updating line {} for resume ID: {}", lineNumber, resumeId);

        Optional<ResumeLine> resumeLineOpt = resumeLineRepository.findByResumeIdAndLineNumber(resumeId, lineNumber);
        if (resumeLineOpt.isEmpty()) {
            throw new RuntimeException("Resume line not found for resume ID: " + resumeId + ", line: " + lineNumber);
        }

        ResumeLine resumeLine = resumeLineOpt.get();
        resumeLine.setContent(newContent);

        return resumeLineRepository.save(resumeLine);
    }

    @Transactional
    public ResumeLine insertLine(String resumeId, Integer lineNumber, String content) {
        log.info("Inserting line at position {} for resume ID: {}", lineNumber, resumeId);

        // Check if resume exists
        if (!resumeRepository.existsById(resumeId)) {
            throw new RuntimeException("Resume not found with ID: " + resumeId);
        }

        // Shift existing lines down
        List<ResumeLine> existingLines = resumeLineRepository.findByResumeIdOrderByLineNumber(resumeId);
        for (ResumeLine line : existingLines) {
            if (line.getLineNumber() >= lineNumber) {
                line.setLineNumber(line.getLineNumber() + 1);
                resumeLineRepository.save(line);
            }
        }

        // Get the resume entity
        Resume resume = resumeRepository.findById(resumeId)
            .orElseThrow(() -> new RuntimeException("Resume not found with ID: " + resumeId));

        // Create new line
        ResumeLine newLine = new ResumeLine();
        newLine.setResume(resume);
        newLine.setLineNumber(lineNumber);
        newLine.setContent(content);

        return resumeLineRepository.save(newLine);
    }


    public long getLineCount(String resumeId) {
        return resumeLineRepository.countByResumeId(resumeId);
    }

    @Transactional
    public List<ResumeLine> updateMultipleLines(String resumeId, List<ResumeLineUpdateDto> updates) {
        log.info("Updating {} lines for resume ID: {}", updates.size(), resumeId);

        // Get the resume entity
        Resume resume = resumeRepository.findById(resumeId)
            .orElseThrow(() -> new RuntimeException("Resume not found with ID: " + resumeId));

        List<ResumeLine> updatedLines = new ArrayList<>();

        for (ResumeLineUpdateDto update : updates) {
            Optional<ResumeLine> resumeLineOpt = resumeLineRepository.findByResumeIdAndLineNumber(resumeId, update.getLineNumber());

            if (resumeLineOpt.isPresent()) {
                // Update existing line
                ResumeLine resumeLine = resumeLineOpt.get();
                resumeLine.setContent(update.getContent());
                updatedLines.add(resumeLineRepository.save(resumeLine));
                log.debug("Updated line {} for resume ID: {}", update.getLineNumber(), resumeId);
            } else {
                // Append new line if it doesn't exist
                ResumeLine newLine = new ResumeLine();
                newLine.setResume(resume);
                newLine.setLineNumber(update.getLineNumber());
                newLine.setContent(update.getContent());
                updatedLines.add(resumeLineRepository.save(newLine));
                log.debug("Appended new line {} for resume ID: {}", update.getLineNumber(), resumeId);
            }
        }

        log.info("Successfully updated/appended {} lines for resume ID: {}", updatedLines.size(), resumeId);
        return updatedLines;
    }
}