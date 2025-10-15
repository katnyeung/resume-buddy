# Resume Buddy - AI-Powered Resume Enhancement Platform

## 📌 Current State: Phase 9 - Job Matching Feature
**Last Updated**: October 14, 2025
**Status**: MVP + Job search microservice with automated job crawling and intelligent job matching (vector similarity + skill gap analysis)

## Project Overview
AI-powered resume analysis platform with Lexical editor, Neo4j graph database for job/skill relationships, O*NET occupation mapping, and **NEW: Job search/matching service using vector similarity**.

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
1. **MySQL (resumebuddy)**: Raw resumes, line-by-line content, structured ATS analysis
2. **Neo4j**: Job/occupation/skill graph with O*NET taxonomy
3. **MySQL (jobsearch)**: Job search profiles, job listings, matches
4. **Redis**: Vector embeddings (1536-dim) with HNSW indexing

### Core Services (Resume API - :8080)
- `DoclingHttpService` - Document parsing
- `AIAnalysisService` - LLM-based resume analysis
- `ResumeAnalysisService` - Structured data extraction
- `JobAnalysisService` - Job normalization + O*NET integration
- `ONetIntegrationService` - O*NET API client
- `Neo4jGraphService` - Graph operations + skill mapping

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

## Phase 7: Job Search Service Features ✅

### 1. Multi-Experience Profile Generation
- User selects 1-N resume experiences in editor
- LLM generates 8-12 bulleted job requirements (generic, concise)
- **NEW UX**: Profile appears in **editable textarea at top of edit page**
- User can edit and save to regenerate vectors
- No popup modal - all editing happens inline

### 2. Profile Management UX Flow
1. User selects experiences via checkboxes in ATS Summary
2. Clicks "Generate Mock Job Post" button
3. Profile loads automatically at top of page in green-bordered section
4. User edits in textarea, clicks "Save & Regenerate Vectors"
5. Backend regenerates embedding and updates Redis
6. Ready for job search/matching

### 3. Dual Vector System (CORRECTED Oct 13)
- **Profile-level vector**: Full mock job post for quick filtering
- **Line-level vectors**: Individual **mock job post lines** (NOT user resume) - each bullet point from Grok-generated requirements
- Master-detail: `job_search_profile` (1) → `job_search_profile_line` (many)
- Redis keys: `profile:vector:{profileId}` (full) + `profile:line:{lineId}` (each line)
- Lines stored in MySQL + vectors in Redis
- Enables granular line-by-line matching with actual job listings

### 4. Vector Search with Redis HNSW
- OpenAI text-embedding-3-small (1536 dimensions)
- Cosine similarity search (<5ms typical)
- Top-K results (default: 20)

### 5. Skill Gap Analysis
- Matched skills vs missing skills
- Match percentage calculation
- Categorized by technical/soft skills

### 6. DDD Architecture
- Clean separation: Domain → Application → Infrastructure → API
- No business logic in controllers
- Domain services for core operations

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
- `infrastructure/redis/` - RedisVectorService
- `infrastructure/external/` - ResumeApiClient, GrokLLMClient, VectorEmbeddingService, AdzunaApiClient, ReedApiClient
- `infrastructure/external/jobsources/` - JobSourceApiClient (interface), AdzunaApiClient, ReedApiClient
- `dto/adzuna/` - AdzunaJobDto, AdzunaSearchResponse (common format)
- `dto/reed/` - ReedJobDto, ReedSearchResponse
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

## Recent Enhancements

**Phase 6 (Oct 12, 2025)** - Skill Credibility Report:
- Dynamic trust badges based on connection count
- Recruiter-friendly terminology ("Demonstrated in Resume" vs "Concrete Examples")
- Task importance display for each skill
- Priority order: UNSUPPORTED → WEAK → MODERATE → STRONG
- Category filtering (excludes "Domain Expertise/Knowledge")
- Importance-first sorting with task frequency tiebreaker

**Phase 7 (Oct 13, 2025)** - Job Search Service:
- New microservice with DDD architecture (domain/application/infrastructure/api)
- Multi-experience profile generation with LLM-generated bulleted requirements
- **UX Enhancement**: Editable textarea at top of edit page (no modal)
- **Dual vector system** (FIXED):
  - Profile-level: Full mock job post vector
  - Line-level: Each bullet point from mock job post (stored in `job_search_profile_line`)
  - NOT user resume lines - only LLM-generated mock requirements
- **Single source of truth**: Frontend fetches lines from `job_search_profile_line` table via `/profiles/{id}/lines`
- **Batch embedding optimization**: OpenAI batch API (up to 2048 texts per request) instead of line-by-line
- Redis vector search with HNSW indexing (1536-dim OpenAI embeddings)
- Line-by-line vectorization of mock job post for granular matching
- Edit & save triggers: delete old lines, re-split, re-vectorize with batch API
- Skill gap analysis (matched vs missing)
- REST API for profile management and job matching
- <5ms vector search queries, top-K results
- Swagger/OpenAPI documentation at http://localhost:8085/swagger-ui.html
- **Cleanup**: Deleted obsolete `ProfileLineVector` entity (replaced with `JobSearchProfileLine`)

**Phase 8 (Oct 14, 2025)** - Automated Job Crawling (Updated Oct 15):
- **Multi-source job crawling architecture**:
  - Adzuna API integration (gb, us, au, ca, de, fr, nl, in, nz, za, sg, ie supported)
  - **Reed.co.uk API integration** (Oct 15 - IMPLEMENTED):
    - UK-focused job board (largest in UK)
    - Basic Auth with API key
    - Converts Reed DTOs to common Adzuna format
    - Supports keywords, location, salary filters
    - Distance-based search (10 miles default)
    - Full-time/part-time/permanent filters
  - RapidAPI support (future)
- **LLM-powered keyword generation**:
  - Grok analyzes database state (job count, profile locations)
  - Generates 10 BASE role keywords → auto-expands to 30 (base, senior, lead)
  - Example: "software engineer" → ["software engineer", "senior software engineer", "lead software engineer"]
- **Location intelligence**:
  - LLM maps profile locations to Adzuna country codes + cities
  - "London, UK" → `countryCode: "gb", cityRegion: "London"`
  - Smart URL building: `/api/jobs/gb/search/1?where=London`
  - Avoids `?where=gb` mistake (returns 0 results)
- **Batch vectorization optimization** (MAJOR PERFORMANCE FIX):
  - **Phase 1**: Fast MySQL save (no vectorization) - 10x faster
  - **Phase 2**: Batch OpenAI API call for all descriptions at once
  - **Phase 3**: Batch Redis storage + MySQL update with redis keys
  - Result: ~50 jobs vectorized in <2 seconds vs ~50 seconds one-by-one
- **Hybrid storage**:
  - Structured columns: `title`, `company`, `location`, `description`, `url`, `salaryRange`, `contractType`, `postedDate`, `expiresDate`
  - `raw_data` JSON column: Complete API response for future data mining
- **Post-processing filters**:
  - Removes non-technical roles (sales, marketing, recruitment, etc.)
  - Empty `what_exclude` arrays (filtering done after fetch)
- **Scheduler + Manual endpoints**:
  - `POST /api/job-search/admin/crawl` - Manual single crawl
  - `POST /api/job-search/admin/crawl/scheduled-simulation` - Test full LLM flow
  - `@Scheduled` cron job (disabled by default, enable via `app.job-crawling.enabled=true`)
  - Default: Daily at 2 AM, crawls 30 keyword variations (10 base × 3 levels)
- **Rate limiting**: 2-second delay between API requests
- **Deduplication**: URL-based duplicate checking before save
- **Fail-fast error handling** (Oct 15):
  - **Any Adzuna API error immediately stops the entire crawl process**
  - No exception swallowing - errors propagate through all layers
  - Exception flow: `AdzunaApiClient` → `JobCrawlerService` → `JobCrawlingApplicationService` → Controller/Scheduler
  - Scheduler re-throws exceptions to ensure Spring sees the failure
  - Clear CRITICAL logs at each layer for easy debugging

**Phase 9 (Oct 14, 2025)** - Line-by-Line Job Matching with Proficiency Weighting (Enhanced Parser):
- **Pure line-by-line matching** (removed full-doc vectors):
  - Each profile line searches for similar job listing lines
  - Aggregates best match scores per job listing
  - Only `listing:line:*` and `profile:line:*` vectors in Redis
- **Three-stage matching pipeline**:
  - **Stage 1**: Each profile line → top-K job lines via Redis HNSW, aggregate by listing ID, take MAX score
  - **Stage 2**: Proficiency-weighted skill matching in job description (regex with word boundaries)
  - **Stage 3**: Re-rank by combined score (60% vector similarity + 40% weighted skill score)
- **Architecture cleanup**:
  - Removed `redis_vector_key` from `JobSearchProfile` entity
  - Removed `redis_vector_key` and `required_skills` from `JobListing` entity
  - Spring JPA auto-drops columns on restart
  - Removed full-doc vectorization code from `JobCrawlerService` and `JobSearchApplicationService`
- **Enhanced job description parser** (Oct 14 update - Newline-first splitting):
  - **Two-phase splitting strategy**:
    - **Phase 1**: Split on `\n` (respects original formatting from job boards)
    - **Phase 2**: Further split long lines (>100 chars) into chunks
  - **Chunk sizes**:
    - **Target**: 100-char chunks (IDEAL_LINE_LENGTH)
    - **Max**: 150-char chunks (MAX_LINE_LENGTH)
    - **Min**: 20-char minimum to filter noise
  - **Long line splitting** (only if >100 chars):
    1. **Simple period split**: `. `
    2. **Semicolon split**: `; `
    3. **Comma split**: `, ` (max 12 parts)
    4. **Force chunk**: Word boundary split at 150-char max
  - **Example**: Job description with `\n` delimiters → split at newlines first, then chunk long lines
  - Respects natural line breaks from job board APIs (Adzuna, Reed, etc.)
  - **Re-vectorize required**: Run `/admin/revectorize/listing-lines` to re-parse all job descriptions
- **Redis key format** (CRITICAL FIX - Oct 14):
  - **Pattern**: `listing:line:{database_line_id}` and `profile:line:{database_line_id}`
  - Uses actual MySQL primary key IDs in Redis keys (NOT random UUIDs)
  - Matching extracts line ID from Redis key → looks up in `job_listing_line` table
  - **Fixed**: `JobCrawlerService` and `AdminController` now save lines to MySQL FIRST, then use returned IDs in Redis keys
  - **Why**: Random UUIDs caused 0 matches because database lookups failed
- **Redis index fix** (CRITICAL):
  - Old index used prefixes: `profile:vector:`, `listing:vector:` (wrong!)
  - New index uses prefixes: `profile:line:`, `listing:line:` (correct!)
  - Must rebuild index: `POST /api/job-search/admin/rebuild-index`
  - Then re-vectorize: `POST /api/job-search/admin/revectorize/listing-lines` and `/revectorize/profile-lines`
- **Proficiency-weighted skill matching** (NEW):
  - Uses `job_search_profile_skill` table with `proficiency_score` column (0-100)
  - **Weighted score formula**: `sum(matched_skill_proficiencies) / sum(all_proficiencies) * 100`
  - Example: If "Java"(80) + "Spring Boot"(70) match out of "Java"(80) + "Spring Boot"(70) + "Python"(60) → weighted: 71.4%
  - Regex search with word boundaries (`\b`) in job descriptions
  - **Two metrics in API response**:
    - `skillMatchPercentage`: Simple count-based (2/3 = 66.7%)
    - `weightedSkillScore`: Proficiency-weighted (150/210 = 71.4%) ← **Used for re-ranking**
  - Missing low-priority skills don't hurt score as much as missing high-priority skills
- **Combined ranking** (60/40 split):
  - Final score = (vector_similarity * 0.6) + (weighted_skill_score / 100 * 0.4)
  - Balances semantic similarity with actual skill match quality
- **Match classification**: STRONG (≥0.85), GOOD (≥0.70), MODERATE (<0.70)
- **Cache bypass feature** (Oct 14 fix):
  - `/matching-results` endpoint accepts `refresh=true` query parameter
  - Use case: After re-vectorizing job listings with new parser, force fresh match computation
  - Default behavior: Return cached matches if topK hasn't changed significantly
  - With `refresh=true`: Always performs fresh vector search + skill analysis
  - Logs indicate "Performing FRESH search" vs "Using cached matches"
- **Admin endpoints**:
  - `POST /admin/rebuild-index` - Drop old index, create with line prefixes
  - `POST /admin/revectorize/listing-lines` - Parse job descriptions → lines → batch vectorize (uses DB IDs)
  - `POST /admin/revectorize/profile-lines` - Re-vectorize existing profile lines (uses DB IDs)
- **Production-ready**: Line-by-line matching eliminates full-doc dilution, matches specific requirements to specific job sections

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

## Future Enhancements 📋
1. AI-generated suggestions for adding missing tasks
2. "Add to resume" button pre-filling editor
3. Track user actions on suggestions
4. Candidate comparison queries
5. Career path visualization
6. Export to PDF/ATS-friendly formats
