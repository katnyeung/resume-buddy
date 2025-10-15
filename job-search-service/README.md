# Job Search Service

AI-powered job matching service using vector similarity and skill gap analysis.

## Overview

Separate Spring Boot microservice that generates personalized job search profiles from resume experiences and matches candidates with job listings using Redis vector search.

## Architecture (DDD)

```
domain/
├── model/          - Entities (JobSearchProfile, JobListing, JobMatch)
├── repository/     - JPA repositories
└── service/        - Domain services (JobPostGenerator, SkillMatcher)

application/
└── service/        - Application services (orchestration)

infrastructure/
├── external/       - API clients (ResumeApi, Grok, OpenAI)
├── redis/          - Vector storage service
├── config/         - Spring configuration
└── persistence/    - (future: repository implementations)

api/
├── controller/     - REST controllers
└── dto/            - Request/response DTOs
```

## Tech Stack

- **Spring Boot 3.2.1** + Java 17
- **MySQL 8.0** - Persistent storage
- **Redis Stack** - Vector search (RediSearch + HNSW)
- **OpenAI Embeddings API** - text-embedding-3-small (1536 dims)
- **Grok-4** - Job post generation
- **Resume API** - HTTP client to resume-api:8080

## Key Features

1. **Multi-Experience Profile Generation** - Combine 1-N resume experiences
2. **LLM-Generated Job Posts** - Natural language profiles
3. **Vector Similarity Search** - Cosine similarity with HNSW indexing
4. **Skill Gap Analysis** - Matched vs missing skills
5. **Real-time Matching** - <500ms search queries

## API Endpoints

### Profile Management
```
POST   /api/job-search/profiles
PUT    /api/job-search/profiles/{id}
GET    /api/job-search/profiles/{id}
GET    /api/job-search/profiles?resumeId={resumeId}
DELETE /api/job-search/profiles/{id}
```

### Job Matching
```
POST /api/job-search/profiles/{id}/search?topK=20
GET  /api/job-search/profiles/{id}/matches
```

## Setup

### 1. Start Redis Stack
```bash
cd job-search-service
docker-compose up -d
```

### 2. Configure Environment
```bash
export OPENAI_API_KEY=sk-...
export GROK_API_KEY=xai-...
export DB_USERNAME=root
export DB_PASSWORD=root
```

### 3. Run Service
```bash
mvn spring-boot:run
```

Service runs on **port 8081**

## Database Schema

**MySQL** (`jobsearch` database):
- `job_search_profile` - Generated profiles with Redis keys
- `job_listing` - External job postings
- `job_match` - Similarity scores + skill gaps (JSON)

**Redis**:
- `profile:vector:{id}` - 1536-dim embeddings
- `listing:vector:{id}` - Job listing embeddings
- `idx:vectors` - RediSearch HNSW index

## Flow

1. User selects 1-N experiences in frontend
2. POST `/api/job-search/profiles` with `{resumeId, experienceIds[]}`
3. Service fetches experiences from resume-api:8080
4. LLM generates job post from combined experiences
5. OpenAI creates vector embedding (1536 floats)
6. Store profile in MySQL + vector in Redis
7. POST `/api/job-search/profiles/{id}/search`
8. Redis KNN search finds top-K similar job vectors
9. Fetch job details from MySQL, compute skill gaps
10. Return ranked matches with skill analysis

## Redis Vector Index

Created on startup with:
- **Algorithm**: HNSW (Hierarchical Navigable Small World)
- **Dimension**: 1536
- **Distance Metric**: Cosine similarity
- **Index Name**: `idx:vectors`

## Configuration

See `src/main/resources/application.yml`:
- Resume API URL (default: http://localhost:8080/api)
- OpenAI embedding model
- Grok LLM settings
- MySQL connection
- Redis connection

## MVP Scope

**Included**:
- Multi-experience profile creation
- Vector search with Redis
- Skill gap analysis
- Manual search triggers

**Future** (Post-MVP):
- Background job scraping scheduler
- Real job board integrations (LinkedIn, Indeed)
- Email notifications for new matches
- Track user actions (applied, saved)
- Career path recommendations

## RedisInsight UI

Access at http://localhost:8001 to visualize vectors and debug queries.
