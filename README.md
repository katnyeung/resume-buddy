# Complete Backend Controllers for TipTap Integration

## 🔗 Missing Backend-Frontend Communication

You're absolutely correct! Here's the complete backend controller structure that connects with the TipTap editor for real-time editing and AI suggestions.

## 📁 Updated Backend Structure

```
backend/
├── src/main/java/com/resumebuddy/api/
│   ├── ResumeApplication.java
│   ├── controller/
│   │   ├── ResumeController.java          # File upload & basic operations
│   │   ├── EditingController.java         # Real-time editing endpoints
│   │   ├── AnalysisController.java        # AI analysis endpoints
│   │   └── SuggestionController.java      # AI suggestions endpoints
│   ├── service/
│   │   ├── ResumeParsingService.java
│   │   ├── EditingService.java            # Content editing logic
│   │   ├── AnalysisService.java           # AI analysis service
│   │   └── SuggestionService.java         # AI suggestion service
│   ├── model/
│   │   ├── ParsedResume.java
│   │   ├── dto/                           # Data Transfer Objects
│   │   │   ├── EditRequest.java
│   │   │   ├── AnalysisRequest.java
│   │   │   ├── SuggestionRequest.java
│   │   │   └── SuggestionResponse.java
│   └── config/
       └── CorsConfig.java                 # CORS configuration
```

## 🎯 1. EditingController.java - Real-time TipTap Communication

```java
package com.resumebuddy.api.controller;

import com.resumebuddy.api.model.ParsedResume;
import com.resumebuddy.api.model.dto.*;
import com.resumebuddy.api.service.EditingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/editing")
@CrossOrigin(origins = "http://localhost:3000")
@RequiredArgsConstructor
@Tag(name = "Resume Editing", description = "Real-time resume editing operations")
public class EditingController {

    private final EditingService editingService;

    @PostMapping("/save-content")
    @Operation(summary = "Save edited content", description = "Save the current editor content")
    public ResponseEntity<SaveContentResponse> saveContent(@Valid @RequestBody SaveContentRequest request) {
        log.info("Saving content for resume: {}", request.getResumeId());
        
        SaveContentResponse response = editingService.saveContent(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/auto-save")
    @Operation(summary = "Auto-save content", description = "Background auto-save during editing")
    public ResponseEntity<AutoSaveResponse> autoSave(@Valid @RequestBody AutoSaveRequest request) {
        log.debug("Auto-saving content for resume: {}", request.getResumeId());
        
        AutoSaveResponse response = editingService.autoSave(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/update-block")
    @Operation(summary = "Update specific block", description = "Update a specific resume block")
    public ResponseEntity<UpdateBlockResponse> updateBlock(@Valid @RequestBody UpdateBlockRequest request) {
        log.info("Updating block: {} for resume: {}", request.getBlockId(), request.getResumeId());
        
        UpdateBlockResponse response = editingService.updateBlock(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reorder-blocks")
    @Operation(summary = "Reorder resume blocks", description = "Change the order of resume sections")
    public ResponseEntity<ReorderResponse> reorderBlocks(@Valid @RequestBody ReorderRequest request) {
        log.info("Reordering blocks for resume: {}", request.getResumeId());
        
        ReorderResponse response = editingService.reorderBlocks(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/resume/{resumeId}/history")
    @Operation(summary = "Get edit history", description = "Retrieve editing history for a resume")
    public ResponseEntity<List<EditHistoryEntry>> getEditHistory(@PathVariable String resumeId) {
        log.info("Getting edit history for resume: {}", resumeId);
        
        List<EditHistoryEntry> history = editingService.getEditHistory(resumeId);
        return ResponseEntity.ok(history);
    }
}
```

## 🤖 2. AnalysisController.java - AI Analysis Endpoints

```java
package com.resumebuddy.api.controller;

import com.resumebuddy.api.model.dto.*;
import com.resumebuddy.api.service.AnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/analysis")
@CrossOrigin(origins = "http://localhost:3000")
@RequiredArgsConstructor
@Tag(name = "AI Analysis", description = "AI-powered resume analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    @PostMapping("/analyze-text")
    @Operation(summary = "Analyze selected text", description = "Analyze text selection from TipTap editor")
    public ResponseEntity<TextAnalysisResponse> analyzeText(@Valid @RequestBody TextAnalysisRequest request) {
        log.info("Analyzing text selection: {} characters", request.getSelectedText().length());
        
        TextAnalysisResponse response = analysisService.analyzeText(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/analyze-block")
    @Operation(summary = "Analyze resume block", description = "Deep analysis of a specific resume section")
    public ResponseEntity<BlockAnalysisResponse> analyzeBlock(@Valid @RequestBody BlockAnalysisRequest request) {
        log.info("Analyzing block: {} of type: {}", request.getBlockId(), request.getBlockType());
        
        BlockAnalysisResponse response = analysisService.analyzeBlock(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/calculate-ats-score")
    @Operation(summary = "Calculate ATS score", description = "Calculate ATS compatibility score")
    public ResponseEntity<ATSScoreResponse> calculateATSScore(@Valid @RequestBody ATSScoreRequest request) {
        log.info("Calculating ATS score for resume: {}", request.getResumeId());
        
        ATSScoreResponse response = analysisService.calculateATSScore(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/extract-keywords")
    @Operation(summary = "Extract keywords", description = "Extract and highlight important keywords")
    public ResponseEntity<KeywordExtractionResponse> extractKeywords(@Valid @RequestBody KeywordExtractionRequest request) {
        log.info("Extracting keywords from content: {} characters", request.getContent().length());
        
        KeywordExtractionResponse response = analysisService.extractKeywords(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/job-match")
    @Operation(summary = "Match with jobs", description = "Find matching job opportunities")
    public ResponseEntity<JobMatchResponse> matchJobs(@Valid @RequestBody JobMatchRequest request) {
        log.info("Matching jobs for skills: {}", request.getSkills());
        
        JobMatchResponse response = analysisService.matchJobs(request);
        return ResponseEntity.ok(response);
    }
}
```

## 💡 3. SuggestionController.java - AI Suggestions

```java
package com.resumebuddy.api.controller;

import com.resumebuddy.api.model.dto.*;
import com.resumebuddy.api.service.SuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/suggestions")
@CrossOrigin(origins = "http://localhost:3000")
@RequiredArgsConstructor
@Tag(name = "AI Suggestions", description = "AI-powered content suggestions")
public class SuggestionController {

    private final SuggestionService suggestionService;

    @PostMapping("/get-suggestions")
    @Operation(summary = "Get content suggestions", description = "Get AI suggestions for selected text")
    public ResponseEntity<List<AISuggestion>> getSuggestions(@Valid @RequestBody SuggestionRequest request) {
        log.info("Getting suggestions for text: '{}' in context: {}", 
                 request.getSelectedText(), request.getContext());
        
        List<AISuggestion> suggestions = suggestionService.getSuggestions(request);
        return ResponseEntity.ok(suggestions);
    }

    @PostMapping("/apply-suggestion")
    @Operation(summary = "Apply suggestion", description = "Apply an AI suggestion to the resume")
    public ResponseEntity<ApplySuggestionResponse> applySuggestion(@Valid @RequestBody ApplySuggestionRequest request) {
        log.info("Applying suggestion: {} to resume: {}", request.getSuggestionId(), request.getResumeId());
        
        ApplySuggestionResponse response = suggestionService.applySuggestion(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/grammar-check")
    @Operation(summary = "Check grammar", description = "Check grammar and style of content")
    public ResponseEntity<GrammarCheckResponse> checkGrammar(@Valid @RequestBody GrammarCheckRequest request) {
        log.info("Checking grammar for {} characters", request.getContent().length());
        
        GrammarCheckResponse response = suggestionService.checkGrammar(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/enhance-section")
    @Operation(summary = "Enhance section", description = "Get section-specific enhancement suggestions")
    public ResponseEntity<SectionEnhancementResponse> enhanceSection(@Valid @RequestBody SectionEnhancementRequest request) {
        log.info("Enhancing section: {} of type: {}", request.getSectionId(), request.getSectionType());
        
        SectionEnhancementResponse response = suggestionService.enhanceSection(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/suggestion-history/{resumeId}")
    @Operation(summary = "Get suggestion history", description = "Get history of applied suggestions")
    public ResponseEntity<List<SuggestionHistoryEntry>> getSuggestionHistory(@PathVariable String resumeId) {
        log.info("Getting suggestion history for resume: {}", resumeId);
        
        List<SuggestionHistoryEntry> history = suggestionService.getSuggestionHistory(resumeId);
        return ResponseEntity.ok(history);
    }
}
```

## 📝 4. Data Transfer Objects (DTOs)

### EditRequest.java
```java
package com.resumebuddy.api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SaveContentRequest {
    @NotBlank
    private String resumeId;
    
    @NotBlank
    private String content;
    
    private String contentType = "html"; // html, markdown, plain
    
    @NotNull
    private Long timestamp;
    
    private String userAction; // manual_save, auto_save, etc.
}

@Data
public class SaveContentResponse {
    private String resumeId;
    private boolean success;
    private String message;
    private Long savedAt;
    private String version;
}

@Data
public class AutoSaveRequest {
    @NotBlank
    private String resumeId;
    
    @NotBlank
    private String content;
    
    @NotNull
    private Long timestamp;
    
    private String cursorPosition;
}

@Data
public class AutoSaveResponse {
    private boolean success;
    private Long savedAt;
    private String message;
}

@Data
public class UpdateBlockRequest {
    @NotBlank
    private String resumeId;
    
    @NotBlank
    private String blockId;
    
    @NotBlank
    private String newContent;
    
    private String blockType;
    private Integer position;
}

@Data
public class UpdateBlockResponse {
    private String blockId;
    private boolean success;
    private String message;
    private Long updatedAt;
}
```

### AnalysisRequest.java
```java
package com.resumebuddy.api.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class TextAnalysisRequest {
    @NotBlank
    private String selectedText;
    
    private String context; // surrounding text
    private String blockType; // EXPERIENCE, SKILLS, etc.
    private String resumeId;
    private Integer startPosition;
    private Integer endPosition;
}

@Data
public class TextAnalysisResponse {
    private List<AISuggestion> suggestions;
    private List<Keyword> keywords;
    private List<GrammarIssue> grammarIssues;
    private Double atsScore;
    private List<String> improvements;
    private Long analysisTimestamp;
}

@Data
public class BlockAnalysisRequest {
    @NotBlank
    private String resumeId;
    
    @NotBlank
    private String blockId;
    
    @NotBlank
    private String content;
    
    @NotBlank
    private String blockType;
    
    private String context;
}

@Data
public class BlockAnalysisResponse {
    private String blockId;
    private String blockType;
    private List<AISuggestion> suggestions;
    private List<String> strengths;
    private List<String> weaknesses;
    private Double relevanceScore;
    private List<String> missingElements;
    private List<JobMatch> relatedJobs;
}
```

### SuggestionRequest.java
```java
package com.resumebuddy.api.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SuggestionRequest {
    @NotBlank
    private String selectedText;
    
    private String context;
    private String blockType;
    private String resumeId;
    private String suggestionType; // improvement, grammar, ats, enhancement
}

@Data
public class AISuggestion {
    private String id;
    private String type; // improvement, grammar, ats, enhancement
    private String title;
    private String originalText;
    private String suggestedText;
    private String reasoning;
    private Double confidence;
    private String category; // skills, experience, education, etc.
    private Integer priority; // 1-5
    private Long createdAt;
}

@Data
public class ApplySuggestionRequest {
    @NotBlank
    private String resumeId;
    
    @NotBlank
    private String suggestionId;
    
    @NotBlank
    private String blockId;
    
    private String originalText;
    private String suggestedText;
    private Integer startPosition;
    private Integer endPosition;
}

@Data
public class ApplySuggestionResponse {
    private boolean success;
    private String message;
    private String updatedContent;
    private Long appliedAt;
    private String suggestionId;
}
```

## 🔄 5. Frontend API Integration

### Updated `lib/api.ts` - Complete API Client
```typescript
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 30000,
});

export const resumeAPI = {
  // Existing upload endpoint
  uploadResume: async (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await api.post('/resumes/upload', formData);
    return response.data;
  },

  // Editing endpoints
  saveContent: async (resumeId: string, content: string) => {
    const response = await api.post('/editing/save-content', {
      resumeId,
      content,
      timestamp: Date.now(),
      userAction: 'manual_save'
    });
    return response.data;
  },

  autoSave: async (resumeId: string, content: string) => {
    const response = await api.post('/editing/auto-save', {
      resumeId,
      content,
      timestamp: Date.now()
    });
    return response.data;
  },

  updateBlock: async (resumeId: string, blockId: string, newContent: string) => {
    const response = await api.post('/editing/update-block', {
      resumeId,
      blockId,
      newContent
    });
    return response.data;
  },

  // Analysis endpoints
  analyzeText: async (selectedText: string, context: string, blockType: string) => {
    const response = await api.post('/analysis/analyze-text', {
      selectedText,
      context,
      blockType
    });
    return response.data;
  },

  analyzeBlock: async (resumeId: string, blockId: string, content: string, blockType: string) => {
    const response = await api.post('/analysis/analyze-block', {
      resumeId,
      blockId,
      content,
      blockType
    });
    return response.data;
  },

  calculateATSScore: async (resumeId: string, content: string) => {
    const response = await api.post('/analysis/calculate-ats-score', {
      resumeId,
      content
    });
    return response.data;
  },

  // Suggestion endpoints
  getSuggestions: async (selectedText: string, context: string, blockType: string) => {
    const response = await api.post('/suggestions/get-suggestions', {
      selectedText,
      context,
      blockType
    });
    return response.data;
  },

  applySuggestion: async (resumeId: string, suggestionId: string, blockId: string, originalText: string, suggestedText: string) => {
    const response = await api.post('/suggestions/apply-suggestion', {
      resumeId,
      suggestionId,
      blockId,
      originalText,
      suggestedText
    });
    return response.data;
  },

  checkGrammar: async (content: string) => {
    const response = await api.post('/suggestions/grammar-check', {
      content
    });
    return response.data;
  }
};
```

## 🔄 6. TipTap Editor Integration

### Updated `RichResumeEditor.tsx` with Real API Calls
```typescript
import { useCallback, useEffect, useState } from 'react';
import { useEditor, EditorContent } from '@tiptap/react';
import { resumeAPI } from '../lib/api';
import { debounce } from 'lodash';

export const RichResumeEditor: React.FC<RichResumeEditorProps> = ({
  parsedResume,
  onContentChange
}) => {
  const [activeBlock, setActiveBlock] = useState<string | null>(null);
  const [suggestions, setSuggestions] = useState<AISuggestion[]>([]);
  const [loading, setLoading] = useState(false);

  // Debounced auto-save
  const debouncedAutoSave = useCallback(
    debounce(async (content: string) => {
      try {
        await resumeAPI.autoSave(parsedResume.id, content);
        console.log('✅ Auto-saved successfully');
      } catch (error) {
        console.error('❌ Auto-save failed:', error);
      }
    }, 2000),
    [parsedResume.id]
  );

  const editor = useEditor({
    extensions: [StarterKit, Highlight],
    content: generateInitialContent(parsedResume),
    onUpdate: ({ editor }) => {
      const content = editor.getHTML();
      onContentChange?.(content);
      debouncedAutoSave(content);
    },
    onSelectionUpdate: ({ editor }) => {
      const { from, to } = editor.state.selection;
      if (from !== to) {
        const selectedText = editor.state.doc.textBetween(from, to);
        handleTextSelection(selectedText);
      }
    }
  });

  // Handle text selection for suggestions
  const handleTextSelection = useCallback(async (selectedText: string) => {
    if (selectedText.length < 3) return;

    setLoading(true);
    try {
      const response = await resumeAPI.getSuggestions(
        selectedText,
        getContextAroundSelection(),
        getActiveBlockType()
      );
      setSuggestions(response);
    } catch (error) {
      console.error('Error getting suggestions:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  // Handle block click for analysis
  const handleBlockClick = useCallback(async (blockId: string) => {
    setActiveBlock(blockId);
    setLoading(true);

    try {
      const block = getBlockById(blockId);
      const response = await resumeAPI.analyzeBlock(
        parsedResume.id,
        blockId,
        block.content,
        block.type
      );
      // Handle analysis response
      console.log('Block analysis:', response);
    } catch (error) {
      console.error('Error analyzing block:', error);
    } finally {
      setLoading(false);
    }
  }, [parsedResume.id]);

  // Manual save function
  const handleSave = useCallback(async () => {
    if (!editor) return;

    try {
      const content = editor.getHTML();
      await resumeAPI.saveContent(parsedResume.id, content);
      console.log('✅ Resume saved successfully');
    } catch (error) {
      console.error('❌ Save failed:', error);
    }
  }, [editor, parsedResume.id]);

  // Apply suggestion
  const applySuggestion = useCallback(async (suggestion: AISuggestion) => {
    if (!editor) return;

    try {
      const { from, to } = editor.state.selection;
      
      // Apply to editor
      editor.chain()
        .focus()
        .insertContentAt({ from, to }, suggestion.suggestedText)
        .run();

      // Save to backend
      await resumeAPI.applySuggestion(
        parsedResume.id,
        suggestion.id,
        activeBlock || '',
        suggestion.originalText,
        suggestion.suggestedText
      );

      setSuggestions([]);
    } catch (error) {
      console.error('Error applying suggestion:', error);
    }
  }, [editor, parsedResume.id, activeBlock]);

  return (
    <div className="rich-editor-container">
      {/* Save button */}
      <button onClick={handleSave} className="save-btn">
        💾 Save Resume
      </button>

      {/* Editor */}
      <EditorContent editor={editor} />

      {/* Suggestions */}
      {suggestions.length > 0 && (
        <SuggestionPopover
          suggestions={suggestions}
          onApply={applySuggestion}
          loading={loading}
        />
      )}
    </div>
  );
};
```

## 🔗 Communication Flow Summary

### 1. **User Types in TipTap** → Auto-save
```
User edits → onUpdate → debouncedAutoSave → POST /editing/auto-save
```

### 2. **User Selects Text** → Get Suggestions
```
Text selection → onSelectionUpdate → POST /suggestions/get-suggestions → Show popover
```

### 3. **User Clicks Block** → Analyze Section
```
Block click → handleBlockClick → POST /analysis/analyze-block → Show analysis
```

### 4. **User Applies Suggestion** → Update Content
```
Apply suggestion → POST /suggestions/apply-suggestion → Update editor → Auto-save
```

This complete backend structure now provides all the endpoints needed for real-time TipTap editor communication!
