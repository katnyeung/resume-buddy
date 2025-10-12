# Resume Buddy - AI-Powered Resume Enhancement Platform

## 📌 Current State: Phase 6 - Deep Graph Analysis UI Complete
**Last Updated**: October 12, 2025
**Status**: MVP with full Neo4j graph-based job analysis, skill credibility reporting, and O*NET integration

## Project Overview
AI-powered resume analysis platform with Lexical editor, Neo4j graph database for job/skill relationships, and O*NET occupation mapping.

## Tech Stack
- **Backend**: Spring Boot 3.2.1 + Java 17 + Undertow + MySQL 8.0 + Neo4j 5.x
- **Frontend**: Next.js 14 + TypeScript + Lexical Editor + Tailwind CSS
- **AI/Data**: Grok-4-fast-reasoning (X.AI) + O*NET Web Services API
- **Document Parsing**: Docling microservice (Python FastAPI + Docker)

## Key Architecture

### Database Layers
1. **MySQL**: Raw resumes, line-by-line content, structured ATS analysis
2. **Neo4j**: Job/occupation/skill graph with O*NET taxonomy

### Core Services
- `DoclingHttpService` - Document parsing
- `AIAnalysisService` - LLM-based resume analysis
- `ResumeAnalysisService` - Structured data extraction
- `JobAnalysisService` - Job normalization + O*NET integration
- `ONetIntegrationService` - O*NET API client
- `Neo4jGraphService` - Graph operations + skill mapping

### Frontend Components
- `LexicalEditor` - Rich text editing with formatting
- `AnalysisSummary` - ATS-style structured data display
- `AnalysisOverlay` - Line-by-line grouped analysis
- `JobAnalysisReport` - Deep graph analysis with skill credibility

## Neo4j Graph Structure

**Key Nodes**:
- `JobExperience`, `Occupation` (O*NET SOC), `Skill`, `ONetSkill`, `ONetTechnology`, `ONetActivity`

**Key Relationships**:
- `(JobExperience)-[:MAPS_TO]->(Occupation)` - Multi-occupation mapping (2-3 per job)
- `(JobExperience)-[:REQUIRES_SKILL]->(Skill)` - Technical skills
- `(Occupation)-[:REQUIRES_SKILL]->(ONetSkill)` - Soft skills
- `(Occupation)-[:USES_TECHNOLOGY]->(ONetTechnology)` - Technologies
- `(Skill)-[:DEMONSTRATES]->(ONetSkill)` - LLM-mapped soft skills
- `(Skill)-[:RELATED_TO]->(ONetTechnology)` - LLM-mapped technologies

## Phase 6: Deep Graph Analysis Features ✅

### 1. Skill Evidence Strength Analysis
- **Query**: `(Skill)-[:RELATES_TO_TASK]->(ONetTask)<-[:DEMONSTRATES_TASK]-(DescriptionLine)`
- **Levels**: STRONG (2+ examples), MODERATE (1), WEAK (0 but linked), NONE
- **Output**: Recruiter-focused credibility report

### 2. Description Line Value Scoring
- Ranks lines by skill count (EXCELLENT: 4+, GOOD: 2-3, MODERATE: 1, LOW: 0)
- Helps identify essential vs. low-value lines

### 3. Missing Skill-Task Opportunities
- Finds O*NET tasks candidate COULD demonstrate with existing skills
- Prioritizes by importance (60%+ threshold)

### 4. Missing High-Priority Tasks/Activities
- Identifies critical O*NET gaps regardless of skills
- Top 5 of each, sorted by importance

### 5. Missing Skills Suggestions
- Graph-based suggestions from related occupations
- Filters out "Domain Expertise/Knowledge" categories
- Sorts by importance first, then task frequency
- Shows top 10 by default

## API Endpoints (Key)

**Resume Management**:
- `POST /api/resumes/upload` - Upload file
- `POST /api/resumes/{id}/parse` - Parse with Docling
- `GET /api/resumes/{id}` - Get metadata
- `DELETE /api/resumes/{id}` - Delete (with Neo4j cleanup)

**Analysis**:
- `POST /api/resumes/{id}/analyze` - Line-by-line + structured analysis
- `GET /api/resumes/{id}/structured-analysis` - Get ATS data
- `POST /api/resumes/{resumeId}/experiences/{experienceId}/analyze` - Job analysis with graph
- `GET /api/resumes/{resumeId}/experiences/{experienceId}/analysis` - Get job analysis

**Editor**:
- `PUT /api/resumes/{id}/editor-state` - Save Lexical state
- `GET /api/resumes/{id}/editor-state` - Load state

## Environment Setup

### Backend (application.yml)
```yaml
app:
  openai:
    api-key: ${OPENAI_API_KEY}
    model: grok-4-fast-reasoning
    base-url: https://api.x.ai/v1
  onet:
    username: ${ONET_USERNAME}
    password: ${ONET_PASSWORD}
  neo4j:
    uri: ${NEO4J_URI:bolt://localhost:7687}
    username: ${NEO4J_USERNAME:neo4j}
    password: ${NEO4J_PASSWORD}
  docling:
    service-url: ${DOCLING_SERVICE_URL:http://localhost:8081}
```

### Frontend (.env.local)
```bash
NEXT_PUBLIC_API_URL=http://localhost:8080/api
```

## Quick Start

### Start All Services
```bash
./start-with-docker.sh  # Starts Docling, MySQL, Neo4j
cd backend && mvn spring-boot:run
cd frontend && npm run dev
```

### Stop All
```bash
./stop-with-docker.sh
```

## Key Implementation Files

**Backend**:
- `JobAnalysisService.java` - Main orchestrator
- `Neo4jGraphService.java` - Graph queries + skill mapping
- `ONetIntegrationService.java` - O*NET API client
- `prompts/` - LLM prompt templates

**Frontend**:
- `JobAnalysisReport.tsx` - Deep analysis UI
- `LexicalEditor.tsx` - Main editor
- `AnalysisSummary.tsx` - ATS display

## Key Design Decisions

1. **Dual Analysis System**: Line-based (structure) + Structured (ATS extraction)
2. **Multi-Occupation Mapping**: 2-3 O*NET occupations per job for comprehensive coverage
3. **Real-Time Graph Queries**: Deep analysis computed on-demand (<500ms)
4. **Importance-First Sorting**: Skills/tasks sorted by O*NET importance, not just frequency

## Recent Enhancements (Oct 12, 2025)

**Skill Credibility Report**:
- Dynamic trust badges based on connection count
- Recruiter-friendly terminology ("Demonstrated in Resume" vs "Concrete Examples")
- Task importance display for each skill
- Priority order: UNSUPPORTED → WEAK → MODERATE → STRONG

**Missing Skills**:
- Category filtering (excludes "Domain Expertise/Knowledge")
- Importance-first sorting with task frequency tiebreaker
- Top 10 default view with "Show All" expansion

## MVP Scope - Important Constraints

- This is an MVP - avoid out-of-scope features
- No setup guides needed - update README/CLAUDE.md instead
- Focus on implementation, summaries over detailed explanations
- User handles verification - prioritize making changes

## Common Issues

**Analysis not showing**: Check resume status = "ANALYZED", verify API keys, check logs
**Docling service down**: `cd docling-service && docker-compose logs -f`
**Neo4j connection**: Verify bolt://localhost:7687 and credentials

## Future Enhancements 📋
1. AI-generated suggestions for adding missing tasks
2. "Add to resume" button pre-filling editor
3. Track user actions on suggestions
4. Candidate comparison queries
5. Career path visualization
6. Export to PDF/ATS-friendly formats
