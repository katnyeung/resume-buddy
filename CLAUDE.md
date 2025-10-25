# Resume Buddy - AI-Powered Resume Enhancement Platform

## 📌 Current State: Phase 11.15 - Production Ready MVP
**Last Updated**: October 25, 2025
**Status**: Production-ready with PostgreSQL (Neon), RunPod GPU parsing, auto file cleanup, 3-LLM optimized job analysis, client-side auth guards

## Project Overview
AI resume analysis platform with Lexical editor, Neo4j graph for job/skill relationships, O*NET occupation mapping, vector-based job matching, and interactive skill exploration.

## Tech Stack
- **Backend**: Spring Boot 3.2.1 + Java 17 + PostgreSQL 15 (Neon) + Neo4j 5.x + Redis Stack
  - Resume API (port 8080) - Resume analysis, job analysis, credits, async queue
  - Job Search Service (port 8085) - Profile matching, skill discovery, job crawling
  - Interview Practice (port 8086) - Python FastAPI + LangGraph (Experimental)
- **Frontend**: Next.js 14 + TypeScript + Lexical Editor + Tailwind CSS
- **AI/Data**: Grok-4-fast-reasoning (X.AI) + OpenAI Embeddings + O*NET API + Whisper/TTS
- **Document Parsing**: Docling (Python FastAPI + Docker) with RunPod serverless GPU support
- **Vector Search**: Redis Stack with RediSearch + HNSW indexing

## Key Features

### Resume Analysis
- Skill credibility scoring (STRONG/MODERATE/WEAK/NONE)
- Line value ranking (EXCELLENT/GOOD/MODERATE/LOW)
- Multi-occupation mapping (2-3 O*NET SOCs per job)
- LLM-powered task coverage insights with radar chart

### Job Search & Matching
- Vector-based semantic matching (profile + line-level vectors)
- Hybrid ranking: 60% vector similarity + 40% proficiency-weighted skills
- Smart caching with Force Refresh option
- Date filtering (7d/2wk/1mo/2mo/3mo/All) + topK selector (50-500/ALL)
- Multi-source job crawling (Reed, Adzuna, JSearch) with web scraping

### Interactive Skill Discovery
- **Skill Tag Cloud**: Top 30 in-demand skills with job counts
- **Drilldown Modal**: AND logic filtering with related skill suggestions
- **Skill Train**: Interactive career path explorer (build skill combinations)
- **Skill Heatmap**: 2D co-occurrence matrix (10×10 to 25×25)

### Production Features
- **User credits system**: Token-based billing with ACID transactions
- **Async job queue**: MySQL-based queue with @Scheduled worker
- **Auto file cleanup**: Deletes parsed files after 7 days (saves disk space)
- **RunPod GPU support**: Serverless OCR for resume parsing

## Database Layers
1. **PostgreSQL/Neon (resumebuddy)**: Resumes, analysis, user credits, job queue
2. **PostgreSQL/Neon (jobsearch)**: Profiles, job listings, matches, crawl logs
3. **Neo4j**: Job/occupation/skill graph with O*NET taxonomy
4. **Redis**: Vector embeddings (1536-dim) with HNSW indexing

## Quick Start

```bash
# 1. Start infrastructure
./start-with-docker.sh  # Neo4j, Docling
cd job-search-service && docker-compose up -d  # Redis

# 2. Start services
cd backend && mvn spring-boot:run &              # Resume API :8080
cd job-search-service && mvn spring-boot:run &  # Job Search :8085
cd frontend && npm run dev                       # Frontend :3000
```

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
    use-runpod: ${DOCLING_USE_RUNPOD:false}
  runpod:
    endpoint-id: ${RUNPOD_ENDPOINT_ID}
    api-key: ${RUNPOD_API_KEY}
  job-crawling:
    adzuna:
      app-id: ${ADZUNA_APP_ID}
      app-key: ${ADZUNA_APP_KEY}
    reed:
      api-key: ${REED_API_KEY}
    jsearch:
      api-key: ${JSEARCH_API_KEY}
  file:
    cleanup:
      enabled: true
      retention-days: 7
```

### Frontend (.env.local)
```bash
NEXT_PUBLIC_API_URL=http://localhost:8080/api
```

## Key API Endpoints

**Resume Management** (:8080):
- `POST /api/resumes/upload` - Upload file
- `POST /api/resumes/{id}/parse` - Parse with Docling
- `POST /api/resumes/{id}/analyze` - Line-by-line + structured analysis
- `DELETE /api/resumes/{id}` - Delete with Neo4j cleanup

**Job Analysis** (:8080):
- `POST /api/resumes/{resumeId}/experiences/{expId}/analyze` - Deep analysis with O*NET
- `GET /api/resumes/{resumeId}/experiences/{expId}/analysis` - Get cached results

**Async Queue** (:8080):
- `POST /api/jobs/resumes/{id}/analyze/async` - Queue resume analysis
- `GET /api/jobs/{jobId}/status` - Poll status (QUEUED/PROCESSING/COMPLETED/FAILED)

**Job Search** (:8085):
- `POST /api/job-search/profiles` - Create profile from experiences
- `GET /api/job-search/profiles/{id}/matching-results?topK=20&refresh=false` - Get matches
- `GET /api/job-search/skills/top?limit=30` - Top skills tag cloud
- `GET /api/job-search/skills/heatmap?topN=20` - Skill co-occurrence matrix
- `POST /api/job-search/skill-train/path` - Explore skill path

**Admin** (:8085):
- `POST /api/job-search/admin/crawl` - Manual job crawl (Reed/Adzuna/JSearch)
- `POST /api/job-search/admin/rebuild-index` - Rebuild Redis index
- `POST /api/job-search/admin/revectorize/listing-lines?daysBack=14` - Re-vectorize jobs
- `POST /api/admin/cleanup-files` - Trigger file cleanup

**Swagger**: http://localhost:8085/swagger-ui.html

## Key Implementation Files

**Resume API (backend/)**:
- `JobAnalysisService.java` - Main orchestrator (3 LLM calls)
- `Neo4jGraphService.java` - Graph queries + skill mapping
- `UserCreditService.java` - Token credit management
- `FileCleanupService.java` - Auto file cleanup (NEW)
- `RunPodDoclingService.java` - RunPod GPU integration (NEW)

**Job Search Service (job-search-service/)**:
- `domain/service/` - JobPostGenerator, SkillMatcher, JobCrawlerService
- `application/service/` - JobSearchApplicationService, JobMatchingApplicationService
- `infrastructure/redis/` - RedisVectorService (vectors in Redis only)
- `infrastructure/external/jobsources/` - AdzunaApiClient, ReedApiClient, JSearchApiClient
- `service/` - Neo4jJobListingService (skill co-occurrence, graph indexing)

**Frontend**:
- `LexicalEditor.tsx` - Rich text editor
- `AnalysisSummary.tsx` - ATS display + Skill Train
- `JobAnalysisReport.tsx` - Deep analysis + radar chart
- `SkillFilterCloud.tsx` - Top skills tag cloud
- `SkillHeatmap.tsx` - Co-occurrence matrix
- `JobMatchingResults.tsx` - Job matching page
- `hooks/useAuth.ts` - Client-side authentication guard (NEW)

## Neo4j Graph Structure

**Key Nodes**: `JobExperience`, `Occupation` (O*NET SOC), `Skill`, `ONetSkill`, `ONetTechnology`, `ONetActivity`

**Key Relationships**:
- `(JobExperience)-[:MAPS_TO]->(Occupation)` - Multi-occupation mapping
- `(JobExperience)-[:REQUIRES_SKILL]->(Skill)` - Technical skills
- `(Occupation)-[:REQUIRES_SKILL]->(ONetSkill)` - Soft skills
- `(Skill)-[:DEMONSTRATES]->(ONetSkill)` - LLM-mapped soft skills

## Production Deployment

**Deployment Scripts** (AWS Lightsail):
```bash
# Backend (Resume API :8080)
cd backend && ../deploy-backend.sh

# Job Search Service (:8085)
cd job-search-service && ../deploy-job-search.sh

# Frontend (:3000)
cd frontend && ../deploy-frontend.sh
```

**What the scripts do**:
- Stop service to prevent CPU spikes
- Build/compile application
- Upload artifacts to server
- Restart service and verify status
- Safe error handling with rollback

**Requirements**:
- `~/resume-buddy.pem` - SSH key
- Lightsail IP: 13.43.37.64
- Systemd services: `resume-api`, `job-search`, `frontend`

## Common Issues & Solutions

**Analysis not showing**: Check resume status = "ANALYZED", verify API keys
**Docling service down**: `cd docling-service && docker-compose logs -f`
**Neo4j connection**: Verify bolt://localhost:7687 and credentials
**Job matching returns 0 results**:
  - `POST /api/job-search/admin/rebuild-index` (rebuild Redis index)
  - `POST /api/job-search/admin/revectorize/listing-lines?daysBack=14` (re-vectorize)
**Matches not updating**: Use `?refresh=true` query parameter
**Redis data lost**: Re-vectorize recent jobs (~$0.10 for 14 days)

## Recent Updates

### Phase 11.15 (Oct 25) - Client-Side Auth Guards ✅ [CRITICAL SECURITY FIX]
- Added `useAuth()` hook for authentication verification
- Protected routes now redirect to login if unauthenticated
- Applied to: `/resume/[id]`, `/job-search/[profileId]`, `/analysis/[analysisId]`
- Previously pages were accessible without auth (APIs returned 403 but UI rendered)

### Phase 11.14 (Oct 22) - File Cleanup System ✅
- Auto-deletes parsed resume files after 7 days (saves disk space)
- Scheduled cleanup runs daily, configurable retention period
- Manual cleanup endpoints for admin

### Phase 11.13 (Oct 22) - Adzuna Web Scraping ✅
- Two-stage fetch: API search → web scraping for full descriptions
- Smart redirect parsing, site-specific extraction (TotalJobs JSON, Reed/CV-Library CSS)
- 10-20+ skills/job vs 1-3 with truncated descriptions

### Phase 11.12 (Oct 22) - Vector Storage Optimization ✅
- Removed PostgreSQL vector backup (50% storage reduction)
- Vectors now stored ONLY in Redis (AWS Lightsail 2GB)
- Trade-off: ~$0.10 re-vectorization cost if Redis fails (acceptable for MVP)

### Phase 11.11 (Oct 22) - RunPod Serverless GPU ✅
- Dual-mode Docling: Local Docker OR RunPod serverless
- Pay-per-use GPU ($0.0002/sec vs $0.50+/hr always-on)
- Toggle via `DOCLING_USE_RUNPOD` env var

### Phases 11.9-11.10 (Oct 21) - PostgreSQL Migration ✅
- Migrated both services from MySQL to Neon PostgreSQL (serverless)
- Cost savings: Neon free tier vs $15-20/month Aurora
- JSONB for efficient JSON storage, custom ENUM types

**For detailed implementation history, see [CLAUDE_IMPLEMENTATION_LOG.md](./CLAUDE_IMPLEMENTATION_LOG.md)**

## MVP Scope - Important Constraints
- This is an MVP - avoid out-of-scope features
- No setup guides needed - update README/CLAUDE.md instead
- Focus on implementation, summaries over detailed explanations
- User handles verification - prioritize making changes

## Future Enhancements 📋
1. AI-generated suggestions for adding missing tasks
2. "Add to resume" button pre-filling editor
3. Interview Practice frontend integration
4. Career path visualization
5. Export to PDF/ATS-friendly formats
