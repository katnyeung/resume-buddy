# Resume Buddy - AI-Powered Resume Enhancement Platform

## Project Overview
Resume Buddy is an MVP application that enables users to upload resumes and leverage AI for comprehensive job search assistance, scoring, and description analysis. The platform provides real-time editing with TipTap integration, AI-powered suggestions, and job matching capabilities.

## Tech Stack

### Backend
- **Framework**: Spring Boot 3.2.1 with Java 17+ (Undertow Server)
- **Database**: MySQL 8.0 with Spring Data JPA
- **Document Parsing**: Docling HTTP microservice (Python FastAPI + Docker)
- **API Documentation**: Swagger/OpenAPI 3 with springdoc
- **Dependencies**: Spring Web, Spring Data JPA, Spring Validation

### Frontend
- **Framework**: Next.js 14+ with TypeScript
- **Editor**: Lexical rich text editor with full formatting support and markdown parsing
- **UI Library**: Tailwind CSS with modern components
- **State Management**: React hooks and context API
- **HTTP Client**: Axios for API communication
- **File Upload**: react-dropzone for drag & drop functionality
- **Components**:
  - LexicalEditor - Main editor with toolbar and plugins
  - AnalysisSummary - ATS-style structured analysis display (top of editor)
  - AnalysisOverlay - Line-by-line grouped analysis display (middle section)
  - ToolbarPlugin - Rich text formatting controls
  - OnChangePlugin - Editor state change detection
  - AutoFocusPlugin - Editor focus management

### AI Services
- **OpenAI Integration**: GPT-4 for resume analysis and section detection
- **Analysis Features**: Line-by-line section identification and content grouping

### Development Tools
- **Build Tool**: Maven (backend), npm/yarn (frontend)
- **Code Quality**: ESLint, Prettier (frontend), Checkstyle (backend)
- **Testing**: Jest + React Testing Library (frontend), JUnit 5 (backend)

## Project Structure

```
resume-buddy/
├── backend/                               # Spring Boot API (Undertow)
│   ├── src/main/java/com/resumebuddy/
│   │   ├── ResumeApplication.java
│   │   ├── controller/
│   │   │   ├── ResumeController.java      # File upload & API operations
│   │   │   └── ResumeLineController.java  # Line-based resume editing
│   │   ├── service/
│   │   │   ├── DoclingHttpService.java    # HTTP client for Docling service
│   │   │   ├── FileStorageService.java    # File storage operations
│   │   │   ├── ResumeLineService.java     # Line processing for editing
│   │   │   ├── AIAnalysisService.java     # OpenAI integration for line-by-line AI analysis
│   │   │   └── ResumeAnalysisService.java # Structured analysis retrieval with date sorting
│   │   ├── model/
│   │   │   ├── Resume.java                # Main resume entity
│   │   │   ├── ResumeLine.java            # Line-based resume content
│   │   │   ├── Suggestion.java            # AI-powered suggestions
│   │   │   ├── ResumeStatus.java          # Status enumeration
│   │   │   └── dto/                       # Data Transfer Objects
│   │   ├── repository/
│   │   │   ├── ResumeRepository.java
│   │   │   └── ResumeLineRepository.java
│   │   └── config/
│   │       ├── CorsConfig.java
│   │       ├── RestTemplateConfig.java
│   │       └── OpenApiConfig.java
│   ├── src/main/resources/
│   │   └── application.yml
│   └── pom.xml
├── docling-service/                       # Python Docling Microservice
│   ├── app.py                             # FastAPI application
│   ├── requirements.txt
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── docker-start.sh
├── frontend/                              # Next.js Application
│   ├── src/
│   │   ├── app/
│   │   │   ├── page.tsx                   # Resume list page
│   │   │   ├── edit/[id]/                 # Resume editing
│   │   │   └── upload/                    # Resume upload
│   │   ├── components/
│   │   │   ├── ResumeItem.tsx             # Resume card component
│   │   │   ├── LexicalEditor.tsx          # Lexical editor with analysis display
│   │   │   ├── AnalysisSummary.tsx        # ATS structured analysis display
│   │   │   ├── AnalysisOverlay.tsx        # Line-based grouped analysis display
│   │   │   └── plugins/
│   │   │       ├── ToolbarPlugin.tsx      # Rich text toolbar
│   │   │       ├── OnChangePlugin.tsx     # State change handler
│   │   │       └── AutoFocusPlugin.tsx    # Focus management
│   │   ├── lib/
│   │   │   ├── api.ts                     # API integration
│   │   │   └── utils.ts                   # Utility functions
│   │   └── types/
│   │       └── resume.ts                  # TypeScript definitions
│   ├── package.json
│   └── tailwind.config.js
├── start-with-docker.sh                   # Docker-based startup script
├── stop-with-docker.sh                    # Docker-based shutdown script
├── start-all.sh                           # Complete startup script
├── stop-all.sh                            # Complete shutdown script
├── README.md                              # Project overview
└── CLAUDE.md                              # Detailed documentation
```

## Current Implementation

### 1. Resume Upload & Parsing
- **File Support**: PDF, DOCX, TXT formats
- **Advanced Parsing**: Docling HTTP microservice with Docker
- **Storage**: MySQL database with Spring Data JPA
- **Workflow**: Upload → Parse → Edit workflow with status tracking
- **UI**: Drag-and-drop file upload interface with status feedback

### 2. Line-based Resume Editing
- **Content Organization**: Resume content split into line-by-line format
- **CRUD Operations**: Create, read, update, and delete operations for resume lines
- **Content Processing**: Automatic line processing from parsed content
- **Consistent Storage**: Proper relationship management between Resume and ResumeLine entities
- **Editor Interface**: TipTap-based rich text editing for individual lines

### 3. Frontend Implementation
- **Resume List**: Homepage showing all uploaded resumes with status
- **Upload Interface**: Drag-and-drop file uploader with validation
- **Edit Interface**: Split-pane editor with line selection and editing
- **Navigation**: Easy navigation between upload, edit, and listing views
- **Responsive Design**: Mobile-friendly layout with Tailwind CSS

### 4. API Structure
- **RESTful Design**: Well-structured REST API with Spring Web
- **Swagger Documentation**: Interactive API documentation with springdoc
- **Error Handling**: Proper exception handling and response status codes
- **Health Endpoints**: Health checks for monitoring
- **Frontend Integration**: Complete API client with Axios for frontend-backend communication

### 5. Database Schema

Current schema includes:

```sql
-- Resume storage
CREATE TABLE resumes (
    id VARCHAR(36) PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100),
    file_path VARCHAR(255) NOT NULL,
    file_size BIGINT,
    parsed_content JSON,
    editor_state LONGTEXT,             -- Lexical editor state JSON
    status VARCHAR(20),                -- UPLOADED, PARSED, ANALYZED
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Line-by-line resume content with AI analysis
CREATE TABLE resume_lines (
    id VARCHAR(36) PRIMARY KEY,
    resume_id VARCHAR(36) NOT NULL,
    line_number INT NOT NULL,
    content TEXT,
    -- AI Analysis fields (populated after analysis)
    section_type VARCHAR(50),           -- CONTACT, EXPERIENCE, EDUCATION, SKILLS, etc.
    group_id INT,                       -- Groups related lines (e.g., same job entry)
    group_type VARCHAR(50),             -- JOB, PROJECT, EDUCATION_ITEM, SKILL_CATEGORY, etc.
    analysis_notes TEXT,                -- AI findings and notes for this line
    analyzed_at TIMESTAMP,              -- When this line was last analyzed
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (resume_id) REFERENCES resumes(id) ON DELETE CASCADE
);

-- Structured resume analysis (ATS-style parsed data)
CREATE TABLE resume_analysis (
    id VARCHAR(36) PRIMARY KEY,
    resume_id VARCHAR(36) NOT NULL UNIQUE,
    -- Contact Information
    name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(50),
    linkedin_url VARCHAR(500),
    github_url VARCHAR(500),
    website_url VARCHAR(500),
    -- Professional Summary
    summary TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (resume_id) REFERENCES resumes(id) ON DELETE CASCADE
);

-- Work experience entries
CREATE TABLE resume_analysis_experience (
    id VARCHAR(36) PRIMARY KEY,
    analysis_id VARCHAR(36) NOT NULL,
    job_title VARCHAR(255),
    company_name VARCHAR(255),
    start_date VARCHAR(100),           -- Flexible string format
    end_date VARCHAR(100),             -- "Present" or actual date
    description TEXT,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (analysis_id) REFERENCES resume_analysis(id) ON DELETE CASCADE
);

-- Skills
CREATE TABLE resume_analysis_skill (
    id VARCHAR(36) PRIMARY KEY,
    analysis_id VARCHAR(36) NOT NULL,
    skill_name VARCHAR(255),
    category VARCHAR(100),             -- Backend Development, DevOps, etc.
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (analysis_id) REFERENCES resume_analysis(id) ON DELETE CASCADE
);

-- Education
CREATE TABLE resume_analysis_education (
    id VARCHAR(36) PRIMARY KEY,
    analysis_id VARCHAR(36) NOT NULL,
    degree VARCHAR(255),
    institution VARCHAR(255),
    graduation_date VARCHAR(100),
    description TEXT,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (analysis_id) REFERENCES resume_analysis(id) ON DELETE CASCADE
);

-- Certifications
CREATE TABLE resume_analysis_certification (
    id VARCHAR(36) PRIMARY KEY,
    analysis_id VARCHAR(36) NOT NULL,
    certification_name VARCHAR(255),
    issuing_organization VARCHAR(255),
    issue_date VARCHAR(100),
    credential_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (analysis_id) REFERENCES resume_analysis(id) ON DELETE CASCADE
);

-- Projects
CREATE TABLE resume_analysis_project (
    id VARCHAR(36) PRIMARY KEY,
    analysis_id VARCHAR(36) NOT NULL,
    project_name VARCHAR(255),
    description TEXT,
    technologies_used TEXT,
    project_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (analysis_id) REFERENCES resume_analysis(id) ON DELETE CASCADE
);

-- AI suggestions (future use)
CREATE TABLE suggestions (
    id VARCHAR(36) PRIMARY KEY,
    resume_id VARCHAR(36) NOT NULL,
    line_number INT,
    suggestion_type VARCHAR(50),
    original_text LONGTEXT,
    suggested_text LONGTEXT,
    reasoning LONGTEXT,
    confidence DECIMAL(3,2),
    applied BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP,
    FOREIGN KEY (resume_id) REFERENCES resumes(id) ON DELETE CASCADE
);
```

## API Endpoints

### Resume Management
- `POST /api/resumes/upload` - Upload resume file
- `POST /api/resumes/{id}/parse` - Parse uploaded resume
- `GET /api/resumes/{id}` - Get resume metadata
- `GET /api/resumes/{id}/parsed` - Get structured resume data
- `GET /api/resumes` - List all resumes
- `DELETE /api/resumes/{id}` - Delete resume

### Resume Line Editing
- `GET /api/resumes/{id}/lines` - Get all lines for a resume
- `POST /api/resumes/{id}/process-lines` - Process resume into lines
- `PUT /api/resumes/{id}/lines/{lineNumber}` - Update specific line
- `POST /api/resumes/{id}/lines` - Insert a new line
- `GET /api/resumes/{id}/lines/count` - Get total line count
- `PUT /api/resumes/{id}/lines/batch` - Batch update multiple lines

### Editor State Management
- `PUT /api/resumes/{id}/editor-state` - Save Lexical editor state with formatting
- `GET /api/resumes/{id}/editor-state` - Load Lexical editor state

### AI Analysis (Line-based)
- `POST /api/resumes/{id}/analyze` - Analyze resume with AI (line-by-line section detection, grouping)
- `GET /api/resumes/{id}/analysis-status` - Check if resume has been analyzed

### AI Analysis (Structured)
- `GET /api/resumes/{id}/structured-analysis` - Get structured analysis data (ATS-style parsed resume)
- `GET /api/resumes/{id}/analysis-exists` - Check if structured analysis exists

### Health & Utility
- `GET /api/resumes/health` - Backend health check
- `GET /health` - Docling service health check

## Development Commands

### Backend Commands
```bash
# Run backend development server
cd backend && mvn spring-boot:run

# Run tests
mvn test

# Build for production
mvn clean package

# Clean build
mvn clean compile
```

### Frontend Commands
```bash
# Install dependencies
cd frontend && npm install

# Run development server
npm run dev

# Build for production
npm run build

# Type check
npm run type-check
```

### Docling Service Commands
```bash
# Start Docling microservice with Docker
cd docling-service && ./docker-start.sh

# Or manually with docker-compose
cd docling-service && docker-compose up -d

# View logs
docker-compose logs -f

# Stop service
docker-compose down
```

### Full Stack Commands
```bash
# Start complete system
./start-with-docker.sh

# Stop all services
./stop-with-docker.sh
```

## Environment Configuration

### Backend (application.yml)
```yaml
spring:
  application:
    name: resume-buddy-api

  datasource:
    url: jdbc:mysql://localhost:3306/resumebuddy?useSSL=false&serverTimezone=UTC&createDatabaseIfNotExist=true
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: update
    database-platform: org.hibernate.dialect.MySQL8Dialect
    show-sql: true

  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

server:
  port: 8080

# Custom Application Properties
app:
  openai:
    api-key: ${OPENAI_API_KEY:your-openai-api-key}
    model: ${OPENAI_MODEL:gpt-4}

  file:
    upload-dir: ${UPLOAD_DIR:./uploads}

  docling:
    service-url: ${DOCLING_SERVICE_URL:http://localhost:8081}
```

### Frontend (.env.local)
```bash
# API Configuration
NEXT_PUBLIC_API_URL=http://localhost:8080/api
NEXT_PUBLIC_UPLOAD_MAX_SIZE=10485760

# Feature Flags
NEXT_PUBLIC_ENABLE_AI_SUGGESTIONS=false
NEXT_PUBLIC_ENABLE_JOB_MATCHING=false
```

## MVP Implementation Status

### Phase 1: Core Infrastructure
1. ✅ Set up Spring Boot backend with basic CRUD operations
2. ✅ Implement Docling microservice with Docker for document parsing
3. ✅ Create MySQL database with JPA entities
4. ✅ Set up Swagger API documentation

### Phase 2: Resume Processing
1. ✅ Implement file upload and storage
2. ✅ Integrate with Docling service for parsing
3. ✅ Create line-based content representation
4. ✅ Implement line editing operations

### Phase 3: Frontend Integration
1. ✅ Create Next.js frontend
2. ✅ Integrate Lexical editor with full formatting support
3. ✅ Implement resume management UI
4. ✅ Add markdown parsing from Docling output
5. ✅ Implement block type selector (H1-H6, Normal)
6. ✅ Add editor state persistence with formatting
7. ✅ Preserve empty lines in resume structure

### Phase 4: AI Integration & Structured Analysis
1. ✅ Add analysis fields to ResumeLine entity (Completed)
2. ✅ Create AIAnalysisService with OpenAI integration (Completed)
3. ✅ Implement line-by-line analysis API endpoint (Completed)
4. ✅ Create structured analysis database schema (Completed)
   - ResumeAnalysis entity with contact info and professional summary
   - ResumeAnalysisExperience with job details (title, company, dates, description)
   - ResumeAnalysisSkill with skill names and categories
   - ResumeAnalysisEducation with degree, institution, graduation date
   - ResumeAnalysisCertification with certification name, issuer, dates, credential ID
   - ResumeAnalysisProject with project name, description, technologies
5. ✅ Implement structured analysis service (Completed)
   - Extract contact information (name, email, phone, LinkedIn, GitHub, website)
   - Extract professional summary
   - Parse and store work experiences with full details
   - Parse and store skills with categorization
   - Parse and store education, certifications, and projects
6. ✅ Frontend analysis UI implementation (Completed)
   - AnalysisSummary component displaying ATS-parsed data at top of editor
   - Statistics cards showing counts (experiences, skills, education, certs, projects)
   - Collapsible detailed view with full information for each section
   - Job numbering (Job 1, Job 2, etc.) with action buttons
   - Date-based sorting (most recent first) for work experiences
7. ✅ Line-based analysis overlay (Completed)
   - AnalysisOverlay component showing grouped line analysis
   - Color-coded sections (CONTACT, EXPERIENCE, EDUCATION, SKILLS, etc.)
   - Expandable groups with content preview and AI insights
   - Job numbering with extracted titles from content
8. ✅ Action buttons for future enhancements (Completed)
   - "Analyze Job" button for each work experience (placeholder)
   - "Find Similar Jobs" button for each work experience (placeholder)
9. 📋 Add ATS scoring functionality (Planned)
10. 📋 Implement job-specific analysis (Planned)
11. 📋 Implement job search integration (Planned)

### Phase 5: Job Matching
1. 📋 Implement job search API integration (Planned)
2. 📋 Create skill matching algorithms (Planned)
3. 📋 Add job recommendation features (Planned)
4. 📋 Implement relevance scoring (Planned)

## Testing Strategy

### Backend Testing
- Unit tests for services and controllers
- Integration tests for API endpoints
- Database testing with TestContainers MySQL
- AI service mocking for reliable tests

### Frontend Testing
- Component testing with React Testing Library
- Integration testing for editor functionality
- API mocking with MSW
- E2E testing with Playwright

## Deployment

### Development
- Docling Service: `cd docling-service && docker-compose up -d`
- Backend: `mvn spring-boot:run`
- Frontend: `cd frontend && npm run dev`
- Database: Local MySQL server

### Production (Planned)
- Docling Service: Docker container deployment
- Backend: JAR deployment on cloud platforms
- Frontend: Vercel or Netlify deployment
- Database: Managed MySQL service (AWS RDS, Google Cloud SQL)

## Success Metrics
- Resume upload and parsing accuracy > 95%
- API response time < 500ms
- Document processing time < 30 seconds
- Editor response time < 100ms
- System reliability > 99.9% uptime

## Current Implementation State (As of Latest Update)

### What's Working
The application is fully functional with the following features:

1. **Complete Resume Upload & Parsing Flow**:
   - Users can upload PDF/DOCX/TXT files
   - Docling microservice parses documents with markdown support
   - Parsed content stored in MySQL database
   - Resume status tracking: UPLOADED → PARSED → ANALYZED

2. **Rich Text Editor (Lexical)**:
   - Full formatting toolbar (bold, italic, underline, headings, lists, links)
   - Block type selector (H1-H6, Normal text)
   - Markdown shortcuts support
   - Editor state persistence (saves/loads with all formatting)
   - Empty line preservation for proper resume structure

3. **AI-Powered Analysis (Two-Part System)**:

   **Part A: Line-by-Line Analysis**
   - Analyzes each resume line with OpenAI GPT-4
   - Detects section types (CONTACT, EXPERIENCE, EDUCATION, SKILLS, etc.)
   - Groups related lines (e.g., lines 35-44 = Job 1)
   - Stores in `resume_lines` table with AI metadata

   **Part B: Structured Analysis (ATS-Style)**
   - Extracts structured data like an ATS would parse it
   - Stores in dedicated tables: `resume_analysis`, `resume_analysis_experience`, `resume_analysis_skill`, etc.
   - Full contact information extraction
   - Professional summary extraction
   - Work experiences with job titles, companies, dates, descriptions
   - Skills with categories
   - Education, certifications, projects

4. **Frontend Display Components**:

   **AnalysisSummary Component** (Top of Editor):
   - Displays structured ATS-parsed data
   - Contact info cards
   - Professional summary
   - Statistics (count of experiences, skills, education, certs, projects)
   - Collapsible detailed view showing all extracted data
   - Work experiences sorted by date (most recent first)
   - Job numbering (Job 1, Job 2, etc.)
   - Action buttons: "Analyze Job" and "Find Similar Jobs" (placeholders for future)

   **AnalysisOverlay Component** (Middle Section):
   - Shows line-by-line grouped analysis
   - Color-coded section badges
   - Expandable groups with content preview
   - AI insights and analysis notes
   - Job numbering with extracted titles

5. **Date Parsing & Sorting**:
   - ResumeAnalysisService includes smart date parsing
   - Supports multiple formats: "YYYY-MM-DD", "YYYY-MM", "YYYY", "Month YYYY", "MM/YYYY"
   - Experiences automatically sorted by start date (newest first)

### Architecture Highlights

**Three-Layer Data Model**:
1. `resumes` table - Raw uploaded resume metadata
2. `resume_lines` table - Line-by-line content with AI group metadata
3. `resume_analysis_*` tables - Structured ATS-style parsed data (6 related tables)

**Two Analysis Approaches**:
- Line-based: Good for showing how AI interprets the resume structure
- Structured: Good for displaying what an ATS would extract

**Frontend Component Hierarchy**:
```
LexicalEditor (Main container)
├── AnalysisSummary (Top - ATS structured data)
│   └── Work Experience entries with action buttons
├── Toolbar (Save, Analyze buttons)
├── AnalysisOverlay (Middle - Line groups)
│   └── Expandable groups with color coding
└── Lexical Editor Core (Bottom - Rich text editing)
```

### Key Design Decisions

1. **Why Two Analysis Systems?**
   - Line-based: Shows resume structure and grouping (useful for debugging, understanding)
   - Structured: Shows extracted data (useful for ATS preview, job matching)
   - Both work together to give users complete visibility

2. **Why Date Sorting?**
   - Resumes traditionally show most recent experience first
   - ATS systems expect reverse chronological order
   - Smart parsing handles various date formats from AI extraction

3. **Why Action Buttons in ATS Summary?**
   - Centralized location for future job-specific features
   - Users see structured data and can act on specific jobs
   - Removed from line overlay to avoid duplication

4. **Why Lexical Instead of TipTap?**
   - Better React integration
   - More flexible plugin system
   - Official Facebook/Meta support
   - JSON-based state makes persistence easier

### What Needs to Be Built Next

1. **ATS Scoring System**:
   - Calculate compatibility score (0-100)
   - Check for keywords, formatting, section completeness
   - Display score in AnalysisSummary
   - Provide improvement suggestions

2. **Job-Specific Analysis**:
   - Implement "Analyze Job" button functionality
   - Analyze individual work experience quality
   - Suggest improvements for specific job entries
   - Check for quantifiable achievements

3. **Job Search Integration**:
   - Implement "Find Similar Jobs" button functionality
   - Integrate with job search APIs (LinkedIn, Indeed, etc.)
   - Match skills and experience to available positions
   - Display job recommendations

4. **Real-Time Editing Sync**:
   - When user edits in Lexical editor, update analysis
   - Re-analyze changed sections automatically
   - Show "Analysis outdated" warning if edits made

5. **Export Functionality**:
   - Export to PDF with formatting
   - Export to ATS-friendly plain text
   - Export structured JSON for external use

### Important Notes for Future Development

**DO NOT**:
- Change the dual analysis system (line-based + structured) - they serve different purposes
- Remove the date sorting logic - it's essential for proper chronological order
- Modify the core database schema without migration plan
- Break the action button placement in AnalysisSummary

**DO**:
- Use the existing structured data for new features
- Extend the DTOs rather than changing existing ones
- Follow the plugin pattern for new Lexical features
- Keep the ATS summary as the primary action hub

**Performance Considerations**:
- OpenAI API calls are expensive - cache analysis results
- Large resumes may have 100+ lines - pagination may be needed
- Structured analysis should be lazy-loaded
- Editor state JSON can be large - consider compression

**Testing Reminders**:
- Test with various resume formats (traditional, modern, academic)
- Test date parsing with international formats
- Test with resumes in different languages (if expanding scope)
- Test with very long resumes (10+ pages)

### Configuration Files to Check

- `backend/src/main/resources/application.yml` - OpenAI API key, database config
- `backend/.env` - Local environment variables
- `frontend/.env.local` - Frontend API URL
- `backend/src/main/resources/prompts/` - AI prompt templates

### Common Issues & Solutions

**Issue**: Analysis not showing
- **Solution**: Check resume status is "ANALYZED", verify OpenAI API key, check console logs

**Issue**: Dates not sorting correctly
- **Solution**: Check date format in database, verify parseDate() method handles format

**Issue**: Editor state not persisting
- **Solution**: Verify editor_state column in resumes table, check Save button functionality

**Issue**: Docker Docling service not responding
- **Solution**: Run `docker-compose logs -f` in docling-service directory, restart Docker

This documentation should help future Claude instances or developers understand the current state and continue building effectively!