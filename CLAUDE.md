# Resume Buddy - AI-Powered Resume Enhancement Platform

## 📌 Current State: Phase 11.1 - Reed API Full Description + Job Analysis Optimization
**Last Updated**: October 18, 2025 (Evening)
**Status**: MVP + Job search microservice + Token credit system + Crawl activity logging + Reed API full job descriptions + **Optimized job analysis endpoint**

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
  - **Reed.co.uk API integration** (Oct 15):
    - UK-focused job board (largest in UK)
    - Basic Auth with API key
    - Converts Reed DTOs to common Adzuna format
    - Supports keywords, location, salary filters
    - Distance-based search (10 miles default)
    - Full-time/part-time/permanent filters
  - **JSearch (RapidAPI) integration** (Oct 15):
    - Aggregates jobs from Google Jobs, Indeed, LinkedIn, Glassdoor, etc.
    - RapidAPI headers (X-RapidAPI-Key, X-RapidAPI-Host)
    - Natural language queries ("java developer in london")
    - Smart date filtering (maps maxDaysOld to "today"/"3days"/"week"/"month")
    - Converts JSearch DTOs to common Adzuna format
    - Supports multiple countries via country code parameter
    - Rich metadata: employer logos, multiple apply links, ONET codes
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
  - `POST /api/job-search/admin/crawl` - Manual single crawl (logs activity)
  - `POST /api/job-search/admin/crawl/scheduled-simulation` - Test full LLM flow (logs activity)
  - `GET /api/job-search/admin/crawl/history?limit=5` - Get recent crawl activity logs
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
- **Job skill extraction & Neo4j indexing** (Oct 17 - Keyword-based):
  - **Keyword matching** against Neo4j `Skill` vocabulary (no LLM required!)
  - Uses regex with word boundaries (`\b`) for accurate matching
  - **Uses full job descriptions** from `job_listing_line` table (concatenated) instead of truncated `description` field
  - Skills stored in MySQL `job_listing.extracted_skills` JSON column
  - Direct Neo4j indexing via `Neo4jJobListingService` (no HTTP calls to backend)
  - Creates `JobListing` nodes linked to `Skill` nodes via `REQUIRES_SKILL`
  - Shared Neo4j Aura instance with backend service (database: `neo4j`)
  - Skill vocabulary cached for 1 hour (Spring `@Cacheable`)
  - **Performance**: Processes 100+ jobs/second (vs 2-3 jobs/sec with LLM)
  - **Cost**: $0 (vs ~$0.001 per job with GPT-4o-mini)
  - Enables market insights: skill demand analysis, co-occurrence patterns
- **Job analysis endpoint optimization** (Oct 18 - Database-level limiting):
  - **Pageable-based queries**: Uses Spring Data `Pageable` to limit at database level instead of in-memory
  - **Default limit**: 1000 jobs per request (configurable via `maxJobs` parameter)
  - **Performance**: Avoids loading thousands of records into memory when only analyzing a subset
  - **Use case**: Process jobs in controlled batches (e.g., 100 jobs/request) for gradual market insights
  - **Parameters**: `batchSize` (processing batch), `maxJobs` (database limit), `maxDaysOld` (filter), `forceReanalyze` (re-analyze analyzed jobs)
  - **Database optimization**: `LIMIT` clause added to SQL queries via JPA repository methods
  - Endpoint: `POST /api/job-search/admin/analyze-jobs`

**Phase 9 (Oct 14, 2025)** - Line-by-Line Job Matching with Proficiency Weighting (Enhanced Parser):

**Phase 10 (Oct 18, 2025)** - Token Credit System + Async Job Queue ✅:
- **Prepaid token credit system**:
  - New tables: `user_credits` (balance tracking), `credit_transactions` (audit trail)
  - Default: 1000 free credits per new user
  - Costs: Resume upload=50, Resume analysis=100, Job experience=50, Profile=25 credits
  - ACID transactions: Credits deducted on job start, refunded on failure
  - **Upload page secured**: Requires authentication, shows credit balance, blocks upload if insufficient credits (402 Payment Required)
- **MySQL-based async job queue**:
  - New table: `job_queue` with optimistic locking (prevents race conditions)
  - Status flow: QUEUED → PROCESSING → COMPLETED/FAILED
  - Retry logic: Max 3 attempts with exponential backoff
  - Rate limiting: Max 3 concurrent jobs globally, 2 per user
- **Scheduled worker**:
  - `@Scheduled` polling every 2 seconds
  - Parallel processing (up to 3 workers)
  - Auto-refund credits on job failure
- **New async API endpoints**:
  - `POST /api/jobs/resumes/{id}/analyze/async` - Queue resume analysis
  - `POST /api/jobs/resumes/{resumeId}/experiences/{expId}/analyze/async` - Queue job experience analysis
  - `GET /api/jobs/{jobId}/status` - Poll job status with queue position
  - `GET /api/users/{userId}/credits` - Get credit balance
  - `POST /api/users/admin/credits/grant` - Admin credit management
- **Why MySQL queue over Kafka/SQS**:
  - Zero additional infrastructure (<100 users)
  - ACID transactions (credit deduction atomic with job status)
  - Easy debugging (plain SQL queries)
  - $0/month vs $40-100/month for SQS+Lambda or Kafka
- **Cloud-ready design**: Designed for AWS Lightsail + Aurora MySQL + Redis Cloud migration (Phase 11)

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

**Phase 11 (Oct 18, 2025)** - Job Crawl Activity Logging + Complete Job Analysis + UX Improvements ✅:
- **Audit trail for all crawl operations**:
  - New table: `crawl_activity_log` with source, keywords, location, result counts, status, timestamps
  - Columns: id, source (ADZUNA/REED/JSEARCH), keywords, location, totalFetched, totalSaved, duplicatesUpdated, duplicatesSkipped, failedToProcess, status (SUCCESS/FAILED), errorMessage, processingTimeMs, crawledAt
  - Indexes: crawled_at DESC, source, status for fast queries
- **Automatic logging in JobCrawlingApplicationService**:
  - Logs captured on EVERY crawl operation (manual + scheduled)
  - Try-catch wrapper: Logs success OR failure with error message
  - Preserves existing error handling behavior (re-throws exceptions)
- **New API endpoint**:
  - `GET /api/job-search/admin/crawl/history?limit=5` - Returns recent crawl activities
  - Returns top 5 by default, ordered by most recent first
- **Frontend visibility on job search page**:
  - **CrawlActivityHistory.tsx** component displays last 5 crawl activities
  - Shows: timestamp ("2h ago"), source badge (color-coded), keywords/location, records updated/saved, status badge
  - Auto-refreshes every 30 seconds
  - Manual refresh button with loading spinner
  - Success entries: Show fetched/new/updated counts with icons
  - Failed entries: Red border, displays error message (truncated to 100 chars)
  - Positioned above JobSearchProfile section
- **User benefits**:
  - Transparency: Users see when job boards were last updated
  - Trust: Visual confirmation that crawling is working
  - Debugging: Failed crawls show error messages immediately
- **Complete job analysis mode** (Oct 18):
  - **topK selector**: Dropdown in UI (50/100/200/300/500/ALL)
  - **ALL mode**: Setting topK=9999 analyzes EVERY job listing in database (no limit)
  - **Use case**: Market analysis - see all 1773 jobs scored against your profile
  - **Vector search optimization**: topK * 10 multiplier (increased from * 2) for broader coverage
  - **Performance**: ALL mode searches 50,000 results per profile line (covers all job lines)
  - **Benefits**: Find hidden gems, statistical analysis, complete market coverage
- **Date range filter** (Oct 18):
  - **maxDaysOld parameter**: Filter jobs by posted/fetched date
  - **UI dropdown**: "from last" - 7 days / 2 weeks / 1 month / 2 months / 3 months / All time
  - **Default**: 1 month (30 days) - only show recent job postings
  - **Logic**: Uses `postedDate` if available, falls back to `fetchedAt`
  - **Applied post-search**: Filters cached matches + new searches
  - **Benefits**: Avoid old/expired listings, focus on fresh opportunities
- **Smart caching & performance** (Oct 18):
  - **Page load behavior**: Auto-loads existing results from `job_match` table (instant!)
  - **Cache-first approach**: Shows cached matches immediately, no vector search on page load
  - **Preference persistence**: topK and maxDaysOld saved to localStorage (remembers across sessions)
  - **Refresh logic**: Backend checks cache → returns if exists → only searches if Force Refresh checked
  - **Force Refresh**: Explicitly triggers fresh vector search + updates cache
  - **Empty state**: Only shown if no cached results exist (first-time user or after cache expiry)
  - **Redis LIMIT fix**: Added `.limit(0, topK)` to override Redis default LIMIT of 10
  - **Performance**: Page load <1s (cached from job_match), Force Refresh ~10-15s (ALL mode)
- **Star rating UX improvement** (Oct 18):
  - **Direct star selection**: Click any star (1/2/3) to set rating directly
  - **Toggle behavior**: Click same star again to remove rating (set to 0)
  - **Visual feedback**: Filled stars (yellow) vs empty stars (gray), hover effects
  - **Smart tooltips**: "Set 3-star rating", "Change to 2-star", "Remove 1-star rating"
  - **No more cycling**: Replaced old click-to-cycle (0→1→2→3→0) with direct selection
  - **Better UX**: Users can instantly set priority without multiple clicks
- **Logging improvements** (Oct 18):
  - **Reduced verbosity**: Disabled per-job match logging (was generating 1000+ log lines)
  - **Skill gap analysis**: Changed from INFO to DEBUG level
  - **Vector search details**: Changed from INFO to DEBUG level
  - **Per-match scoring**: Commented out (easily re-enabled for debugging)
  - **Summary logs kept**: High-level progress logs remain (STAGE 1/2 complete, total matches)
  - **Result**: Clean, readable logs showing only important milestones
- **Preserve user actions on refresh** (Oct 18):
  - **Problem**: Force Refresh was deleting ALL old matches, losing red flags/saved/applied status
  - **Solution**: Only delete matches without user actions (`isSaved=0 AND isApplied=false AND isRedflag=false`)
  - **Preserved data**: Saved ratings (1-3 stars), applied status, red flags
  - **Updated scores**: Recalculates similarity + skill gap while preserving user actions
  - **Result**: Users never lose their flags/ratings when refreshing matches

**Phase 11.1 (Oct 18, 2025 Evening)** - Reed API Full Description + HTML Stripping + UI Fixes ✅:
- **Problem 1**: Both Adzuna and Reed `/search` endpoints return truncated descriptions with "…" suffix
  - Example: "Through our behaviours of telli..." (truncated at ~500 chars)
  - Only captured 1-2 skills per job instead of 10-20+
  - Skill extraction severely limited by incomplete data
- **Solution 1**: Implemented two-stage Reed API fetching
  - **Stage 1**: `/search` endpoint - get list of jobs (fast)
  - **Stage 2**: `/jobs/{jobId}` endpoint - fetch FULL description for each job (complete)
  - New DTO: `ReedJobDetailsDto` for details endpoint
  - 500ms rate limiting between detail fetches to avoid overwhelming API
- **Performance impact**:
  - 50 jobs now takes ~30-40 seconds (vs 5-10 seconds with truncated)
  - Trade-off: Slower crawl but COMPLETE job data for accurate skill extraction
- **Problem 2**: Reed and DevITJobs return HTML-formatted descriptions
  - Example: `Do:</strong></p> <ul> <li> <p>Build, test, and deploy new features`
  - HTML tags interfere with skill extraction and readability
- **Solution 2**: Added HTML stripping to `JobDescriptionParser`
  - Strips ALL HTML tags: `<p>`, `<strong>`, `<ul>`, `<li>`, etc.
  - Preserves structure: Block tags (`</p>`, `</li>`, `<br>`) → newlines
  - Decodes HTML entities: `&nbsp;` → space, `&amp;` → &, `&bull;` → •
  - Normalizes whitespace: Multiple spaces/newlines collapsed
  - Applied BEFORE line splitting for clean parsing
- **Benefits**:
  - ✅ Full job descriptions (no truncation)
  - ✅ 10-20+ skills per job instead of 1-2
  - ✅ Clean, readable text without HTML noise
  - ✅ Better matching accuracy
  - ✅ Users see complete job requirements
- **Scheduler updated**: Both `JobCrawlScheduler` and `/admin/crawl/scheduled-simulation` now use REED instead of ADZUNA
- **Adzuna deprecated**: No longer used for scheduled crawls (infrastructure preserved for manual use if needed)
- **DevITJobs fixed**: Parser now handles custom XML format (`<job>` tags, `dd.MM.yyyy` dates)
- **Frontend error handling**: Added retry logic for 500 errors (1-second delay, auto-retry once)
- **UI Fix - Refresh button stuck**: Fixed "Refresh Results" button staying in "Refreshing..." state after completion
  - **Root cause**: `finally` block had incorrect conditional logic that skipped state reset on manual button clicks
  - **Solution**: Always reset `loading` and `isManualRefresh` states in `finally` block, use early `return` for retry case
  - **Result**: Button properly returns to normal state after successful/failed requests
- **Files modified**:
  - Created: `ReedJobDetailsDto.java`
  - Updated: `ReedApiClient.java` (added `fetchJobDetails()`, updated `convertToAdzunaFormat()`)
  - Updated: `JobCrawlScheduler.java` (ADZUNA → REED)
  - Updated: `AdminController.java` (ADZUNA → REED in simulation endpoint)
  - Updated: `JobDescriptionParser.java` (added `stripHtml()` method, called at start of `parseDescription()`)
  - Updated: `DevITJobsApiClient.java` (fixed XML parsing for custom format)
  - Updated: `JobMatchingResults.tsx` (added retry logic for 500 errors + fixed button stuck bug)
  - Updated: `job-search/[profileId]/page.tsx` (added retry logic for profile fetch)

## Future Enhancements 📋
1. AI-generated suggestions for adding missing tasks
2. "Add to resume" button pre-filling editor
3. Track user actions on suggestions
4. Candidate comparison queries
5. Career path visualization
6. Export to PDF/ATS-friendly formats
