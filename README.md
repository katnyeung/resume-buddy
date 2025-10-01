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
│   │   │   └── RichResumeEditor.tsx      # TipTap editor
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
- **Analysis Features**: Section detection, content grouping, line-by-line insights

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
- **Database Schema**: MySQL with JPA entities and AI analysis fields
- **REST API**: Spring Boot with Swagger documentation
- **Health Monitoring**: Service health checks
- **CORS Configuration**: Frontend integration ready
- **Lexical Editor**: Full rich text editing with formatting preservation
- **Block Type Selector**: Convert text to headings (H1-H6) via dropdown
- **Markdown Parsing**: Automatic conversion from Docling output (##, **, etc.)
- **Editor State Persistence**: Save/load with all formatting intact
- **Empty Line Preservation**: Maintains resume structure and spacing
- **Resume Management UI**: List, view, edit, and delete resumes
- **Stateful Workflow**: Upload → Parse → Edit workflow with status tracking

### 🔄 In Progress
- **AI Resume Analysis**: Line-by-line section detection and content grouping
- **OpenAI Integration**: GPT-4 for intelligent resume insights
- **Analysis API**: Endpoint for AI-powered resume analysis

### 📋 Planned
- **Frontend Analysis UI**: Visual display of AI analysis results
- **Section Highlighting**: Color-coded sections and groups
- **Job Matching**: Skills and position matching
- **ATS Scoring**: Resume optimization for applicant tracking systems

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
- `POST /api/resumes/{id}/parse` - Parse uploaded resume
- `GET /api/resumes/{id}` - Get resume metadata
- `GET /api/resumes/{id}/parsed` - Get structured resume data
- `GET /api/resumes/{id}/lines` - Get resume content as lines
- `PUT /api/resumes/{id}/lines/{lineNumber}` - Update specific line
- `POST /api/resumes/{id}/lines` - Insert new line
- `PUT /api/resumes/{id}/editor-state` - Save Lexical editor state
- `GET /api/resumes/{id}/editor-state` - Load Lexical editor state
- `POST /api/resumes/{id}/analyze` - AI analysis of resume
- `GET /api/resumes/health` - Service health check

## 📚 Implementation Progress

The current implementation follows an MVP (Minimum Viable Product) approach:

1. ✅ **Core Infrastructure**: Backend API, database setup, Docling service
2. ✅ **Document Processing**: Upload, parse with markdown support, and store resumes
3. ✅ **Rich Text Editing**: Lexical editor with full formatting and block type selection
4. ✅ **Frontend Integration**: Next.js with Lexical editor and state persistence
5. 🔄 **AI Analysis**: OpenAI integration for section detection and grouping
6. 📋 **Analysis UI**: Frontend display of AI analysis results
7. 📋 **Job Matching**: Planned for future implementation

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