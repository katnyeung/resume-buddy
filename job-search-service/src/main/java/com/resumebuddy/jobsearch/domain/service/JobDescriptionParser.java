package com.resumebuddy.jobsearch.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Domain Service: Job Description Parser
 * Parses unstructured job descriptions into meaningful lines for vectorization
 */
@Service
@Slf4j
public class JobDescriptionParser {

    private static final int MIN_LINE_LENGTH = 20;
    private static final int MAX_LINE_LENGTH = 150; // Short chunks for granular matching
    private static final int IDEAL_LINE_LENGTH = 100; // Target length for sentence chunks

    // Patterns for bullet points
    private static final Pattern BULLET_PATTERN = Pattern.compile("^[•\\-*⦿▪◦]\\s*");
    private static final Pattern NUMBERED_PATTERN = Pattern.compile("^\\d+[.)\\s]+");

    // Regex pattern to detect natural sentence/line boundaries
    // Matches: period/exclamation/question mark + space + capital letter OR end of string
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("([.!?]+)\\s+(?=[A-Z])|([.!?]+)$");

    /**
     * Parse job description into meaningful lines
     *
     * Strategy:
     * 0. Strip HTML tags and decode HTML entities
     * 1. Split on bullet points (•, -, *, numbered lists)
     * 2. Split on double newlines (paragraphs)
     * 3. Split long paragraphs on sentence boundaries
     * 4. Clean and filter lines
     *
     * @param description Raw job description text (may contain HTML)
     * @return List of parsed lines
     */
    public List<String> parseDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            log.warn("Empty job description provided");
            return new ArrayList<>();
        }

        log.debug("Parsing job description ({} chars)", description.length());

        // Step 0: Strip HTML tags and decode entities FIRST
        description = stripHtml(description);
        log.debug("After HTML stripping: {} chars", description.length());

        List<String> lines = new ArrayList<>();

        // Step 1: First, split by ANY newline (single or double)
        // This handles job descriptions with \n characters
        String[] allLines = description.split("\\n+");

        log.debug("Initial split by newlines: {} raw lines", allLines.length);

        for (String rawLine : allLines) {
            if (rawLine == null || rawLine.trim().isEmpty()) {
                continue;
            }

            String trimmed = rawLine.trim();

            // Step 2: If line is very long (>IDEAL_LINE_LENGTH), further split it
            if (trimmed.length() > IDEAL_LINE_LENGTH) {
                log.debug("Line too long ({} chars), splitting further", trimmed.length());
                List<String> splitLines = splitLongLine(trimmed);
                if (!splitLines.isEmpty()) {
                    lines.addAll(splitLines);
                } else {
                    // Fallback: add the long line as-is
                    String cleaned = cleanLine(trimmed);
                    if (isValidLine(cleaned)) {
                        lines.add(cleaned);
                    }
                }
            } else {
                // Step 3: Line is reasonable length, just clean and add
                String cleaned = cleanLine(trimmed);
                if (isValidLine(cleaned)) {
                    lines.add(cleaned);
                }
            }
        }

        log.info("Parsed {} lines from job description ({} raw lines)", lines.size(), allLines.length);
        return lines;
    }

    /**
     * Check if text contains bullet points
     */
    private boolean containsBulletPoints(String text) {
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (BULLET_PATTERN.matcher(line.trim()).find() ||
                NUMBERED_PATTERN.matcher(line.trim()).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Clean a line: remove bullets, trim, normalize whitespace
     */
    private String cleanLine(String line) {
        if (line == null) {
            return "";
        }

        // Remove bullet points
        line = BULLET_PATTERN.matcher(line).replaceFirst("");
        line = NUMBERED_PATTERN.matcher(line).replaceFirst("");

        // Normalize whitespace
        line = line.replaceAll("\\s+", " ").trim();

        // Remove trailing punctuation if it's a colon (often used before lists)
        if (line.endsWith(":")) {
            line = line.substring(0, line.length() - 1).trim();
        }

        return line;
    }

    /**
     * Check if line is valid for vectorization
     */
    private boolean isValidLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }

        // Too short
        if (line.length() < MIN_LINE_LENGTH) {
            return false;
        }

        // Filter out common header/footer noise
        String lower = line.toLowerCase();
        if (lower.matches("^(about us|company|benefits|perks|job description|responsibilities|requirements|qualifications|role requirements)$")) {
            return false;
        }

        return true;
    }

    /**
     * Split long lines on sentence boundaries using regex pattern matching
     * Detects: period/exclamation/question + space + capital letter
     */
    private List<String> splitLongLine(String line) {
        List<String> result = new ArrayList<>();

        log.debug("Splitting long line ({} chars)", line.length());

        // Strategy 1: Use regex to find natural sentence boundaries
        // Split on pattern: [.!?]+ followed by space and capital letter
        String[] sentences = SENTENCE_BOUNDARY.split(line);

        // Always try period split first (most common sentence delimiter)
        // Try splitting on period alone (without capital letter requirement)
        String[] parts = line.split("\\.\\s+");
        if (parts.length > 1) {
            log.debug("Split on period: {} parts", parts.length);
            sentences = parts;
        } else if (sentences.length == 1) {
            // If regex didn't split AND period split didn't work, try other delimiters
            log.debug("No sentence boundaries found, trying alternative delimiters");

            // Try splitting on semicolon
            parts = line.split(";\\s+");
            if (parts.length > 1) {
                log.debug("Split on semicolon: {} parts", parts.length);
                sentences = parts;
            } else {
                // Try splitting on comma (but only if reasonable number of parts)
                parts = line.split(",\\s+");
                if (parts.length >= 2 && parts.length <= 12) {
                    log.debug("Split on comma: {} parts", parts.length);
                    sentences = parts;
                }
            }
        } else {
            log.debug("Regex split into {} sentences", sentences.length);
        }

        // Process each sentence/fragment
        for (String sentence : sentences) {
            if (sentence == null || sentence.trim().isEmpty()) {
                continue;
            }

            String cleaned = cleanLine(sentence);

            // If still too long (>300 chars), FORCE split at word boundaries
            if (cleaned.length() > MAX_LINE_LENGTH) {
                log.debug("Sentence still too long ({} chars), FORCE chunking at word boundaries", cleaned.length());
                List<String> chunks = forceChunkLine(cleaned, MAX_LINE_LENGTH);
                log.debug("Force chunk produced {} chunks", chunks.size());
                for (String chunk : chunks) {
                    if (isValidLine(chunk)) {
                        result.add(chunk);
                    }
                }
            } else if (isValidLine(cleaned)) {
                result.add(cleaned);
            }
        }

        // FALLBACK: If STILL no valid sentences, ALWAYS force chunk
        if (result.isEmpty() && line.length() > MAX_LINE_LENGTH) {
            log.warn("All splitting strategies failed, FORCING chunk of entire line ({} chars)", line.length());
            List<String> chunks = forceChunkLine(line, MAX_LINE_LENGTH);
            log.debug("Final force chunk produced {} chunks", chunks.size());
            for (String chunk : chunks) {
                String cleaned = cleanLine(chunk);
                if (isValidLine(cleaned)) {
                    result.add(cleaned);
                }
            }
        } else if (result.isEmpty()) {
            // Line is short enough, just add it
            String cleaned = cleanLine(line);
            if (isValidLine(cleaned)) {
                result.add(cleaned);
            }
        }

        log.debug("Splitting complete: produced {} lines from input", result.size());
        return result;
    }

    /**
     * Force split a line into chunks at word boundaries
     */
    private List<String> forceChunkLine(String line, int maxLength) {
        List<String> chunks = new ArrayList<>();
        String[] words = line.split("\\s+");

        StringBuilder currentChunk = new StringBuilder();
        for (String word : words) {
            if (currentChunk.length() + word.length() + 1 > maxLength) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }
            }
            if (currentChunk.length() > 0) {
                currentChunk.append(" ");
            }
            currentChunk.append(word);
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    /**
     * Parse and number lines (for display purposes)
     */
    public List<ParsedLine> parseAndNumber(String description) {
        List<String> lines = parseDescription(description);
        List<ParsedLine> numbered = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            numbered.add(new ParsedLine(i + 1, lines.get(i)));
        }

        return numbered;
    }

    /**
     * Strip HTML tags and decode common HTML entities
     *
     * Examples:
     * - <p>Text</p> → Text
     * - <strong>Bold</strong> → Bold
     * - <ul><li>Item</li></ul> → Item
     * - &nbsp; → (space)
     * - &amp; → &
     */
    private String stripHtml(String html) {
        if (html == null) {
            return "";
        }

        // Replace common block-level HTML tags with newlines (preserve structure)
        html = html.replaceAll("</p>", "\n");
        html = html.replaceAll("</div>", "\n");
        html = html.replaceAll("</li>", "\n");
        html = html.replaceAll("<br\\s*/?>", "\n");
        html = html.replaceAll("</h[1-6]>", "\n");

        // Remove ALL remaining HTML tags (opening and closing)
        html = html.replaceAll("<[^>]+>", " ");

        // Decode common HTML entities
        html = html.replace("&nbsp;", " ")
                   .replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&quot;", "\"")
                   .replace("&#39;", "'")
                   .replace("&apos;", "'")
                   .replace("&mdash;", "—")
                   .replace("&ndash;", "–")
                   .replace("&bull;", "•")
                   .replace("&middot;", "·")
                   .replace("&hellip;", "…");

        // Normalize whitespace (collapse multiple spaces/newlines)
        html = html.replaceAll("[ \\t]+", " "); // Multiple spaces → single space
        html = html.replaceAll("\\n{3,}", "\n\n"); // 3+ newlines → 2 newlines

        return html.trim();
    }

    /**
     * Simple DTO for numbered lines
     */
    public static class ParsedLine {
        private final int lineNumber;
        private final String content;

        public ParsedLine(int lineNumber, String content) {
            this.lineNumber = lineNumber;
            this.content = content;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getContent() {
            return content;
        }
    }
}
