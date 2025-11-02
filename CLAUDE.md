# Resume Buddy - AI-Powered Resume Enhancement Platform

## 📌 Current State: Phase 11.18 - Interview Practice Production Deployment
**Last Updated**: October 27, 2025
**Status**: Production-ready with interview practice service, Stripe payments, user profile management, PostgreSQL (Neon), RunPod GPU parsing, auto file cleanup, 3-LLM optimized job analysis, client-side auth guards

## Project Overview
AI resume analysis platform with Lexical editor, Neo4j graph for job/skill relationships, O*NET occupation mapping, vector-based job matching, and interactive skill exploration.

## Tech Stack
- **Backend**: Spring Boot 3.2.1 + Java 17 + PostgreSQL 15 (Neon) + Neo4j 5.x + Redis Stack
  - Resume API (port 8080) - Resume analysis, job analysis, credits, async queue
  - Job Search Service (port 8085) - Profile matching, skill discovery, job crawling
  - Interview Practice (port 8086) - Python FastAPI + LangGraph (Docker)
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
- **Stripe payment integration**: Buy credits (£2/200, £5/500, £8/1000) via Stripe Checkout (prices exclude tax)
- **User profile management**: Edit name, view transaction history, soft-delete account
- **Async job queue**: PostgreSQL-based queue with @Scheduled worker
- **Auto file cleanup**: Deletes parsed files after 7 days (saves disk space)
- **RunPod GPU support**: Serverless OCR for resume parsing
- **Google OAuth2 login**: Auto user creation/linking, JWT token generation
- **Interview practice**: AI-powered voice interview with real-time feedback and interrupts

## Database Layers
1. **PostgreSQL/Neon (resumebuddy)**: Resumes, analysis, user credits, job queue, payment records
2. **PostgreSQL/Neon (jobsearch)**: Profiles, job listings, matches, crawl logs
3. **PostgreSQL/Neon (interview_practice)**: Interview sessions, rounds, transcripts
4. **Neo4j**: Job/occupation/skill graph with O*NET taxonomy
5. **Redis**: Vector embeddings (1536-dim) with HNSW indexing + LangGraph checkpoints

## Quick Start

```bash
# 1. Start infrastructure
./start-with-docker.sh  # Neo4j, Docling
cd job-search-service && docker-compose up -d  # Redis
docker-compose up -d  # Interview Practice :8086 (from root)

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
spring:
  mail:
    host: ${MAIL_HOST:email-smtp.eu-west-2.amazonaws.com}
    username: ${AWS_SES_USERNAME}
    password: ${AWS_SES_PASSWORD}
    from: ${MAIL_FROM:noreply@resumebuddy.cv}
stripe:
  api-key: ${STRIPE_SECRET_KEY}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET}
  price-id-200: ${STRIPE_PRICE_ID_200}
  price-id-500: ${STRIPE_PRICE_ID_500}
  price-id-1000: ${STRIPE_PRICE_ID_1000}
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

**Stripe Payments** (:8080):
- `POST /api/payments/create-checkout-session` - Create Stripe Checkout session
- `POST /api/payments/webhook` - Stripe webhook (signature verified)
- `GET /api/payments/success?session_id=xxx` - Verify payment status

**User Profile** (:8080):
- `GET /api/users/{userId}/profile` - Get full profile with credits
- `PUT /api/users/{userId}/profile/name` - Update full name
- `DELETE /api/users/{userId}/account` - Soft delete account
- `GET /api/users/{userId}/profile/transactions` - Transaction history

**Email Notifications** (:8080):
- `POST /api/notifications/send` - Send email to user (requires X-API-Key)
- Used by interview-practice-service for round reminders
- Requires AWS SES SMTP configuration

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
- `StripePaymentService.java` - Stripe Checkout + webhook handling
- `UserProfileService.java` - Profile management + soft delete
- `FileCleanupService.java` - Auto file cleanup
- `RunPodDoclingService.java` - RunPod GPU integration

**Job Search Service (job-search-service/)**:
- `domain/service/` - JobPostGenerator, SkillMatcher, JobCrawlerService
- `application/service/` - JobSearchApplicationService, JobMatchingApplicationService
- `infrastructure/redis/` - RedisVectorService (vectors in Redis only)
- `infrastructure/external/jobsources/` - AdzunaApiClient, ReedApiClient, JSearchApiClient
- `service/` - Neo4jJobListingService (skill co-occurrence, graph indexing)

**Interview Practice Service (interview-practice-service/)**:
- `main.py` - FastAPI entry point
- `api/routes/streaming.py` - WebSocket streaming endpoint
- `domain/agents/interrupt_agent.py` - AI interrupt logic
- `domain/services/email_scheduler.py` - Daily round reminders
- `infrastructure/clients/openai_client.py` - Whisper STT + TTS
- `infrastructure/clients/grok_client.py` - Grok LLM for questions/evaluation

**Frontend**:
- `LexicalEditor.tsx` - Rich text editor
- `AnalysisSummary.tsx` - ATS display + Skill Train
- `JobAnalysisReport.tsx` - Deep analysis + radar chart
- `SkillFilterCloud.tsx` - Top skills tag cloud
- `SkillHeatmap.tsx` - Co-occurrence matrix
- `JobMatchingResults.tsx` - Job matching page
- `app/credits/page.tsx` - Credit purchase page with 3 packages
- `app/profile/page.tsx` - User profile + transaction history
- `app/interview/[sessionId]/[roundNum]/page.tsx` - Interview practice page
- `components/InterviewPractice.tsx` - WebSocket streaming + live transcript
- `lib/api/interview.ts` - Interview API client
- `hooks/useAuth.ts` - Client-side authentication guard

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

# Interview Practice Service (:8086 - Docker)
./deploy-interview-practice.sh

# Frontend (:3000)
cd frontend && ../deploy-frontend.sh
```

**What the scripts do**:
- Backend/Job Search: Stop service → Build JAR → Upload → Restart systemd service
- Interview Practice: Build Docker image → Upload tar.gz → Load & start container
- Frontend: Build Next.js → Upload → Restart systemd service
- Safe error handling with rollback

**Requirements**:
- `~/resume-buddy.pem` - SSH key
- Lightsail IP: 13.43.37.64
- Systemd services: `resume-api`, `job-search`, `frontend`
- Docker installed on server for interview-practice
- Nginx configured for WebSocket proxying (/ws/interview)

## AWS SES Email Setup

The interview-practice-service sends email reminders for scheduled rounds via the backend's notification endpoint. Email is sent using AWS SES (Simple Email Service).

### 1. Verify Domain in AWS SES

1. Go to **AWS Console → SES → Verified identities**
2. Click **Create identity** → Choose **Domain**
3. Enter your domain: `resumebuddy.cv`
4. Select **Easy DKIM** (recommended) → Create identity
5. AWS will provide DKIM CNAME records to add to your DNS

### 2. Add DKIM Records to Namecheap

1. Go to **Namecheap → Domain List → Manage → Advanced DNS**
2. Add the 3 CNAME records provided by AWS SES:
   ```
   Type: CNAME Record
   Host: xyz._domainkey
   Value: xyz.dkim.amazonses.com
   ```
3. Wait for DNS propagation (~5-30 minutes)
4. AWS SES will auto-verify the domain when DNS records are detected

### 3. Create SMTP Credentials

1. Go to **AWS Console → SES → SMTP settings**
2. Click **Create SMTP credentials**
3. AWS will generate:
   - SMTP username (e.g., `AKIAXXXXXXXXXXXXXXXX`)
   - SMTP password (long base64 string - save this!)
4. Save both to your backend `.env` file

### 4. Request Production Access (if in Sandbox)

**Note**: New AWS SES accounts start in "Sandbox mode" - you can only send to verified email addresses.

To send to any email:
1. Go to **SES → Account dashboard**
2. Click **Request production access**
3. Fill out the form:
   - **Use case**: Transactional emails for interview practice reminders
   - **Expected sending volume**: <500 emails/day
   - **Compliance**: We have opt-in (users create sessions)
4. AWS typically approves within 24 hours

### 5. Backend Environment Variables

Add to `backend/.env`:

```bash
# AWS SES SMTP Configuration
AWS_SES_USERNAME=AKIAXXXXXXXXXXXXXXXX
AWS_SES_PASSWORD=your-smtp-password-from-step-3
MAIL_HOST=email-smtp.eu-west-2.amazonaws.com  # Use your region
MAIL_FROM=noreply@resumebuddy.cv
```

**SMTP Endpoints by Region**:
- `eu-west-2` (London): `email-smtp.eu-west-2.amazonaws.com`
- `us-east-1` (N. Virginia): `email-smtp.us-east-1.amazonaws.com`
- `us-west-2` (Oregon): `email-smtp.us-west-2.amazonaws.com`

### 6. Verify Email Sending

Test the notification endpoint:

```bash
curl -X POST http://localhost:8080/api/notifications/send \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-internal-api-key-change-in-production" \
  -d '{
    "userId": "your-user-id",
    "subject": "Test Email",
    "body": "This is a test email from Resume Buddy."
  }'
```

Check AWS SES → Sending statistics for delivery metrics.

### 7. Cost Estimate

- **Free tier**: 62,000 emails/month when sending from AWS Lightsail
- **After free tier**: $0.10 per 1,000 emails
- **Estimated cost**: ~$0.05/month for MVP (<500 emails)

## Common Issues & Solutions

**Analysis not showing**: Check resume status = "ANALYZED", verify API keys
**Docling service down**: `cd docling-service && docker-compose logs -f`
**Neo4j connection**: Verify bolt://localhost:7687 and credentials
**Job matching returns 0 results**:
  - `POST /api/job-search/admin/rebuild-index` (rebuild Redis index)
  - `POST /api/job-search/admin/revectorize/listing-lines?daysBack=14` (re-vectorize)
**Matches not updating**: Use `?refresh=true` query parameter
**Redis data lost**: Re-vectorize recent jobs (~$0.10 for 14 days)
**Job-search-service crashes on AWS Lightsail**:
  - HikariCP thread starvation from expensive Neo4j queries
  - Fixed: Reduced pool size to 5, limited matrix queries to 10×10 max
  - Fixed: Disabled SQL logging (show-sql: false)
  - Fixed: Added 10s query timeout

## Recent Updates

### Phase 11.20 (Oct 31) - AWS SES Email Notifications ✅
**Implemented email notification system for interview practice round reminders:**

**Backend Changes:**
- Added `spring-boot-starter-mail` dependency to pom.xml
- Created `EmailService` with JavaMailSender for SMTP email sending
- Created `NotificationController` with `POST /api/notifications/send` endpoint
- Created `NotificationRequest` DTO (userId, subject, body)
- Configured AWS SES SMTP in application.yml (host, username, password, from)
- Endpoint secured with API key authentication (X-API-Key header)

**Interview Practice Integration:**
- Email scheduler calls backend `/api/notifications/send` endpoint
- Sends round reminders at scheduled times (daily 9 AM cron job)
- Round 1 email sent immediately upon session creation
- Subsequent round emails sent after previous round completion

**AWS SES Setup Required:**
1. Verify domain (resumebuddy.cv) in AWS SES
2. Add DKIM CNAME records to Namecheap DNS
3. Create SMTP credentials in AWS SES console
4. Add credentials to backend `.env` (AWS_SES_USERNAME, AWS_SES_PASSWORD)
5. Request production access (to send to any email, not just verified)

**Benefits:**
- 62,000 free emails/month from AWS Lightsail
- $0.10 per 1,000 emails after free tier
- ~$0.05/month estimated cost for MVP

**Files Modified:**
- `backend/pom.xml` - Added mail dependency
- `backend/src/main/resources/application.yml` - Added SMTP config
- Created: `EmailService.java`, `NotificationController.java`, `NotificationRequest.java`
- `CLAUDE.md` - Added AWS SES setup documentation

### Phase 11.19 (Oct 29) - Job Search Resource Optimization ✅
**Fixed AWS Lightsail resource exhaustion causing crashes:**

**Database Tuning:**
- Reduced HikariCP pool: 10→5 connections (Lightsail has limited RAM)
- Added connection timeout: 20s
- Added leak detection: 60s threshold
- Added query timeout: 10s (prevents runaway queries)

**Query Optimization:**
- Limited skill path matrix: 34×15 → 10×10 (100 pairs vs 510)
- Limited heatmap matrix: topN capped at 15 (225 pairs max)
- Added LIMIT 200 to co-occurrence queries

**Logging Reduction:**
- Disabled SQL logging (show-sql: false)
- Reduced log levels: DEBUG→INFO, SQL→WARN
- Silenced HikariCP housekeeping warnings

**Result**: Reduced query load by 80%, eliminated thread starvation warnings

### Phase 11.18 (Oct 27) - Interview Practice Production Deployment ✅
**Full-stack integration of AI-powered voice interview practice:**

**Frontend (Next.js/React):**
- Created `/app/interview/[sessionId]/[roundNum]/page.tsx` - Interview practice page
- `InterviewPractice.tsx` component with full feature parity from HTML test
- WebSocket client with real-time audio streaming (30ms chunks)
- Live transcript display with incremental updates
- AI interrupt handling (gentle/moderate/firm with visual indicators)
- MediaRecorder management (restart on interrupt, silent restart)
- Audio playback for questions and interrupts
- Final evaluation with score display and feedback
- Debug log panel for troubleshooting
- API client in `lib/api/interview.ts`

**Backend (Python/FastAPI):**
- Updated email scheduler URL from `/interview/session/{id}/round/{num}` to `/interview/{id}/{num}`
- Dockerfile with ffmpeg and audio processing dependencies
- docker-compose.yml for production deployment
- .dockerignore for clean builds

**DevOps:**
- `deploy-interview-practice.sh` script for AWS Lightsail
- Builds Docker image locally → uploads tar.gz → loads on server
- Health check verification after deployment
- Nginx WebSocket proxying configuration documented

**Key Features:**
- WebSocket streaming at `wss://resumebuddy.cv/ws/interview`
- Real-time transcription with OpenAI Whisper
- AI interrupts based on coaching level (CONSERVATIVE/MODERATE/AGGRESSIVE)
- Scheduled multi-round practice with email reminders
- 50 credits per session (3 rounds)
- Cost: ~$0.06 per session (STT + TTS + Grok LLM)

### Phase 11.17 (Oct 25) - Stripe Payment Integration ✅
**Complete credit purchase system with Stripe Checkout:**

**Backend (Java/Spring Boot):**
- Added `stripe-java` dependency (v25.13.0) to pom.xml
- Created `payment_records` table to track all Stripe transactions
- Added `deleted_at` column to `users` table for soft deletion
- `PaymentRecord` entity with status tracking (PENDING/COMPLETED/FAILED/REFUNDED)
- `PaymentPackage` enum defining 3 credit packages (200/$1, 500/$4, 1000/$8)
- `StripePaymentService`: Creates Checkout sessions, handles webhooks, grants credits
- `StripePaymentController`: Create session, webhook endpoint (no auth), verify payment
- `UserProfileService`: Get profile, update name, soft delete, transaction history
- `UserProfileController`: Profile CRUD endpoints
- Whitelisted `/api/payments/webhook` in SecurityConfig (Stripe signature verification)
- Added Stripe configuration to application.yml (API keys, webhook secret, price IDs)

**Frontend (Next.js/TypeScript):**
- `/credits` page: 3 credit packages with pricing, features, Stripe redirect
- `/credits/success` page: Payment verification, success/pending/failed states
- `/profile` page: Edit name, view credits, transaction history table, delete account modal
- Updated `CreditBalance` component: Clickable badge → redirects to /credits
- Updated `AppHeader`: Added profile icon button
- Added API functions: `createCheckoutSession`, `verifyPaymentSuccess`, `getUserProfile`, `updateUserName`, `deleteAccount`, `getTransactionHistory`

**Payment Flow:**
1. User clicks credit badge → /credits page
2. Selects package → frontend calls `POST /api/payments/create-checkout-session`
3. Backend creates Stripe session, saves PENDING payment record
4. Frontend redirects to Stripe Checkout (hosted page)
5. User completes payment on Stripe
6. Stripe redirects to `/credits/success?session_id=xxx`
7. **Asynchronously:** Stripe webhook → backend verifies signature → grants credits
8. Payment record updated to COMPLETED
9. Frontend success page polls to verify credits added

**Setup Requirements:**
- Create 3 products in Stripe Dashboard with Price IDs
- Configure webhook endpoint: `https://resumebuddy.cv/api/payments/webhook`
- Set environment variables: `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, price IDs
- Run SQL migrations: `add_soft_delete_column.sql`, `create_payment_records_table.sql`

### Phase 11.16 (Oct 25) - Resume Analysis Job Check Fix ✅
**Prevent Duplicate Analysis Jobs on Page Refresh:**
- Added active job detection on LexicalEditor page load (same pattern as job experience analysis)
- Backend: `GET /api/jobs/resumes/{resumeId}/check-active` endpoint checks for QUEUED/PROCESSING jobs
- Frontend: On mount, checks for active job and auto-resumes JobStatusIndicator if found
- Prevents double-charging (50 credits → 100 credits) when user refreshes during analysis
- User sees continuous job progress indicator even after refresh

**UI Improvement:**
- Added blue info banner explaining raw parsed text from Docling (shows before first analysis)
- Users now understand they're viewing raw PDF extraction and can edit before "Analyze"
- Banner disappears after first analysis to avoid clutter

### Phase 11.15 (Oct 25) - Security Fixes ✅ [CRITICAL]
**Client-Side Auth Guards:**
- Added `useAuth()` hook for authentication verification
- Protected routes now redirect to login if unauthenticated
- Applied to: `/resume/[id]`, `/job-search/[profileId]`, `/analysis/[analysisId]`
- Previously pages were accessible without auth (APIs returned 403 but UI rendered)

**Google OAuth2 Login Fix:**
- Fixed redirect_uri_mismatch error - OAuth2 endpoints served at root (not /api prefix)
- Set `OAUTH2_REDIRECT_URI=https://resumebuddy.cv/login/oauth2/code/google` in production .env
- Added `server.forward-headers-strategy: framework` for reverse proxy support
- OAuth2 flow: auto creates new users OR links Google to existing email accounts
- Generates initial credits for new Google users
- **Required in Google Cloud Console**: Add `https://resumebuddy.cv/login/oauth2/code/google` to Authorized Redirect URIs

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
