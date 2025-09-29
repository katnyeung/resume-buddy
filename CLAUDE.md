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
- **Editor**: TipTap rich text editor for real-time resume editing
- **UI Library**: Tailwind CSS with modern components
- **State Management**: React hooks and context API
- **HTTP Client**: Axios for API communication
- **File Upload**: react-dropzone for drag & drop functionality

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
│   │   │   └── RichResumeEditor.tsx       # TipTap editor
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
    status VARCHAR(20),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Line-by-line resume content
CREATE TABLE resume_lines (
    id VARCHAR(36) PRIMARY KEY,
    resume_id VARCHAR(36) NOT NULL,
    line_number INT NOT NULL,
    content TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (resume_id) REFERENCES resumes(id) ON DELETE CASCADE
);

-- AI suggestions
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
2. ✅ Integrate TipTap editor
3. ✅ Implement resume management UI
4. ✅ Add line-based editing UI
5. 🔄 Implement auto-save functionality (In Progress)
6. 🔄 Add UI refinements and polish (In Progress)

### Phase 4: AI Integration
1. 📋 Integrate OpenAI API for text analysis (Planned)
2. 📋 Implement suggestion generation system (Planned)
3. 📋 Create analysis endpoints and services (Planned)
4. 📋 Add ATS scoring functionality (Planned)

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