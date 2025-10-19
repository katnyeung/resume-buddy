# Resume Buddy - AI-Powered Resume Enhancement Platform

## 📌 Current State: Phase 11.3 - Skill Train (Interactive Career Path Explorer) ✅
**Last Updated**: October 19, 2025
**Status**: Production-ready MVP with job search, vector matching, graph-based skill discovery, token credits, and skill train

## Project Overview
AI resume analysis platform with Lexical editor, Neo4j graph for job/skill relationships, O*NET occupation mapping, vector-based job matching, and interactive skill exploration.

## Tech Stack
- **Backend**:
  - **Resume API** (port 8080): Spring Boot 3.2.1 + Java 17 + MySQL 8.0 + Neo4j 5.x
  - **Job Search Service** (port 8085): Spring Boot 3.2.1 + Java 17 + MySQL 8.0 + Redis Stack + Swagger/OpenAPI
- **Frontend**: Next.js 14 + TypeScript + Lexical Editor + Tailwind CSS
- **AI/Data**: Grok-4-fast-reasoning (X.AI) + OpenAI Embeddings (text-embedding-3-small) + O*NET Web Services API
- **Document Parsing**: Docling microservice (Python FastAPI + Docker)
- **Vector Search**: Redis Stack with RediSearch + HNSW indexing

## Key Architecture

### Service Architecture (Microservices)

```
┌──────────────────────────────────────────────────────┐
│  Frontend (Next.js) - Port 3000                       │
│  - Resume Editor | Job Search UI                     │
└────────────┬─────────────────────────────────────────┘
             │
      ┌──────┴──────┐
      │             │
┌─────▼─────┐  ┌───▼───────────────────────────────┐
│ Resume API│  │ Job Search Service (NEW)          │
│ :8080     │  │ :8085 (Swagger UI available)      │
│           │  │                                   │
│ MySQL     │  │ - Multi-experience profiles      │
│ Neo4j     │  │ - Redis vector search            │
└───────────┘  │ - Skill gap analysis             │
               │ - HTTP → Resume API              │
               │                                   │
               │ MySQL (jobsearch DB)             │
               │ Redis Stack (vectors)            │
               └───────────────────────────────────┘
```

### Database Layers
1. **MySQL (resumebuddy)**: Raw resumes, line-by-line content, structured ATS analysis, **user credits, job queue**
2. **Neo4j**: Job/occupation/skill graph with O*NET taxonomy
3. **MySQL (jobsearch)**: Job search profiles, job listings, matches, **crawl activity logs**
4. **Redis**: Vector embeddings (1536-dim) with HNSW indexing

### Core Services (Resume API - :8080)
- `DoclingHttpService` - Document parsing
- `AIAnalysisService` - LLM-based resume analysis
- `ResumeAnalysisService` - Structured data extraction
- `JobAnalysisService` - Job normalization + O*NET integration
- `ONetIntegrationService` - O*NET API client
- `Neo4jGraphService` - Graph operations + skill mapping
- **`UserCreditService`** - Token credit management with ACID transactions (NEW)
- **`JobQueueService`** - Async job queue with optimistic locking (NEW)
- **`JobQueueWorker`** - Scheduled worker (@Scheduled every 2s) (NEW)
- **`JobExecutor`** - Job execution wrapper for existing services (NEW)

### Core Services (Job Search - :8081)
**Domain**:
- `JobPostGenerator` - LLM-powered job post creation from experiences
- `SkillMatcher` - Skill gap analysis (cosine similarity + set difference)

**Application**:
- `JobSearchApplicationService` - Profile orchestration
- `JobMatchingApplicationService` - Vector search + ranking

**Infrastructure**:
- `RedisVectorService` - Vector storage + RediSearch queries
- `VectorEmbeddingService` - OpenAI embeddings client
- `ResumeApiClient` - HTTP client to fetch experience data
- `GrokLLMClient` - Job post generation

### Frontend Components
- `LexicalEditor` - Rich text editing with formatting
- `AnalysisSummary` - ATS-style structured data display + **Job Search Profile Editor** (editable textarea at top of page)
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

## Core Features

### Resume Analysis (Phase 6)
- **Skill credibility scoring**: STRONG (2+ examples), MODERATE (1), WEAK/NONE (graph-linked)
- **Line value ranking**: By skill count (EXCELLENT: 4+, GOOD: 2-3, MODERATE: 1, LOW: 0)
- **Missing skill/task recommendations**: From O*NET graph, importance-sorted
- Multi-occupation mapping (2-3 O*NET SOCs per job)

### Job Search & Matching (Phases 7-11)
- **Profile creation**: Select 1-N experiences → LLM generates mock job post (8-12 bullets)
- **Editable profiles**: Inline textarea at top of page, save triggers re-vectorization
- **Dual vector system**: Profile-level (full post) + line-level (each bullet) vectors
- **Hybrid matching**: 60% vector similarity + 40% proficiency-weighted skills
- **Match classification**: STRONG (≥0.85), GOOD (≥0.70), MODERATE (<0.70)
- **Smart caching**: Cache-first page loads, Force Refresh option, preserves user flags
- **Date filtering**: 7d/2wk/1mo/2mo/3mo/All (default: 1 month)
- **topK selector**: 50/100/200/300/500/ALL jobs analyzed

### Neo4j Skill Discovery (Phase 11.2)
- **Interactive tag cloud**: Top 30 in-demand skills with job counts
- **Drilldown modal**: AND logic filtering (Java → Java+Docker → Java+Docker+AWS)
- **Related skills**: Shows co-occurring skills in matching jobs
- **Graph-discovered jobs**: Separate table bypassing vector search
- **Performance**: <100ms Neo4j queries, <50ms MySQL batch fetch

### Skill Train (Phase 11.3)
- **Interactive career path explorer**: Build skill combinations to explore job opportunities
- **Visual train track**: Horizontal stations showing skills (owned vs gaps) with job counts
- **Path building**: Click skills to extend path, see related skills at each level (AND logic)
- **Backtrack/Reset**: Navigate back or restart exploration
- **Zero cost**: Reuses existing Neo4j queries, no LLM calls
- **Placement**: Displayed after Skills section in ATS Analysis Summary page

## API Endpoints (Key)

**Resume Management** (:8080):
- `POST /api/resumes/upload` - Upload file
- `POST /api/resumes/{id}/parse` - Parse with Docling
- `GET /api/resumes/{id}` - Get metadata
- `DELETE /api/resumes/{id}` - Delete (with Neo4j cleanup)

**Analysis** (:8080):
- `POST /api/resumes/{id}/analyze` - Line-by-line + structured analysis
- `GET /api/resumes/{id}/structured-analysis` - Get ATS data
- `POST /api/resumes/{resumeId}/experiences/{experienceId}/analyze` - Job analysis with graph
- `GET /api/resumes/{resumeId}/experiences/{experienceId}/analysis` - Get job analysis

**Editor** (:8080):
- `PUT /api/resumes/{id}/editor-state` - Save Lexical state
- `GET /api/resumes/{id}/editor-state` - Load state

**Async Job Queue** (:8080 - NEW Phase 10):
- `POST /api/jobs/resumes/{id}/analyze/async` - Queue resume analysis (async)
- `POST /api/jobs/resumes/{resumeId}/experiences/{expId}/analyze/async` - Queue job experience analysis (async)
- `GET /api/jobs/{jobId}/status` - Poll job status (returns QUEUED/PROCESSING/COMPLETED/FAILED)

**User Credits** (:8080 - NEW Phase 10):
- `GET /api/users/{userId}/credits` - Get credit balance
- `GET /api/users/{userId}/credits/transactions` - Get transaction history
- `POST /api/users/admin/credits/grant` - Admin: Grant credits to user

**Job Search** (:8085 - NEW):
- `POST /api/job-search/profiles` - Create profile from experiences
- `PUT /api/job-search/profiles/{id}` - Update job post
- `GET /api/job-search/profiles/{id}` - Get profile
- `GET /api/job-search/profiles/{id}/lines` - Get profile lines (single source of truth)
- `GET /api/job-search/profiles?resumeId={resumeId}` - List profiles
- `POST /api/job-search/profiles/{id}/search?topK=20` - Search jobs (creates/updates matches)
- `GET /api/job-search/profiles/{id}/matches` - Get cached matches
- `GET /api/job-search/profiles/{id}/matching-results?topK=20&refresh=false` - Line-by-line matching results (vector + skill analysis), add `refresh=true` to bypass cache
- `POST /api/job-search/admin/rebuild-index` - Rebuild Redis index with line-level prefixes (DESTRUCTIVE)
- `POST /api/job-search/admin/revectorize/listing-lines?batchSize=50` - Parse job descriptions → lines → batch vectorize
- `POST /api/job-search/admin/revectorize/profile-lines?batchSize=50` - Re-vectorize existing profile lines

**Skill Train** (:8085 - NEW Phase 11.3):
- `GET /api/job-search/resumes/{resumeId}/skill-train` - Get skill train data (user skills + market skills + job counts)
- `POST /api/job-search/skill-train/path` - Explore skill path (AND logic, returns job count + related skills)

**Swagger & Docs**:
- **Swagger UI**: http://localhost:8085/swagger-ui.html
- **OpenAPI Docs**: http://localhost:8085/api-docs

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
  job-crawling:
    adzuna:
      app-id: ${ADZUNA_APP_ID}
      app-key: ${ADZUNA_APP_KEY}
    reed:
      api-key: ${REED_API_KEY}
    jsearch:
      api-key: ${JSEARCH_API_KEY}
```

### Frontend (.env.local)
```bash
NEXT_PUBLIC_API_URL=http://localhost:8080/api
```

## Quick Start

### Start All Services
```bash
# 1. Start infrastructure (MySQL, Neo4j, Docling)
./start-with-docker.sh

# 2. Start Redis for job search
cd job-search-service && docker-compose up -d && cd ..

# 3. Start Resume API (:8080)
cd backend && mvn spring-boot:run &

# 4. Start Job Search Service (:8085)
cd job-search-service && mvn spring-boot:run &

# 5. Start Frontend (:3000)
cd frontend && npm run dev
```

### Stop All
```bash
./stop-with-docker.sh
cd job-search-service && docker-compose down
```

## Usage Examples

### Manual Job Crawl (Reed.co.uk)
```bash
POST http://localhost:8085/api/job-search/admin/crawl
Content-Type: application/json

{
  "source": "REED",
  "keywords": "java developer",
  "location": "London",
  "maxResults": 50,
  "page": 1,
  "fullTimeOnly": false,
  "permanentOnly": true,
  "maxDaysOld": 7
}
```

### Manual Job Crawl (Adzuna)
```bash
POST http://localhost:8085/api/job-search/admin/crawl
Content-Type: application/json

{
  "source": "ADZUNA",
  "keywords": "software engineer",
  "location": "gb:London",
  "maxResults": 50,
  "page": 1,
  "fullTimeOnly": false,
  "permanentOnly": true,
  "maxDaysOld": 7
}
```

### Manual Job Crawl (JSearch - RapidAPI)
```bash
POST http://localhost:8085/api/job-search/admin/crawl
Content-Type: application/json

{
  "source": "JSEARCH",
  "keywords": "java developer",
  "location": "gb:London",
  "maxResults": 50,
  "page": 1,
  "maxDaysOld": 7
}
```

## Key Implementation Files

**Resume API (backend/)**:
- `JobAnalysisService.java` - Main orchestrator
- `Neo4jGraphService.java` - Graph queries + skill mapping
- `ONetIntegrationService.java` - O*NET API client
- `prompts/` - LLM prompt templates

**Job Search Service (job-search-service/)**:
- `domain/model/` - JobSearchProfile, JobListing, JobMatch entities
- `domain/service/` - JobPostGenerator, SkillMatcher, JobCrawlerService, JobDescriptionParser
- `application/service/` - JobSearchApplicationService, JobMatchingApplicationService, JobCrawlingApplicationService
- `service/` - JobAnalysisService (keyword matching), KeywordSkillMatcher (regex-based), Neo4jJobListingService (direct Neo4j indexing)
- `infrastructure/redis/` - RedisVectorService
- `infrastructure/external/` - GrokLLMClient, VectorEmbeddingService, AdzunaApiClient, ReedApiClient
- `infrastructure/external/jobsources/` - JobSourceApiClient (interface), AdzunaApiClient, ReedApiClient, JSearchApiClient
- `config/` - Neo4jConfig (Neo4j Driver bean)
- `dto/adzuna/` - AdzunaJobDto, AdzunaSearchResponse (common format)
- `dto/reed/` - ReedJobDto, ReedSearchResponse
- `dto/jsearch/` - JSearchJobDto, JSearchSearchResponse
- `dto/analysis/` - JobAnalysisRequest, JobAnalysisResponse (skill extraction DTOs)
- `api/controller/` - JobSearchController, AdminController

**Frontend**:
- `JobAnalysisReport.tsx` - Deep analysis UI
- `LexicalEditor.tsx` - Main editor
- `AnalysisSummary.tsx` - ATS display

## Key Design Decisions

1. **Dual Analysis System**: Line-based (structure) + Structured (ATS extraction)
2. **Multi-Occupation Mapping**: 2-3 O*NET occupations per job for comprehensive coverage
3. **Real-Time Graph Queries**: Deep analysis computed on-demand (<500ms)
4. **Importance-First Sorting**: Skills/tasks sorted by O*NET importance, not just frequency
5. **Microservice Separation (DDD)**: Job search bounded context separated from resume analysis
6. **Hybrid Storage**: MySQL (persistence) + Redis (vectors) for optimal performance
7. **Multi-Experience Profiles**: Aggregate 1-N experiences for comprehensive job search
8. **Vector-First Matching**: Semantic similarity before rule-based filtering

## Implementation History (Condensed)

**Phases 6-7 (Oct 12-13)** - Resume Analysis & Job Search:
- Skill credibility report with trust badges and O*NET task mapping
- DDD microservice (job-search-service) with dual vector system
- Batch OpenAI embeddings (up to 2048 texts/request)
- Redis HNSW indexing, <5ms vector searches

**Phase 8 (Oct 14-18)** - Job Crawling & Skill Extraction:
- Multi-source APIs: Reed (2-stage fetch for full descriptions), Adzuna, JSearch
- LLM keyword generation (10 base → 30 with senior/lead variants)
- HTML stripping parser (preserves structure, decodes entities)
- Keyword-based skill extraction (100+ jobs/sec, $0 cost vs LLM)
- Neo4j indexing (`JobListing` → `REQUIRES_SKILL` → `Skill`)
- Batch vectorization (50 jobs in <2s), crawl activity logging

**Phase 9 (Oct 14)** - Line-by-Line Matching:
- Removed full-doc vectors, pure line-by-line Redis search
- Newline-first parser (respects job board formatting, 100-150 char chunks)
- Proficiency-weighted skills: `sum(matched_proficiencies) / sum(all_proficiencies)`
- 60/40 hybrid ranking (vector + weighted skills)
- Redis key fix: `listing:line:{db_id}` instead of random UUIDs

**Phase 10 (Oct 18)** - Token Credits & Async Queue:
- MySQL-based queue (QUEUED → PROCESSING → COMPLETED/FAILED)
- ACID credit transactions (deduct on start, refund on fail)
- Costs: Upload=50, Analysis=100, Job exp=50, Profile=25 credits
- @Scheduled worker (2s polling, max 3 concurrent, retry 3x)
- Rationale: $0/mo vs $40-100/mo for SQS/Kafka at <100 users

**Phase 11 (Oct 18)** - UX Enhancements:
- topK selector (50/100/200/300/500/ALL), date filter (7d-3mo)
- Smart caching: Auto-load cached matches, Force Refresh option
- Preserves user flags on refresh (stars/saved/applied/redflag)
- Direct star selection UX (click to set, click again to remove)
- Reduced logging verbosity (DEBUG for per-job details)

## MVP Scope - Important Constraints

- This is an MVP - avoid out-of-scope features
- No setup guides needed - update README/CLAUDE.md instead
- Focus on implementation, summaries over detailed explanations
- User handles verification - prioritize making changes

## Common Issues

**Analysis not showing**: Check resume status = "ANALYZED", verify API keys, check logs
**Docling service down**: `cd docling-service && docker-compose logs -f`
**Neo4j connection**: Verify bolt://localhost:7687 and credentials
**Job matching returns 0 results**: Redis index has wrong prefixes. Fix:
1. `POST http://localhost:8085/api/job-search/admin/rebuild-index` (drops old index)
2. `POST http://localhost:8085/api/job-search/admin/revectorize/listing-lines` (parse jobs → lines)
3. `POST http://localhost:8085/api/job-search/admin/revectorize/profile-lines` (re-vectorize profiles)
4. Restart service to drop old MySQL columns (`redis_vector_key`, `required_skills`)

**Matches not updating after re-vectorizing**: Use `?refresh=true` query parameter:
- `GET /api/job-search/profiles/{id}/matching-results?refresh=true&topK=20`
- Forces fresh vector search instead of returning cached results
- Useful after running `/admin/revectorize/listing-lines` with improved parser

**Phase 11.1 (Oct 18 Evening)** - Reed Full Descriptions & HTML Cleanup:
- Two-stage Reed fetching: `/search` (list) → `/jobs/{id}` (full description)
- HTML stripping parser (preserves structure, decodes entities, normalizes whitespace)
- Result: 10-20+ skills/job (vs 1-2 with truncated descriptions)
- 500ms rate limiting, Reed now default scheduler source
- Frontend retry logic for 500 errors, fixed refresh button stuck bug

**Phase 11.2 (Oct 18 Night)** - Neo4j Skill Discovery ✅:
- **Interactive tag cloud**: Top 30 in-demand skills with job counts (clickable pills)
- **Drilldown modal**: AND logic filtering, related skill suggestions, date distribution chart
- **Graph-discovered jobs**: Separate table bypassing vector search, shows skill-based matches
- **3 new endpoints**: `/skills/top`, `/skills/drilldown`, `/jobs/by-ids`
- **Neo4j queries**: `getJobsBySkills()` (SIZE check for AND), `getRelatedSkills()` (co-occurrence)
- **Use cases**: Skill discovery, market insights, career planning, deterministic search
- **Components**: `SkillFilterCloud.tsx`, `SkillDrilldownModal.tsx`, `DateDistributionChart.tsx`

**Phase 11.3 (Oct 19)** - Skill Train (Interactive Career Path Explorer) ✅:
- **Horizontal train track UI**: Left-to-right skill path visualization in ATS Analysis Summary
- **Starting stations**: User's existing skills from resume (solid green borders with ✓)
- **Gap skills**: Market skills user doesn't have (dashed gray borders)
- **Interactive exploration**: Click skill → see related skills → build path (AND logic)
- **Job count badges**: Each station shows number of jobs requiring that skill
- **Path tracking**: Current path displayed with arrow connectors (→) and total job count
- **Backtrack/Reset**: Navigate back one step or reset to start
- **2 new endpoints**: `GET /resumes/{id}/skill-train`, `POST /skill-train/path`
- **Reuses Neo4j queries**: `getJobsBySkills()`, `getRelatedSkills()`, `getTopInDemandSkills()`
- **Use cases**: Career planning, skill gap identification, learning roadmap, market demand visibility
- **Components**: `SkillTrain.tsx` (placed after Skills section in AnalysisSummary)
- **Performance**: <100ms Neo4j queries, no LLM calls, $0 cost

## Future Enhancements 📋
1. AI-generated suggestions for adding missing tasks
2. "Add to resume" button pre-filling editor
3. Track user actions on suggestions
4. Candidate comparison queries
5. Career path visualization
6. Export to PDF/ATS-friendly formats
