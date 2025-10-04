# Resume Buddy - Graph Architecture Master Plan
**Version**: 1.0
**Date**: October 3, 2025
**Status**: Planning Phase

## Executive Summary

This document outlines the comprehensive graph-based architecture for Resume Buddy, leveraging Neo4j to create a rich knowledge graph that connects resumes, skills, activities, occupations, and job postings. The architecture supports:

1. **Multi-occupation matching** (one job → multiple O*NET occupations)
2. **Activity-based clustering** using LLM (no ML libraries needed)
3. **Job board integration** with activity matching
4. **Cross-resume analytics** (skill trends, common patterns)
5. **Career pathfinding** and recommendations

---

## Table of Contents

1. [Core Problem Statement](#core-problem-statement)
2. [Graph Model Architecture](#graph-model-architecture)
3. [Multi-Occupation Matching](#multi-occupation-matching)
4. [Activity Clustering Strategy](#activity-clustering-strategy)
5. [Use Cases & Scenarios](#use-cases--scenarios)
6. [Job Board Integration](#job-board-integration)
7. [Cross-Resume Analytics](#cross-resume-analytics)
8. [Implementation Phases](#implementation-phases)
9. [Technical Specifications](#technical-specifications)
10. [Future Extensions](#future-extensions)

---

## 1. Core Problem Statement

### Current Limitations
- Neo4j used as document store, not leveraging graph capabilities
- Job experiences map to single O*NET occupation (unrealistic)
- No skill extraction or matching
- No activity-level granularity
- No cross-resume intelligence
- No job posting integration

### Target State
A rich knowledge graph where:
- **Jobs map to multiple occupations** with confidence scores
- **Activities are clustered semantically** and linked across jobs/occupations/postings
- **Skills form a network** showing relationships and progression paths
- **Job postings are integrated** and matched against candidate profiles
- **Cross-resume patterns emerge** (trending skills, common career paths)

---

## 2. Graph Model Architecture

### Complete Node Types

```cypher
// Core Entities
(:Resume {id, user_id, created_at, version})
(:JobExperience {id, resume_id, title, company, location, start_date, end_date, description, seniority})
(:Occupation {code, title, description, category, typical_salary_range})
(:JobPosting {id, title, company, posted_date, url, description, location, salary_range})

// Knowledge Graph Nodes
(:Skill {id, name, category, subcategory, is_technical})
(:Technology {id, name, category, version, popularity_score})
(:ActivityCluster {id, name, category, description, typical_seniority})
(:Domain {id, name, sector, industry})
(:Industry {id, name, sector})
(:Company {id, name, size, industry, reputation_score})
(:Location {id, city, country, region})

// Aggregation Nodes (for analytics)
(:SkillTrend {skill_id, year_month, frequency, avg_salary})
(:CareerPath {from_occupation, to_occupation, avg_years, success_rate})
```

### Relationship Types

```cypher
// Resume → Experience
(:Resume)-[:HAS_EXPERIENCE {sequence_number}]->(:JobExperience)

// Experience → Occupations (MULTI-MAPPING - KEY FEATURE)
(:JobExperience)-[:MAPS_TO {
    confidence: 0.85,           // How well does this job match this occupation?
    primary: true,              // Is this the primary occupation?
    reasoning: "Job involves...",
    mapped_at: timestamp
}]->(:Occupation)

// Experience → Skills
(:JobExperience)-[:REQUIRES_SKILL {
    proficiency_level: 85,      // 0-100 scale
    years_used: 3.5,
    is_primary: true,           // Core skill for this role
    mentioned_count: 5,         // How many times mentioned in description
    extracted_from: "achievement_2"
}]->(:Skill)

// Experience → Technologies
(:JobExperience)-[:USES_TECHNOLOGY {
    proficiency: 80,
    version: "3.x",
    context: "Production system with 10K users"
}]->(:Technology)

// Experience → Activity Clusters
(:JobExperience)-[:PERFORMED_ACTIVITY {
    confidence: 0.92,           // How confident is the mapping?
    impact_level: "High",       // Low/Medium/High/Critical
    scope: "Team/Department/Company/Industry",
    metrics: "99.95% uptime, 10K requests/sec",
    achievement_text: "Full text of achievement"
}]->(:ActivityCluster)

// Occupation → Skills (from O*NET)
(:Occupation)-[:REQUIRES_SKILL {
    importance: 85,             // From O*NET importance scale
    level: 75,                  // From O*NET level scale
    is_core: true               // Top 10 skills for this occupation
}]->(:Skill)

// Occupation → Activity Clusters (from O*NET)
(:Occupation)-[:REQUIRES_ACTIVITY {
    importance: 90,             // From O*NET
    frequency: "Daily/Weekly/Monthly",
    typical_seniority: "Junior/Mid/Senior/Lead"
}]->(:ActivityCluster)

// Activity Clusters → O*NET Work Activities (granular mapping)
(:ActivityCluster)-[:CONTAINS_ONET_ACTIVITY {
    activity_name: "Design software applications",
    activity_id: "4.A.2.b.2",
    weight: 0.9                 // How central is this O*NET activity to the cluster?
}]->(:ONetActivity)

// Job Posting → Occupations
(:JobPosting)-[:SEEKING_OCCUPATION {
    confidence: 0.88,
    extracted_by: "LLM",
    keywords_matched: ["software", "developer", "java"]
}]->(:Occupation)

// Job Posting → Skills
(:JobPosting)-[:REQUIRES_SKILL {
    is_required: true,          // Required vs. nice-to-have
    proficiency_level: 80,
    mentioned_count: 3,
    context: "5+ years experience"
}]->(:Skill)

// Job Posting → Activity Clusters
(:JobPosting)-[:EXPECTS_ACTIVITY {
    confidence: 0.85,
    extracted_from: "responsibility_section"
}]->(:ActivityCluster)

// Skills → Skills (relationships)
(:Skill)-[:RELATED_TO {
    strength: 0.7,              // How related (co-occurrence)
    relationship_type: "prerequisite/complementary/alternative"
}]->(:Skill)

(:Skill)-[:BELONGS_TO_CATEGORY]->(:SkillCategory)

// Technology → Technology (ecosystems)
(:Technology)-[:PART_OF_STACK {
    stack_name: "MERN",
    typical_together: 0.85
}]->(:Technology)

// Occupation → Occupation (career paths)
(:Occupation)-[:COMMON_TRANSITION {
    avg_years: 3.5,
    transition_probability: 0.65,
    skill_overlap: 0.75,
    salary_increase_pct: 25
}]->(:Occupation)

(:Occupation)-[:RELATED_TO {
    similarity_score: 0.80,
    source: "O*NET API"
}]->(:Occupation)

// Cross-Resume Analytics
(:JobExperience)-[:SIMILAR_TO {
    similarity_score: 0.88,
    shared_skills: 12,
    shared_activities: 8,
    computed_at: timestamp
}]->(:JobExperience)

// Company → Industry
(:Company)-[:OPERATES_IN]->(:Industry)
(:JobExperience)-[:AT_COMPANY]->(:Company)
(:JobExperience)-[:IN_LOCATION]->(:Location)
(:JobExperience)-[:DOMAIN_KNOWLEDGE]->(:Domain)
```

---

## 3. Multi-Occupation Matching

### Problem: One Job → Multiple Occupations

**Example**: Java Developer at GFT Technologies
- Writing code → **Software Developer** (15-1252.00)
- Infrastructure/DevOps → **DevOps Engineer** (15-1299.08)
- Working with data → **Database Architect** (15-1243.00)
- Leading team initiatives → **Software Engineering Manager** (11-3021.00)

### Solution: Confidence-Based Multi-Mapping

```cypher
// Single job experience maps to multiple occupations
(:JobExperience {id: "exp-001"})-[:MAPS_TO {
    confidence: 0.92,
    primary: true,
    reasoning: "Primary role involves Spring Boot development and microservices",
    evidence_count: 8,          // 8 activities match this occupation
    skill_match_pct: 85
}]->(:Occupation {code: "15-1252.00", title: "Software Developers"})

(:JobExperience {id: "exp-001"})-[:MAPS_TO {
    confidence: 0.78,
    primary: false,
    reasoning: "Significant DevOps responsibilities: CI/CD, Kubernetes, Infrastructure",
    evidence_count: 5,
    skill_match_pct: 70
}]->(:Occupation {code: "15-1299.08", title: "DevOps Engineers"})

(:JobExperience {id: "exp-001"})-[:MAPS_TO {
    confidence: 0.65,
    primary: false,
    reasoning: "Works with BigQuery and Oracle, optimizes query performance",
    evidence_count: 3,
    skill_match_pct: 60
}]->(:Occupation {code: "15-1243.01", title: "Database Architects"})
```

### Multi-Occupation Mapping Algorithm

```java
public class OccupationMappingService {

    public List<OccupationMapping> mapJobToOccupations(JobExperience job) {
        // Step 1: Extract all skills and activities
        List<Skill> skills = extractSkills(job.getDescription());
        List<Achievement> achievements = extractAchievements(job.getDescription());

        // Step 2: Use LLM to identify candidate occupations
        String prompt = """
            Analyze this job experience and identify ALL relevant O*NET occupations.
            A single job can involve multiple occupations (e.g., developer + DevOps + team lead).

            Job Title: %s
            Company: %s
            Description & Achievements:
            %s

            Skills Used: %s

            Return JSON with all matching occupations (typically 2-4):
            {
              "occupations": [
                {
                  "soc_code": "15-1252.00",
                  "title": "Software Developers",
                  "confidence": 0.92,
                  "primary": true,
                  "reasoning": "Primary role involves software development...",
                  "matching_activities": ["Design software", "Modify programs"...],
                  "matching_skills": ["Java", "Spring Boot", "GCP"...]
                }
              ]
            }
            """.formatted(job.getTitle(), job.getCompany(),
                         job.getDescription(), skills);

        // Step 3: Parse LLM response
        OccupationMappingResponse response = callLLM(prompt);

        // Step 4: Validate against O*NET data
        for (OccupationMapping mapping : response.getOccupations()) {
            // Fetch O*NET data for this occupation
            ONetOccupation onetData = onetService.getOccupationDetails(mapping.getSocCode());

            // Calculate actual skill overlap
            double skillOverlap = calculateSkillOverlap(skills, onetData.getSkills());

            // Refine confidence score
            mapping.setSkillMatchPercentage(skillOverlap);
            mapping.setConfidence(refineConfidence(mapping.getConfidence(), skillOverlap));
        }

        return response.getOccupations();
    }

    private double calculateSkillOverlap(List<Skill> jobSkills, List<ONetSkill> occupationSkills) {
        int matches = 0;
        for (Skill jobSkill : jobSkills) {
            for (ONetSkill onetSkill : occupationSkills) {
                if (skillsMatch(jobSkill, onetSkill)) {
                    matches++;
                    break;
                }
            }
        }
        return (double) matches / Math.max(jobSkills.size(), occupationSkills.size());
    }
}
```

### Benefits of Multi-Occupation Mapping

1. **Realistic representation** of hybrid roles (common in modern tech)
2. **Better job matching** (match against any of candidate's occupations)
3. **Career flexibility** (show multiple career paths from single job)
4. **Skill portfolio analysis** (identify cross-functional skills)
5. **Market positioning** (understand candidate's breadth vs. depth)

---

## 4. Activity Clustering Strategy

### Approach: LLM-Based Semantic Clustering (No ML Libraries)

Instead of K-means clustering with embeddings, use LLM to map achievements to **predefined activity categories**.

### Activity Cluster Taxonomy

Based on O*NET DWAs (Detailed Work Activities), create ~50-100 semantic clusters:

```java
// Predefined activity clusters (curated from O*NET)
public enum ActivityClusterCategory {
    // Development Activities
    SOFTWARE_DESIGN_ARCHITECTURE("Software Design & Architecture"),
    CODE_DEVELOPMENT("Code Development & Implementation"),
    CODE_REVIEW_REFACTORING("Code Review & Refactoring"),
    API_DEVELOPMENT("API Development & Integration"),

    // Infrastructure & Operations
    SYSTEM_DEPLOYMENT("System Deployment & Release"),
    INFRASTRUCTURE_AUTOMATION("Infrastructure Automation"),
    PERFORMANCE_MONITORING("Performance Monitoring & Optimization"),
    INCIDENT_RESPONSE("Incident Response & Troubleshooting"),

    // Data & Analytics
    DATA_MODELING("Data Modeling & Schema Design"),
    QUERY_OPTIMIZATION("Query Optimization"),
    DATA_PIPELINE_DEVELOPMENT("Data Pipeline Development"),
    ANALYTICS_REPORTING("Analytics & Reporting"),

    // Testing & Quality
    UNIT_TESTING("Unit Testing"),
    INTEGRATION_TESTING("Integration Testing"),
    PERFORMANCE_TESTING("Performance Testing"),
    QUALITY_ASSURANCE("Quality Assurance & Code Quality"),

    // Collaboration & Leadership
    TECHNICAL_COLLABORATION("Technical Collaboration"),
    CODE_MENTORING("Code Mentoring & Knowledge Sharing"),
    TECHNICAL_DOCUMENTATION("Technical Documentation"),
    STAKEHOLDER_COMMUNICATION("Stakeholder Communication"),

    // Project Management
    PROJECT_PLANNING("Project Planning & Estimation"),
    AGILE_SCRUM_PRACTICES("Agile/Scrum Practices"),
    TECHNICAL_LEADERSHIP("Technical Leadership"),

    // Security
    SECURITY_IMPLEMENTATION("Security Implementation"),
    COMPLIANCE_STANDARDS("Compliance & Standards"),

    // Cloud & DevOps
    CLOUD_ARCHITECTURE("Cloud Architecture & Design"),
    CONTAINERIZATION("Containerization & Orchestration"),
    CICD_AUTOMATION("CI/CD Pipeline Automation"),
    MONITORING_OBSERVABILITY("Monitoring & Observability")
}
```

### Activity Cluster Structure

```java
@Node("ActivityCluster")
public class ActivityCluster {
    @Id
    private String id;

    private String name;                    // "Software Design & Architecture"
    private String category;                // "Development"
    private String description;

    // O*NET mapping
    private List<String> onetActivityIds;   // ["4.A.2.b.2", "4.A.2.a.4"]
    private List<String> onetActivityNames; // ["Design software applications", ...]

    // Keywords for matching
    private List<String> keywords;          // ["architecture", "design", "scalable", "system design"]

    // Typical seniority for this activity
    private String typicalSeniority;        // "Mid to Senior"

    // Examples of achievements that map to this cluster
    private List<String> exampleAchievements;
}
```

### LLM-Based Activity Mapping

```java
public class ActivityMappingService {

    public List<ActivityMapping> mapAchievementsToActivities(List<Achievement> achievements) {

        // Get all activity clusters
        List<ActivityCluster> clusters = activityClusterRepository.findAll();

        String clusterDefinitions = clusters.stream()
            .map(c -> String.format("- %s: %s (Keywords: %s)",
                c.getName(), c.getDescription(), String.join(", ", c.getKeywords())))
            .collect(Collectors.joining("\n"));

        String achievementsText = achievements.stream()
            .map(Achievement::getText)
            .collect(Collectors.joining("\n\n"));

        String prompt = """
            Map these job achievements to the most relevant activity clusters.
            Each achievement may map to multiple clusters.

            Achievements:
            %s

            Available Activity Clusters:
            %s

            Return JSON:
            {
              "mappings": [
                {
                  "achievement_index": 0,
                  "achievement_text": "Developed cloud-native microservices...",
                  "activity_clusters": [
                    {
                      "cluster_name": "Software Design & Architecture",
                      "confidence": 0.95,
                      "reasoning": "Achievement explicitly describes designing microservices architecture",
                      "evidence_keywords": ["architecture", "microservices", "scalable"]
                    },
                    {
                      "cluster_name": "Cloud Architecture & Design",
                      "confidence": 0.90,
                      "reasoning": "Uses GCP and cloud-native patterns",
                      "evidence_keywords": ["cloud-native", "GCP"]
                    }
                  ],
                  "impact_metrics": {
                    "quantified": true,
                    "metrics": "99.95% uptime, 10K+ requests",
                    "impact_level": "High"
                  }
                }
              ]
            }
            """.formatted(achievementsText, clusterDefinitions);

        return callLLMAndParse(prompt);
    }
}
```

### Activity Cluster → O*NET Mapping

Each cluster links to specific O*NET DWAs:

```cypher
// Example: Software Design & Architecture cluster
(:ActivityCluster {
    id: "AC_SOFTWARE_DESIGN",
    name: "Software Design & Architecture",
    category: "Development"
})-[:CONTAINS_ONET_ACTIVITY {
    activity_id: "4.A.2.b.2",
    activity_name: "Design software applications",
    weight: 1.0  // Primary activity
}]->(:ONetActivity)

(:ActivityCluster {id: "AC_SOFTWARE_DESIGN"})
-[:CONTAINS_ONET_ACTIVITY {
    activity_id: "4.A.2.a.2",
    activity_name: "Collaborate with others to determine design specifications",
    weight: 0.8  // Secondary activity
}]->(:ONetActivity)
```

Now when you query, you can:

```cypher
// Find all O*NET activities a candidate has performed
MATCH (exp:JobExperience {id: "exp-001"})
      -[:PERFORMED_ACTIVITY]->
      (cluster:ActivityCluster)
      -[:CONTAINS_ONET_ACTIVITY]->
      (onet:ONetActivity)
RETURN DISTINCT onet.activity_name
```

---

## 5. Use Cases & Scenarios

### Use Case 1: Multi-Occupation Career Profile

**Scenario**: Java Developer with DevOps and Data skills

**Graph Query**:
```cypher
// Get candidate's occupation profile with confidence scores
MATCH (resume:Resume {id: "user-123"})-[:HAS_EXPERIENCE]->(exp:JobExperience)
      -[mapping:MAPS_TO]->(occ:Occupation)
WITH occ,
     COUNT(exp) AS experience_count,
     AVG(mapping.confidence) AS avg_confidence,
     MAX(mapping.confidence) AS max_confidence,
     COLLECT(DISTINCT exp.title) AS job_titles
RETURN occ.title,
       occ.code,
       experience_count,
       ROUND(avg_confidence * 100, 1) AS confidence_pct,
       job_titles
ORDER BY avg_confidence DESC

// Result:
// Software Developers (15-1252.00) - 3 experiences - 88% confidence
// DevOps Engineers (15-1299.08) - 2 experiences - 72% confidence
// Database Architects (15-1243.01) - 1 experience - 61% confidence
```

**Business Value**: Candidate can be matched against jobs in any of these 3 occupations!

### Use Case 2: Activity-Based Job Matching

**Scenario**: Match candidate to job posting based on activities (not just skills)

**Graph Query**:
```cypher
// Find jobs where candidate has performed 70%+ of expected activities
MATCH (resume:Resume {id: "user-123"})-[:HAS_EXPERIENCE]->(exp)
      -[:PERFORMED_ACTIVITY]->(cluster:ActivityCluster)
WITH resume, COLLECT(DISTINCT cluster) AS candidate_activities

MATCH (job:JobPosting {id: "posting-456"})-[:EXPECTS_ACTIVITY]->(required_cluster:ActivityCluster)
WITH resume, job, candidate_activities, COLLECT(required_cluster) AS required_activities

WITH resume, job,
     candidate_activities,
     required_activities,
     [a IN candidate_activities WHERE a IN required_activities] AS matched_activities

RETURN job.title,
       job.company,
       SIZE(matched_activities) AS matched_count,
       SIZE(required_activities) AS total_required,
       (SIZE(matched_activities) * 100.0 / SIZE(required_activities)) AS match_percentage,
       [a IN required_activities WHERE NOT a IN candidate_activities | a.name] AS missing_activities
ORDER BY match_percentage DESC

// Result for "Senior Java Developer - Cloud Migration" posting:
// Match: 85% (17/20 activities matched)
// Missing: ["Kubernetes Cluster Management", "Service Mesh Configuration", "Cost Optimization"]
```

**Business Value**: More accurate job matching than keyword-based systems!

### Use Case 3: Skill Gap Analysis for Career Transition

**Scenario**: Developer wants to transition to DevOps Engineer

**Graph Query**:
```cypher
// Current occupation → Target occupation skill gap
MATCH (resume:Resume {id: "user-123"})-[:HAS_EXPERIENCE]->(exp)
      -[:REQUIRES_SKILL]->(has_skill:Skill)

MATCH (target:Occupation {code: "15-1299.08"})-[:REQUIRES_SKILL]->(req_skill:Skill)

// Skills already have
WITH COLLECT(DISTINCT has_skill) AS candidate_skills,
     COLLECT(DISTINCT req_skill) AS required_skills

// Missing skills
WITH [s IN required_skills WHERE NOT s IN candidate_skills] AS missing_skills,
     [s IN required_skills WHERE s IN candidate_skills] AS matched_skills,
     required_skills

UNWIND missing_skills AS skill
MATCH (skill)<-[req:REQUIRES_SKILL]-(target:Occupation {code: "15-1299.08"})

RETURN skill.name,
       skill.category,
       req.importance AS importance,
       req.level AS required_level,
       req.is_core AS is_core_skill
ORDER BY req.importance DESC

// Result:
// Kubernetes - Container Orchestration - Importance: 90 - Level: 80 - Core: true
// Terraform - Infrastructure as Code - Importance: 85 - Level: 75 - Core: true
// Prometheus/Grafana - Monitoring - Importance: 80 - Level: 70 - Core: false
// ...

// Readiness score
WITH SIZE(matched_skills) AS matched,
     SIZE(required_skills) AS total
RETURN (matched * 100.0 / total) AS readiness_percentage
// Result: 65% ready for DevOps role (need 5-7 more core skills)
```

### Use Case 4: Cross-Resume Skill Trends

**Scenario**: What skills are trending among Java developers in 2024?

**Graph Query**:
```cypher
// Find skill frequency among similar job experiences
MATCH (exp:JobExperience)-[:MAPS_TO]->(:Occupation {code: "15-1252.00"})
WHERE exp.start_date >= "2023-01" OR exp.end_date = "Present"

MATCH (exp)-[:REQUIRES_SKILL]->(skill:Skill)

WITH skill, COUNT(DISTINCT exp) AS usage_count, COUNT(DISTINCT exp.resume_id) AS resume_count

RETURN skill.name,
       skill.category,
       usage_count,
       resume_count,
       (resume_count * 100.0 / (SELECT COUNT(DISTINCT resume_id) FROM JobExperience WHERE ...)) AS adoption_rate
ORDER BY usage_count DESC
LIMIT 20

// Result (trending skills):
// Kubernetes - Container Orchestration - 45 jobs - 32 resumes - 68% adoption
// Docker - Containerization - 52 jobs - 38 resumes - 81% adoption
// Spring Boot - Framework - 48 jobs - 35 resumes - 75% adoption
// Microservices - Architecture - 42 jobs - 30 resumes - 64% adoption
```

**Business Value**: Market intelligence! Show candidates which skills are most in-demand.

### Use Case 5: Similar Candidate Discovery

**Scenario**: Find candidates with similar experience for benchmarking

**Graph Query**:
```cypher
// Find candidates with similar skills and activities
MATCH (my_resume:Resume {id: "user-123"})-[:HAS_EXPERIENCE]->(my_exp)
      -[:REQUIRES_SKILL]->(skill)

MATCH (other_resume:Resume)-[:HAS_EXPERIENCE]->(other_exp)
      -[:REQUIRES_SKILL]->(skill)

WHERE my_resume <> other_resume

WITH my_resume, other_resume,
     COUNT(DISTINCT skill) AS shared_skills,
     COLLECT(DISTINCT skill.name) AS common_skills

// Also check activity overlap
MATCH (my_resume)-[:HAS_EXPERIENCE]->()-[:PERFORMED_ACTIVITY]->(cluster)
      <-[:PERFORMED_ACTIVITY]-()<-[:HAS_EXPERIENCE]-(other_resume)

WITH my_resume, other_resume, shared_skills, common_skills,
     COUNT(DISTINCT cluster) AS shared_activities

WHERE shared_skills >= 8 AND shared_activities >= 5

RETURN other_resume.id,
       shared_skills,
       shared_activities,
       common_skills[0..10] AS top_common_skills
ORDER BY (shared_skills + shared_activities) DESC
LIMIT 10

// Result: Top 10 most similar candidates (for salary benchmarking, etc.)
```

### Use Case 6: Job Posting → Multiple Occupation Mapping

**Scenario**: Job posting for "Full Stack Engineer" matches multiple occupations

**Input**: Job Posting Description
```
We're seeking a Full Stack Engineer to:
- Build React frontends and Node.js backends
- Design PostgreSQL database schemas
- Deploy applications to AWS using Docker/K8s
- Participate in Agile sprints and code reviews
- Mentor junior developers
```

**LLM Mapping Result**:
```cypher
(:JobPosting {id: "post-789", title: "Full Stack Engineer"})
-[:SEEKING_OCCUPATION {confidence: 0.88, primary: true}]->
(:Occupation {code: "15-1254.00", title: "Web Developers"})

(:JobPosting {id: "post-789"})
-[:SEEKING_OCCUPATION {confidence: 0.75, primary: false}]->
(:Occupation {code: "15-1252.00", title: "Software Developers"})

(:JobPosting {id: "post-789"})
-[:SEEKING_OCCUPATION {confidence: 0.60, primary: false}]->
(:Occupation {code: "15-1243.01", title: "Database Architects"})
```

**Matching Query**:
```cypher
// Find candidates who match ANY of the job's occupations
MATCH (job:JobPosting {id: "post-789"})-[:SEEKING_OCCUPATION]->(occ:Occupation)
MATCH (exp:JobExperience)-[:MAPS_TO {primary: true}]->(occ)
MATCH (exp)<-[:HAS_EXPERIENCE]-(resume:Resume)

WITH resume, job, COUNT(DISTINCT occ) AS occupation_matches

// Also check skill overlap
MATCH (job)-[:REQUIRES_SKILL]->(req_skill)
MATCH (resume)-[:HAS_EXPERIENCE]->()-[:REQUIRES_SKILL]->(req_skill)

WITH resume, job, occupation_matches,
     COUNT(DISTINCT req_skill) AS skill_matches

RETURN resume.id,
       occupation_matches,
       skill_matches,
       (occupation_matches * 30 + skill_matches * 70) AS match_score
ORDER BY match_score DESC

// Candidates matched even if they don't have exact same job title!
```

---

## 6. Job Board Integration

### Architecture: Job Scraping → Graph Enrichment → Matching

```
┌─────────────────┐
│  Job Board API  │ (LinkedIn, Indeed, Greenhouse)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Scraper Service │ (Scheduled job, daily)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Job Enrichment │ (LLM extracts: occupations, skills, activities)
│     Service     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   Neo4j Graph   │ (JobPosting nodes + relationships)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Matching Engine │ (Find candidates for jobs, jobs for candidates)
└─────────────────┘
```

### Job Posting Enrichment Process

```java
@Service
public class JobPostingEnrichmentService {

    public void enrichJobPosting(JobPosting posting) {

        // Step 1: Extract occupations
        List<OccupationMapping> occupations = extractOccupations(posting);

        // Step 2: Extract required skills
        List<SkillRequirement> skills = extractSkills(posting);

        // Step 3: Extract expected activities
        List<ActivityMapping> activities = extractActivities(posting);

        // Step 4: Extract technologies
        List<Technology> technologies = extractTechnologies(posting);

        // Step 5: Store in graph
        neo4jService.createJobPostingNode(posting, occupations, skills, activities, technologies);
    }

    private List<OccupationMapping> extractOccupations(JobPosting posting) {
        String prompt = """
            Analyze this job posting and identify all relevant O*NET occupations.

            Job Title: %s
            Company: %s
            Description:
            %s

            Return JSON with matching occupations:
            {
              "occupations": [
                {
                  "soc_code": "15-1254.00",
                  "title": "Web Developers",
                  "confidence": 0.90,
                  "primary": true,
                  "reasoning": "Job primarily focuses on web development..."
                }
              ]
            }
            """.formatted(posting.getTitle(), posting.getCompany(), posting.getDescription());

        return callLLMAndParse(prompt);
    }

    private List<SkillRequirement> extractSkills(JobPosting posting) {
        String prompt = """
            Extract all required and preferred skills from this job posting.
            Categorize each skill and determine if it's required or nice-to-have.

            Job Description:
            %s

            Return JSON:
            {
              "skills": [
                {
                  "name": "Java",
                  "category": "Programming Language",
                  "is_required": true,
                  "proficiency_level": 80,
                  "years_experience": 5,
                  "context": "5+ years of Java development experience required"
                }
              ]
            }
            """.formatted(posting.getDescription());

        return callLLMAndParse(prompt);
    }

    private List<ActivityMapping> extractActivities(JobPosting posting) {
        // Map job responsibilities to activity clusters
        List<String> responsibilities = extractResponsibilities(posting);

        return activityMappingService.mapResponsibilitiesToActivities(responsibilities);
    }
}
```

### Job Matching Queries

#### Query 1: Find Best Jobs for Candidate

```cypher
// Multi-factor job matching
MATCH (resume:Resume {id: "user-123"})-[:HAS_EXPERIENCE]->(exp)

// Occupation match
MATCH (exp)-[:MAPS_TO]->(occ:Occupation)<-[:SEEKING_OCCUPATION]-(job:JobPosting)

// Skill match
MATCH (exp)-[:REQUIRES_SKILL]->(skill:Skill)<-[:REQUIRES_SKILL]-(job)

// Activity match
MATCH (exp)-[:PERFORMED_ACTIVITY]->(cluster:ActivityCluster)<-[:EXPECTS_ACTIVITY]-(job)

// Location preference (optional)
OPTIONAL MATCH (resume)-[:PREFERS_LOCATION]->(loc:Location)<-[:IN_LOCATION]-(job)

WITH job, resume,
     COUNT(DISTINCT occ) AS occupation_matches,
     COUNT(DISTINCT skill) AS skill_matches,
     COUNT(DISTINCT cluster) AS activity_matches,
     CASE WHEN loc IS NOT NULL THEN 1.0 ELSE 0.5 END AS location_score

// Get total requirements for percentage calculation
MATCH (job)-[:REQUIRES_SKILL]->(all_skills)
MATCH (job)-[:EXPECTS_ACTIVITY]->(all_activities)

WITH job, resume,
     occupation_matches,
     skill_matches,
     activity_matches,
     location_score,
     COUNT(DISTINCT all_skills) AS total_skills,
     COUNT(DISTINCT all_activities) AS total_activities

WITH job,
     (occupation_matches * 20) AS occ_score,
     ((skill_matches * 100.0 / total_skills) * 50) AS skill_score,
     ((activity_matches * 100.0 / total_activities) * 25) AS activity_score,
     (location_score * 5) AS loc_score,
     skill_matches,
     total_skills,
     activity_matches,
     total_activities

WITH job,
     (occ_score + skill_score + activity_score + loc_score) AS overall_match_score,
     skill_matches,
     total_skills,
     activity_matches,
     total_activities

RETURN job.title,
       job.company,
       job.location,
       job.salary_range,
       job.posted_date,
       ROUND(overall_match_score, 1) AS match_score,
       skill_matches || "/" || total_skills AS skill_coverage,
       activity_matches || "/" || total_activities AS activity_coverage
ORDER BY overall_match_score DESC
LIMIT 20

// Result: Top 20 jobs ranked by multi-factor match score
```

#### Query 2: Find Candidates for Job Posting

```cypher
// Recruiter view: Find best candidates for a job
MATCH (job:JobPosting {id: "post-789"})-[:REQUIRES_SKILL]->(req_skill:Skill)
MATCH (resume:Resume)-[:HAS_EXPERIENCE]->()-[:REQUIRES_SKILL]->(req_skill)

WITH job, resume, COUNT(DISTINCT req_skill) AS skill_matches

// Activity match
MATCH (job)-[:EXPECTS_ACTIVITY]->(cluster:ActivityCluster)
OPTIONAL MATCH (resume)-[:HAS_EXPERIENCE]->()-[:PERFORMED_ACTIVITY]->(cluster)

WITH job, resume, skill_matches,
     COUNT(DISTINCT cluster) AS activity_matches

// Occupation match
MATCH (job)-[:SEEKING_OCCUPATION]->(occ:Occupation)
OPTIONAL MATCH (resume)-[:HAS_EXPERIENCE]->()-[:MAPS_TO]->(occ)

WITH job, resume, skill_matches, activity_matches,
     COUNT(DISTINCT occ) AS occupation_matches

// Calculate total requirements
MATCH (job)-[:REQUIRES_SKILL]->(all_skills)
MATCH (job)-[:EXPECTS_ACTIVITY]->(all_activities)

WITH resume,
     skill_matches,
     COUNT(DISTINCT all_skills) AS total_skills,
     activity_matches,
     COUNT(DISTINCT all_activities) AS total_activities,
     occupation_matches

RETURN resume.id,
       (skill_matches * 100.0 / total_skills) AS skill_match_pct,
       (activity_matches * 100.0 / total_activities) AS activity_match_pct,
       occupation_matches,
       ((skill_matches * 100.0 / total_skills) * 0.6 +
        (activity_matches * 100.0 / total_activities) * 0.3 +
        (occupation_matches * 10)) AS overall_score
ORDER BY overall_score DESC
LIMIT 50

// Result: Top 50 candidates for this job posting
```

### Job Board Data Model

```cypher
(:JobPosting {
    id: "linkedin-123456",
    source: "LinkedIn",
    url: "https://linkedin.com/jobs/...",
    title: "Senior Full Stack Engineer",
    company: "Stripe",
    location: "London, UK",
    remote_policy: "Hybrid",
    salary_range: "£80K-£120K",
    posted_date: "2025-09-28",
    expires_date: "2025-11-28",
    description: "...",
    requirements: "...",
    responsibilities: "...",
    scraped_at: "2025-09-29T10:00:00Z",
    last_updated: "2025-09-29T10:00:00Z",
    status: "Active"
})
```

---

## 7. Cross-Resume Analytics

### Use Case: Market Intelligence & Benchmarking

```cypher
// Aggregation nodes for analytics
(:SkillTrend {
    skill_id: "skill-java",
    year_month: "2025-09",
    frequency: 245,              // Appeared in 245 job experiences this month
    avg_proficiency: 78,
    avg_years_experience: 4.2,
    salary_correlation: 0.65,    // Correlation with higher salaries
    growth_rate: 12.5            // % growth vs. previous month
})

(:CareerPath {
    from_occupation: "15-1252.00",
    to_occupation: "11-3021.00",  // Software Dev → Engineering Manager
    transition_count: 47,         // 47 people made this transition
    avg_years: 5.8,               // Average years before transition
    success_rate: 0.72,           // % who stayed in new role 2+ years
    common_skills: ["Java", "Leadership", "Agile"],
    salary_increase_pct: 35
})
```

### Analytics Queries

#### Query 1: Salary Benchmark by Skills

```cypher
// What skills correlate with higher salaries?
MATCH (exp:JobExperience)-[:REQUIRES_SKILL]->(skill:Skill)
WHERE exp.salary IS NOT NULL

WITH skill,
     AVG(exp.salary) AS avg_salary,
     COUNT(exp) AS job_count,
     STDDEV(exp.salary) AS salary_std_dev

WHERE job_count >= 10  // Only skills with enough data

RETURN skill.name,
       skill.category,
       ROUND(avg_salary, 0) AS avg_salary,
       job_count,
       ROUND(salary_std_dev, 0) AS std_dev
ORDER BY avg_salary DESC
LIMIT 30

// Result: Top paying skills
// Kubernetes - £95K avg (142 jobs)
// AWS - £92K avg (218 jobs)
// Microservices - £88K avg (176 jobs)
```

#### Query 2: Common Career Progressions

```cypher
// Discover typical career paths
MATCH path = (early:JobExperience)-[:NEXT_JOB*1..3]->(later:JobExperience)
WHERE early.start_date < later.start_date

MATCH (early)-[:MAPS_TO]->(early_occ:Occupation)
MATCH (later)-[:MAPS_TO]->(later_occ:Occupation)

WHERE early_occ <> later_occ

WITH early_occ, later_occ,
     COUNT(*) AS transition_count,
     AVG(duration.between(early.start_date, later.start_date).years) AS avg_years

WHERE transition_count >= 5

RETURN early_occ.title AS from_role,
       later_occ.title AS to_role,
       transition_count,
       ROUND(avg_years, 1) AS avg_years_to_transition
ORDER BY transition_count DESC

// Result: Most common transitions
// Software Developer → Senior Software Developer (234 transitions, 2.3 years)
// Senior Software Developer → Engineering Manager (89 transitions, 4.1 years)
// DevOps Engineer → Cloud Architect (56 transitions, 3.8 years)
```

#### Query 3: Skill Co-occurrence Network

```cypher
// Which skills are typically learned together?
MATCH (exp:JobExperience)-[:REQUIRES_SKILL]->(skill1:Skill)
MATCH (exp)-[:REQUIRES_SKILL]->(skill2:Skill)
WHERE skill1 <> skill2

WITH skill1, skill2, COUNT(DISTINCT exp) AS co_occurrence_count

WHERE co_occurrence_count >= 20

// Create RELATED_TO relationships
CREATE (skill1)-[:RELATED_TO {
    strength: co_occurrence_count,
    relationship_type: "complementary"
}]->(skill2)

// Query the network
MATCH (skill:Skill {name: "Spring Boot"})-[r:RELATED_TO]-(related:Skill)
RETURN related.name, r.strength
ORDER BY r.strength DESC
LIMIT 10

// Result: Skills that go with Spring Boot
// Java - 450 co-occurrences
// MySQL - 280
// Docker - 245
// Kubernetes - 198
```

---

## 8. Implementation Phases

### Phase 1: Enhanced Graph Model (Weeks 1-2)

**Deliverables**:
- [ ] Update Neo4j schema with new node types
- [ ] Multi-occupation mapping for JobExperience
- [ ] Skill extraction service
- [ ] Activity cluster taxonomy (50-100 clusters)
- [ ] LLM-based activity mapping service

**Technical Tasks**:
```java
// 1. Create new domain models
@Node("Skill")
@Node("Technology")
@Node("ActivityCluster")
@Node("Domain")

// 2. Update JobExperience with multi-mapping
public void mapJobToOccupations(JobExperience job) {
    List<OccupationMapping> mappings = occupationMappingService.map(job);
    for (OccupationMapping mapping : mappings) {
        createMapsToRelationship(job, mapping);
    }
}

// 3. Skill extraction
public List<Skill> extractSkills(String jobDescription) {
    // LLM prompt to extract skills
}

// 4. Activity mapping
public List<ActivityMapping> mapToActivities(List<Achievement> achievements) {
    // LLM prompt to map to activity clusters
}
```

### Phase 2: Graph Enrichment for Existing Data (Weeks 3-4)

**Deliverables**:
- [ ] Backfill skills for existing resumes
- [ ] Backfill activity mappings for existing jobs
- [ ] Multi-occupation mapping for existing jobs
- [ ] O*NET activity cluster creation

**Migration Strategy**:
```java
@Service
public class GraphMigrationService {

    public void enrichExistingResumes() {
        List<Resume> resumes = resumeRepository.findAll();

        for (Resume resume : resumes) {
            for (JobExperience exp : resume.getExperiences()) {
                // Extract and link skills
                List<Skill> skills = skillExtractionService.extract(exp);
                linkSkillsToJob(exp, skills);

                // Map to multiple occupations
                List<OccupationMapping> occupations = occupationMappingService.map(exp);
                linkOccupationsToJob(exp, occupations);

                // Map activities
                List<ActivityMapping> activities = activityMappingService.map(exp);
                linkActivitiesToJob(exp, activities);
            }
        }
    }
}
```

### Phase 3: Job Posting Integration (Weeks 5-6)

**Deliverables**:
- [ ] Job scraper service (LinkedIn, Indeed APIs)
- [ ] Job posting enrichment service
- [ ] Job-to-occupation mapping
- [ ] Job-to-skill extraction
- [ ] Job matching algorithm

**Architecture**:
```java
@Service
public class JobScraperService {

    @Scheduled(cron = "0 0 2 * * *")  // Run daily at 2 AM
    public void scrapeJobPostings() {
        List<JobPosting> newJobs = linkedInClient.fetchJobs();

        for (JobPosting job : newJobs) {
            // Enrich job posting
            jobEnrichmentService.enrich(job);

            // Store in Neo4j
            jobPostingRepository.save(job);
        }
    }
}

@Service
public class JobMatchingService {

    public List<JobMatch> findJobsForCandidate(String resumeId) {
        // Neo4j query to match candidate → jobs
        return neo4jQuery.matchCandidateToJobs(resumeId);
    }

    public List<CandidateMatch> findCandidatesForJob(String jobPostingId) {
        // Neo4j query to match job → candidates
        return neo4jQuery.matchJobToCandidates(jobPostingId);
    }
}
```

### Phase 4: Analytics & Insights (Weeks 7-8)

**Deliverables**:
- [ ] Skill trend analysis
- [ ] Salary benchmarking
- [ ] Career path discovery
- [ ] Skill gap visualization
- [ ] Market intelligence dashboard

**Queries**:
```java
@Service
public class AnalyticsService {

    public SkillTrendReport getSkillTrends(String skillId, int months) {
        // Aggregate skill usage over time
    }

    public SalaryBenchmark getSalaryBenchmark(List<String> skills) {
        // Calculate salary ranges for skill combinations
    }

    public List<CareerPath> discoverCareerPaths(String currentOccupation) {
        // Find common transitions from current occupation
    }
}
```

### Phase 5: Frontend Integration (Weeks 9-10)

**Deliverables**:
- [ ] Job match results page
- [ ] Skill gap visualization
- [ ] Career path explorer
- [ ] Market insights dashboard
- [ ] Job board integration UI

---

## 9. Technical Specifications

### LLM Prompts

#### Occupation Mapping Prompt
```
You are an expert career counselor and O*NET occupation specialist.

Analyze this job experience and map it to ALL relevant O*NET occupations.
Modern jobs often span multiple occupations (e.g., DevOps = Developer + Sysadmin).

Job Title: {title}
Company: {company}
Description: {description}
Skills Used: {skills}

Return JSON (typically 2-4 occupations):
{
  "occupations": [
    {
      "soc_code": "15-1252.00",
      "title": "Software Developers",
      "confidence": 0.92,
      "primary": true,
      "reasoning": "Primary role involves software development with Spring Boot and microservices",
      "evidence": ["Developed microservices", "Spring Boot expertise", "Code review"],
      "matching_skills": ["Java", "Spring Boot", "GCP"],
      "matching_activities": ["Design software", "Modify programs"]
    }
  ]
}

Confidence scoring:
- 0.90-1.00: Strong match (core responsibilities align)
- 0.70-0.89: Good match (significant portion of role)
- 0.50-0.69: Moderate match (some responsibilities align)
- <0.50: Weak match (tangential)
```

#### Skill Extraction Prompt
```
Extract all technical and soft skills from this job description.

Job Description: {description}

For each skill:
1. Categorize (Programming Language, Framework, Cloud Platform, Database, DevOps Tool, Soft Skill, etc.)
2. Estimate proficiency level (0-100) based on context
3. Note evidence (where mentioned)

Return JSON:
{
  "skills": [
    {
      "name": "Java",
      "category": "Programming Language",
      "subcategory": "Backend",
      "proficiency_level": 90,
      "years_experience": 5,
      "is_core_skill": true,
      "evidence": "5+ years Java development, Spring Boot expertise",
      "mentioned_count": 3
    }
  ]
}

Important:
- Normalize skill names (e.g., "React.js" → "React")
- Include both explicit skills (mentioned) and implicit skills (inferred from achievements)
- Separate technical vs. soft skills
```

#### Activity Mapping Prompt
```
Map these job achievements to predefined activity clusters.

Achievements:
{achievements}

Activity Clusters:
{cluster_definitions}

Each achievement may map to multiple clusters.
Focus on WHAT the person actually DID, not just technologies used.

Return JSON:
{
  "mappings": [
    {
      "achievement_text": "Developed cloud-native microservices...",
      "activity_clusters": [
        {
          "cluster_name": "Software Design & Architecture",
          "confidence": 0.95,
          "reasoning": "Achievement describes designing system architecture",
          "evidence_keywords": ["architecture", "microservices", "scalable"],
          "impact_level": "High",
          "scope": "System-level"
        }
      ],
      "metrics_extracted": {
        "quantified": true,
        "values": ["99.95% uptime", "10K+ requests/sec"],
        "impact": "Millions of users"
      }
    }
  ]
}
```

### Neo4j Indexes

```cypher
// Performance optimization indexes
CREATE INDEX occupation_code IF NOT EXISTS FOR (o:Occupation) ON (o.code);
CREATE INDEX skill_name IF NOT EXISTS FOR (s:Skill) ON (s.name);
CREATE INDEX job_posting_status IF NOT EXISTS FOR (j:JobPosting) ON (j.status);
CREATE INDEX resume_user IF NOT EXISTS FOR (r:Resume) ON (r.user_id);
CREATE FULLTEXT INDEX skill_search IF NOT EXISTS FOR (s:Skill) ON EACH [s.name, s.category];
CREATE FULLTEXT INDEX job_search IF NOT EXISTS FOR (j:JobPosting) ON EACH [j.title, j.description];
```

### API Endpoints

```java
// Occupation mapping
POST /api/resumes/{resumeId}/experiences/{expId}/map-occupations
GET  /api/resumes/{resumeId}/experiences/{expId}/occupations

// Skill management
POST /api/resumes/{resumeId}/experiences/{expId}/extract-skills
GET  /api/resumes/{resumeId}/skills

// Activity mapping
POST /api/resumes/{resumeId}/experiences/{expId}/map-activities
GET  /api/resumes/{resumeId}/activities

// Job matching
GET  /api/resumes/{resumeId}/job-matches?limit=20
GET  /api/job-postings/{jobId}/candidate-matches?limit=50

// Analytics
GET  /api/analytics/skill-trends?skill={skillId}&months=12
GET  /api/analytics/salary-benchmark?skills=Java,Spring,AWS
GET  /api/analytics/career-paths?occupation={socCode}
```

---

## 10. Future Extensions

### Extension 1: Real-time Skill Recommendations

```cypher
// As user types resume, suggest skills they might have missed
MATCH (exp:JobExperience {description: CONTAINS "Spring Boot"})
      -[:REQUIRES_SKILL]->(skill:Skill)
WHERE NOT (current_exp:JobExperience {id: "exp-001"})-[:REQUIRES_SKILL]->(skill)
RETURN skill.name, COUNT(*) AS frequency
ORDER BY frequency DESC
// "Based on similar jobs, you might also have: Docker, Kubernetes, MySQL"
```

### Extension 2: Interview Preparation

```cypher
// What questions might be asked based on occupation + seniority?
MATCH (occ:Occupation {code: "15-1252.00"})-[:REQUIRES_ACTIVITY]->(cluster)
WHERE cluster.typical_seniority CONTAINS "Senior"
RETURN cluster.name AS activity,
       cluster.typical_interview_questions AS questions
// "For Senior Software Developer, expect questions on: System Design, Scalability..."
```

### Extension 3: Resume Optimization

```cypher
// Suggest improvements to increase match for target occupation
MATCH (target:Occupation {code: "15-1252.00"})-[:REQUIRES_SKILL]->(skill)
WHERE NOT (exp:JobExperience {id: "exp-001"})-[:REQUIRES_SKILL]->(skill)
  AND skill.is_core = true
RETURN "Consider adding: " + skill.name + " (core skill for this role)"
```

### Extension 4: Competitive Analysis

```cypher
// How does candidate compare to others for same role?
MATCH (my_resume:Resume {id: "user-123"})-[:HAS_EXPERIENCE]->(my_exp)
      -[:REQUIRES_SKILL]->(skill)
WITH my_resume, COUNT(DISTINCT skill) AS my_skill_count

MATCH (other:Resume)-[:HAS_EXPERIENCE]->()-[:REQUIRES_SKILL]->(skill)
WITH my_resume, my_skill_count, AVG(COUNT(DISTINCT skill)) AS avg_skill_count

RETURN CASE
  WHEN my_skill_count > avg_skill_count THEN "Above average"
  WHEN my_skill_count < avg_skill_count THEN "Below average"
  ELSE "Average"
END AS competitive_position
```

### Extension 5: Salary Prediction

```cypher
// Predict salary based on skills, occupation, location, experience
MATCH (exp:JobExperience)-[:REQUIRES_SKILL]->(skill)
WHERE exp.salary IS NOT NULL

WITH skill, AVG(exp.salary) AS skill_value

MATCH (target_exp:JobExperience {id: "exp-001"})-[:REQUIRES_SKILL]->(skill)

RETURN SUM(skill_value) / COUNT(skill) AS predicted_salary
// Use ML model for better predictions in production
```

---

## Summary

This architecture transforms Resume Buddy from a simple resume parser into an **intelligent career intelligence platform**:

1. **Multi-occupation matching** captures reality of hybrid roles
2. **Activity-based clustering** enables semantic matching beyond keywords
3. **Rich skill graphs** reveal career paths and skill gaps
4. **Job board integration** creates two-sided marketplace
5. **Cross-resume analytics** provide market intelligence
6. **LLM-based approach** avoids complex ML library dependencies

The graph structure enables queries impossible with relational databases, powering features like career pathfinding, skill gap analysis, and intelligent job matching.

**Next Step**: Review this plan and decide which phase to implement first!
