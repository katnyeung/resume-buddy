package com.resumebuddy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuddy.model.Resume;
import com.resumebuddy.model.ResumeLine;
import com.resumebuddy.repository.ResumeLineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class EditorStateService {

    private final ResumeLineRepository resumeLineRepository;
    private final ObjectMapper objectMapper;

    /**
     * Parse Lexical editor state JSON and update resume_lines table
     *
     * @param resume The resume entity
     * @param editorStateJSON The Lexical editor state as JSON string
     */
    @Transactional
    public void updateResumeLinesFromEditorState(Resume resume, String editorStateJSON) {
        try {
            log.info("Parsing editor state and updating resume lines for resume ID: {}", resume.getId());

            // Parse the Lexical JSON
            JsonNode editorState = objectMapper.readTree(editorStateJSON);
            JsonNode root = editorState.path("root");
            JsonNode children = root.path("children");

            if (!children.isArray()) {
                log.warn("No children found in editor state root for resume ID: {}", resume.getId());
                return;
            }

            // Get existing resume lines
            List<ResumeLine> existingLines = resumeLineRepository.findByResumeIdOrderByLineNumber(resume.getId());
            log.info("Found {} existing resume lines for resume ID: {}", existingLines.size(), resume.getId());

            // Extract text content from Lexical nodes
            List<String> textLines = extractTextFromLexicalNodes(children);
            log.info("Extracted {} text lines from editor state", textLines.size());

            // Update existing lines or create new ones
            LocalDateTime now = LocalDateTime.now();
            List<ResumeLine> linesToSave = new ArrayList<>();

            for (int i = 0; i < textLines.size(); i++) {
                String content = textLines.get(i);
                int lineNumber = i + 1;

                ResumeLine line;
                if (i < existingLines.size()) {
                    // Update existing line
                    line = existingLines.get(i);
                    line.setContent(content);
                    line.setLineNumber(lineNumber);
                } else {
                    // Create new line
                    line = new ResumeLine();
                    line.setResume(resume);
                    line.setLineNumber(lineNumber);
                    line.setContent(content);
                }

                linesToSave.add(line);
            }

            // Delete lines that are no longer present (if user deleted lines)
            if (textLines.size() < existingLines.size()) {
                List<ResumeLine> linesToDelete = existingLines.subList(textLines.size(), existingLines.size());
                log.info("Deleting {} removed lines", linesToDelete.size());
                resumeLineRepository.deleteAll(linesToDelete);
            }

            // Save all lines
            resumeLineRepository.saveAll(linesToSave);
            log.info("Successfully updated {} resume lines for resume ID: {}", linesToSave.size(), resume.getId());

        } catch (Exception e) {
            log.error("Error updating resume lines from editor state for resume ID: {}", resume.getId(), e);
            throw new RuntimeException("Failed to update resume lines from editor state", e);
        }
    }

    /**
     * Recursively extract text content from Lexical JSON nodes
     *
     * @param nodes The array of Lexical node children
     * @return List of text content, one per logical line/paragraph
     */
    private List<String> extractTextFromLexicalNodes(JsonNode nodes) {
        List<String> lines = new ArrayList<>();

        if (!nodes.isArray()) {
            return lines;
        }

        for (JsonNode node : nodes) {
            String type = node.path("type").asText();

            // Handle different node types
            switch (type) {
                case "paragraph":
                case "heading":
                case "quote":
                    // Extract text from paragraph/heading/quote children
                    String text = extractTextFromChildren(node.path("children"));
                    lines.add(text);
                    break;

                case "list":
                    // For lists, each list item becomes a separate line
                    JsonNode listChildren = node.path("children");
                    if (listChildren.isArray()) {
                        for (JsonNode listItem : listChildren) {
                            if ("listitem".equals(listItem.path("type").asText())) {
                                String itemText = extractTextFromChildren(listItem.path("children"));
                                lines.add(itemText);
                            }
                        }
                    }
                    break;

                case "code":
                    // Extract code content
                    String code = node.path("text").asText("");
                    lines.add(code);
                    break;

                default:
                    // For unknown node types, try to extract text
                    String content = extractTextFromChildren(node.path("children"));
                    if (!content.isEmpty()) {
                        lines.add(content);
                    }
            }
        }

        return lines;
    }

    /**
     * Extract text content from child nodes (handles text, link, etc.)
     *
     * @param children The child nodes
     * @return Concatenated text content
     */
    private String extractTextFromChildren(JsonNode children) {
        StringBuilder text = new StringBuilder();

        if (!children.isArray()) {
            return text.toString();
        }

        for (JsonNode child : children) {
            String type = child.path("type").asText();

            if ("text".equals(type)) {
                // Direct text node
                text.append(child.path("text").asText(""));
            } else if ("link".equals(type)) {
                // Link node - extract text from its children
                text.append(extractTextFromChildren(child.path("children")));
            } else if ("linebreak".equals(type)) {
                // Line break within a paragraph
                text.append("\n");
            } else {
                // Recursively handle nested nodes
                text.append(extractTextFromChildren(child.path("children")));
            }
        }

        return text.toString();
    }
}
