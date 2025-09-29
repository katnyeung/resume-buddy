# Resume Buddy Frontend

Next.js 14+ frontend with Lexical editor for Resume Buddy application.

## Tech Stack

- **Framework**: Next.js 14+ with App Router
- **Language**: TypeScript
- **Editor**: Lexical (Facebook's extensible text editor)
- **Styling**: Tailwind CSS
- **HTTP Client**: Axios
- **File Upload**: react-dropzone

## Getting Started

### Install Dependencies

```bash
npm install
```

### Environment Variables

Create a `.env.local` file:

```bash
NEXT_PUBLIC_API_URL=http://localhost:8080/api
```

### Run Development Server

```bash
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) in your browser.

## Project Structure

```
frontend/
├── app/
│   ├── page.tsx                 # Resume list (home page)
│   ├── upload/page.tsx          # Upload resume
│   ├── edit/[id]/page.tsx       # Lexical editor
│   ├── layout.tsx               # Root layout
│   └── globals.css              # Global styles (includes Lexical styles)
├── components/
│   ├── LexicalEditor.tsx        # Main Lexical editor component
│   ├── ResumeList.tsx           # Resume cards display
│   ├── FileUploader.tsx         # Drag-drop file upload
│   └── plugins/
│       ├── OnChangePlugin.tsx   # Track editor changes
│       └── AutoFocusPlugin.tsx  # Auto-focus editor
├── lib/
│   ├── api.ts                   # Axios API client
│   ├── lexicalUtils.ts          # Editor state utilities
│   └── types.ts                 # TypeScript definitions
└── package.json
```

## Features

### Resume Management
- **List View**: Display all uploaded resumes with metadata
- **Upload**: Drag-and-drop file upload (PDF, DOCX, TXT)
- **Delete**: Remove resumes from the system
- **Status Tracking**: Visual status indicators (Uploaded, Parsing, Parsed, Failed)

### Lexical Editor
- **Word-style Editor**: Professional document editing experience
- **Line-based Editing**: Resume content managed line-by-line
- **Auto-save Ready**: Change detection and batch updates
- **Rich Text Support**: Bold, italic, underline formatting
- **History**: Undo/redo functionality

### API Integration
- Upload resume files
- Parse resumes with Docling service
- Load resume lines for editing
- Batch update modified lines
- Delete resumes

## Key Components

### LexicalEditor Component
Located at `components/LexicalEditor.tsx`

**Features:**
- Loads resume lines from backend
- Converts lines to Lexical EditorState
- Tracks changes with OnChangePlugin
- Saves modified lines with batch update
- Change detection to update only modified lines

**Props:**
- `resumeId: string` - Resume ID to edit

### FileUploader Component
Located at `components/FileUploader.tsx`

**Features:**
- Drag-and-drop file selection
- File type validation
- Visual feedback

**Props:**
- `onFileSelect: (file: File) => void` - Callback when file selected
- `disabled?: boolean` - Disable uploader

### ResumeList Component
Located at `components/ResumeList.tsx`

**Features:**
- Grid layout of resume cards
- Status badges
- Edit and delete actions
- Empty state

**Props:**
- `resumes: Resume[]` - Array of resumes
- `onDelete: (id: string) => void` - Delete callback

## API Client

Located at `lib/api.ts`

**Methods:**
- `uploadResume(file)` - Upload resume file
- `parseResume(id)` - Parse uploaded resume
- `getResume(id)` - Get resume metadata
- `listResumes()` - Get all resumes
- `deleteResume(id)` - Delete resume
- `getResumeLines(id)` - Get resume lines
- `batchUpdateLines(id, updates)` - Batch update lines
- `updateLine(id, lineNumber, content)` - Update single line
- `getLineCount(id)` - Get total line count

## Utility Functions

Located at `lib/lexicalUtils.ts`

**Functions:**
- `resumeLinesToEditorState(lines)` - Convert backend lines to Lexical JSON
- `editorStateToResumeLines(editorState)` - Convert Lexical state to line updates
- `detectLineChanges(original, current)` - Detect modified lines

## Styling

### Global Styles
Located at `app/globals.css`

**Lexical Editor Classes:**
- `.editor-input` - Main editor container (Word-style font)
- `.editor-paragraph` - Paragraph nodes
- `.editor-text-bold/italic/underline` - Text formatting
- `.editor-placeholder` - Placeholder text
- Custom scrollbar styles

### Tailwind Configuration
Standard Tailwind CSS with custom color variables.

## Development Workflow

1. **Start Backend**: Ensure Spring Boot backend is running on `http://localhost:8080`
2. **Start Frontend**: Run `npm run dev`
3. **Upload Resume**: Navigate to `/upload` and select a file
4. **Edit Resume**: After parsing, you'll be redirected to `/edit/[id]`
5. **Save Changes**: Click "Save" button to persist changes

## API Endpoints Used

All endpoints prefixed with `http://localhost:8080/api`:

- `POST /resumes/upload` - Upload resume
- `POST /resumes/{id}/parse` - Parse resume
- `GET /resumes` - List resumes
- `GET /resumes/{id}` - Get resume
- `DELETE /resumes/{id}` - Delete resume
- `GET /resumes/{id}/lines` - Get lines
- `PUT /resumes/{id}/lines/batch` - Batch update lines

## Future Enhancements (Phase 4)

The current implementation is designed to support:
- AI-powered suggestions
- ATS scoring
- Job matching
- Inline editing hints
- Smart formatting

The line-based architecture and modular plugin system make it easy to integrate AI features.

## Build for Production

```bash
npm run build
npm start
```

## Type Checking

```bash
npm run type-check
```
