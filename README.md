# Resume Buddy - AI-Powered Resume Enhancement Platform

## 🚀 Overview

Resume Buddy is an MVP application that enables users to upload resumes and leverage AI for comprehensive job search assistance, scoring, and description analysis. The platform provides real-time editing with TipTap integration, AI-powered suggestions, and job matching capabilities.

## 🏗️ Architecture

```
Frontend (Next.js + TipTap) ←→ Backend (Spring Boot + Undertow) ←→ Docling Service (Docker)
                                         ↓
                                    MySQL Database
```

## 📁 Project Structure

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
│   │   │   └── ResumeLineService.java     # Line processing for editing
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
│   ├── app.py                            # FastAPI application
│   ├── requirements.txt
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── docker-start.sh
├── frontend/                             # Next.js Application
│   ├── src/
│   │   ├── app/
│   │   │   ├── page.tsx                  # Resume list page
│   │   │   ├── edit/[id]/                # Resume editing
│   │   │   └── upload/                   # Resume upload
│   │   ├── components/
│   │   │   ├── ResumeItem.tsx            # Resume card component
│   │   │   ├── LexicalEditor.tsx         # Lexical editor with analysis
│   │   │   ├── AnalysisSummary.tsx       # ATS structured analysis display
│   │   │   ├── AnalysisOverlay.tsx       # Line-based grouped analysis
│   │   │   └── plugins/                  # Lexical editor plugins
│   │   │       ├── ToolbarPlugin.tsx
│   │   │       ├── OnChangePlugin.tsx
│   │   │       └── AutoFocusPlugin.tsx
│   │   ├── lib/
│   │   │   ├── api.ts                    # API integration
│   │   │   └── utils.ts                  # Utility functions
│   │   └── types/
│   │       └── resume.ts                 # TypeScript definitions
│   ├── package.json
│   └── tailwind.config.js
├── start-with-docker.sh                  # Docker-based startup script
├── stop-with-docker.sh                   # Docker-based shutdown script
├── start-all.sh                          # Complete startup script
├── stop-all.sh                           # Complete shutdown script
└── CLAUDE.md                            # Complete project documentation
```

## 🛠️ Tech Stack

### Backend
- **Framework**: Spring Boot 3.2.1 with Java 17+ (Undertow Server)
- **Database**: MySQL 8.0 with Spring Data JPA
- **Document Parsing**: Docling HTTP microservice (Python FastAPI + Docker)
- **API Documentation**: Swagger/OpenAPI 3 with springdoc
- **Dependencies**: Spring Web, Spring Data JPA, Spring Validation

### Docling Microservice
- **Framework**: Python FastAPI
- **Document Processing**: Docling library for advanced PDF/DOCX parsing
- **Containerization**: Docker with docker-compose
- **Features**: Table detection, layout analysis, structure recognition

### Frontend
- **Framework**: Next.js 14+ with TypeScript
- **Editor**: Lexical rich text editor with full formatting and markdown support
- **UI Library**: Tailwind CSS with modern components
- **State Management**: React hooks and context API
- **File Upload**: react-dropzone for drag & drop uploads

### AI Services
- **OpenAI Integration**: GPT-4 for intelligent resume analysis
- **Line-by-Line Analysis**: Section detection, content grouping, AI insights
- **Structured Extraction**: Contact info, experiences, skills, education, certifications, projects
- **ATS Parsing**: Parse resume like an Applicant Tracking System would

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Maven 3.6+
- MySQL 8.0+
- Docker & Docker Compose
- Node.js 16+ and npm/yarn
- Python 3.11+ (for Docling service)

### 1. Start Docling Service
```bash
cd docling-service
./docker-start.sh
```

### 2. Start Backend
```bash
cd backend
mvn spring-boot:run
```

### 3. Start Frontend
```bash
cd frontend
npm install
npm run dev
```

### 4. Access Application
- **Frontend**: http://localhost:3000
- **Backend API**: http://localhost:8080/api
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Docling Service**: http://localhost:8081/health

## 🔧 Development

### Backend Commands
```bash
# Clean build and run
mvn clean compile
mvn spring-boot:run

# Run tests
mvn test
```

### Frontend Commands
```bash
# Install dependencies
npm install

# Run development server
npm run dev

# Build for production
npm run build

# Type check
npm run type-check
```

### Docling Service Commands
```bash
# Start with Docker
cd docling-service && docker-compose up -d

# View logs
docker-compose logs -f

# Stop service
docker-compose down
```

### Complete System
```bash
# Start everything
./start-with-docker.sh

# Stop everything
./stop-with-docker.sh
```

## 📊 Current Features

### ✅ Completed
- **Document Upload**: Support for PDF, DOCX, TXT files
- **Advanced Parsing**: Docling integration with Docker and markdown support
- **Database Schema**: MySQL with JPA entities, AI analysis fields, and structured analysis tables
- **REST API**: Spring Boot with Swagger documentation
- **Health Monitoring**: Service health checks
- **CORS Configuration**: Frontend integration ready
- **Lexical Editor**: Full rich text editing with formatting preservation
- **Block Type Selector**: Convert text to headings (H1-H6) via dropdown
- **Markdown Parsing**: Automatic conversion from Docling output (##, **, etc.)
- **Editor State Persistence**: Save/load with all formatting intact
- **Empty Line Preservation**: Maintains resume structure and spacing
- **Resume Management UI**: List, view, edit, and delete resumes
- **Stateful Workflow**: Upload → Parse → Analyze → Edit workflow with status tracking
- **AI Resume Analysis**:
  - Line-by-line section detection and content grouping with OpenAI GPT-4
  - Structured analysis extraction (contact info, experiences, skills, education, certifications, projects)
  - Analysis persistence in dedicated database tables
- **ATS Analysis Display**:
  - AnalysisSummary component showing structured parsed data at top of editor
  - Contact information, professional summary, and statistics cards
  - Collapsible detailed view with full work experience, skills, education, certifications, and projects
  - Date-based sorting (most recent first) for work experiences
  - Job numbering with action buttons for future enhancements
- **Line Analysis Display**:
  - AnalysisOverlay component showing grouped line analysis
  - Color-coded sections (CONTACT, EXPERIENCE, EDUCATION, SKILLS, etc.)
  - Expandable groups with content preview and AI insights
  - Job numbering with extracted titles from content

### 📋 Planned
- **ATS Scoring**: Resume optimization scoring for applicant tracking systems
- **Job-Specific Analysis**: Analyze individual job experiences for improvements
- **Job Matching**: Skills and position matching with job postings
- **Job Search Integration**: Find similar jobs based on experience

## 🐳 Docker Integration

The Docling service runs in Docker for:
- **Consistent Environment**: Same runtime across machines
- **Easy Deployment**: Single command startup
- **Resource Isolation**: Contained Python environment
- **Health Monitoring**: Built-in health checks

## 📖 API Documentation

Access the interactive API documentation at:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

### Key Endpoints
- `POST /api/resumes/upload` - Upload resume file
- `POST /api/resumes/{id}/parse` - Parse uploaded resume with Docling
- `GET /api/resumes/{id}` - Get resume metadata
- `GET /api/resumes/{id}/parsed` - Get raw parsed resume data
- `GET /api/resumes/{id}/lines` - Get resume content as lines
- `PUT /api/resumes/{id}/lines/{lineNumber}` - Update specific line
- `POST /api/resumes/{id}/lines` - Insert new line
- `PUT /api/resumes/{id}/editor-state` - Save Lexical editor state with formatting
- `GET /api/resumes/{id}/editor-state` - Load Lexical editor state
- `POST /api/resumes/{id}/analyze` - Run AI analysis (line-by-line + structured extraction)
- `GET /api/resumes/{id}/structured-analysis` - Get ATS-style structured analysis
- `GET /api/resumes/{id}/analysis-exists` - Check if analysis exists
- `GET /api/resumes/health` - Service health check

## 📚 Implementation Progress

The current implementation follows an MVP (Minimum Viable Product) approach:

1. ✅ **Core Infrastructure**: Backend API, database setup, Docling service
2. ✅ **Document Processing**: Upload, parse with markdown support, and store resumes
3. ✅ **Rich Text Editing**: Lexical editor with full formatting and block type selection
4. ✅ **Frontend Integration**: Next.js with Lexical editor and state persistence
5. ✅ **AI Analysis**: OpenAI integration for line-by-line and structured analysis
6. ✅ **Analysis UI**:
   - ATS Summary component (top) - structured data display
   - Analysis Overlay component (middle) - grouped line analysis
   - Date-based sorting for chronological resume order
7. 📋 **ATS Scoring**: Calculate and display ATS compatibility score
8. 📋 **Job Matching**: Search and match relevant job postings

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test with both services running
5. Submit a pull request

## 🎯 Project Goals

- **High Accuracy**: >95% resume parsing accuracy
- **Fast Processing**: <30 seconds for document parsing
- **User Experience**: Intuitive editing interface
- **AI-Powered**: Smart suggestions and improvements
- **Scalable**: Microservice architecture for growth

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.