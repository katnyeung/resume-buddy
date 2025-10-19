export interface Resume {
  id: string;
  filename: string;
  contentType: string;
  fileSize: number;
  status: string;
  atsReport?: string; // JSON string containing ATS quality report
  atsScore?: number;
  atsAnalyzedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ResumeLine {
  id: string;
  lineNumber: number;
  content: string;
  // AI Analysis fields
  sectionType?: string;  // CONTACT, EXPERIENCE, EDUCATION, SKILLS, etc.
  groupId?: number;      // Groups related lines (e.g., same job entry)
  groupType?: string;    // JOB, PROJECT, EDUCATION_ITEM, SKILL_CATEGORY, etc.
  analysisNotes?: string; // AI findings and notes for this line
  analyzedAt?: string;   // When this line was last analyzed
  createdAt: string;
  updatedAt: string;
}

export interface ResumeLineUpdateDto {
  lineNumber: number;
  content: string;
}

export interface BatchUpdateResponse {
  success: boolean;
  message: string;
  updatedCount: number;
  updatedLines: ResumeLine[];
}

// AI Analysis types
export interface AnalysisGroup {
  groupId: number;
  groupType: string;
  sectionType: string;
  lines: ResumeLine[];
  startLine: number;
  endLine: number;
}

export interface ATSReport {
  score: number;
  summary: string;
  strengths: string[];
  improvements: string[];
  contentBalance: 'sparse' | 'balanced' | 'verbose';
  readability: 'poor' | 'fair' | 'good' | 'excellent';
  sectionOrganization: 'poor' | 'fair' | 'good' | 'excellent';
}

export interface AnalysisResultDto {
  resumeId: string;
  analyzedAt: string;
  totalLines: number;
  analyzedLines: number;
  lineAnalyses: LineAnalysisDto[];
  atsReport?: ATSReport;
}

export interface LineAnalysisDto {
  lineNumber: number;
  sectionType: string;
  groupId: number;
  groupType: string;
  analysisNotes: string;
}

// Structured Analysis types
export interface ResumeAnalysisDto {
  id: string;
  resumeId: string;
  name?: string;
  email?: string;
  phone?: string;
  linkedinUrl?: string;
  githubUrl?: string;
  websiteUrl?: string;
  summary?: string;
  experiences: ExperienceDto[];
  skills: SkillDto[];
  educations: EducationDto[];
  certifications: CertificationDto[];
  projects: ProjectDto[];
  createdAt: string;
  updatedAt: string;
}

export interface ExperienceDto {
  id: string;
  jobTitle?: string;
  companyName?: string;
  startDate?: string;
  endDate?: string;
  description?: string;
  isAnalyzed?: boolean;
  analyzedAt?: string;
  analysisId?: string;  // Latest job analysis ID
}

export interface SkillDto {
  id: string;
  skillName?: string;
  category?: string;
}

export interface EducationDto {
  id: string;
  degree?: string;
  institution?: string;
  graduationDate?: string;
  description?: string;
}

export interface CertificationDto {
  id: string;
  certificationName?: string;
  issuingOrganization?: string;
  issueDate?: string;
  credentialId?: string;
}

export interface ProjectDto {
  id: string;
  projectName?: string;
  description?: string;
  technologiesUsed?: string;
  projectUrl?: string;
}

// Job Analysis types
export interface JobAnalysisResult {
  id: string;
  resumeId: string;
  experienceId: string;
  normalizedTitle: string;
  primarySocCode: string;
  seniorityLevel: string;
  impactScore: number;
  technicalDepthScore: number;
  leadershipScore: number;
  overallScore: number;
  recruiterSummary: string;
  workActivities?: WorkActivity[];
  keyStrengths: string[];
  improvementAreas: string[];
  createdAt: string;
}

export interface WorkActivity {
  id: string;
  name: string;
  category: string;
  importance: number;
}

// Job Matching types
export interface JobMatchResult {
  matchId: string;
  profileId: string;
  listingId: string;
  similarityScore: number;
  matchLevel: 'STRONG' | 'GOOD' | 'MODERATE';
  title: string;
  company: string;
  location: string;
  description: string;
  url: string;
  salaryRange?: string;
  postedDate: string;
  fetchedAt: string; // When we fetched this job from the source
  matchedSkills: string[];
  missingSkills: string[];
  skillMatchPercentage: number;
  weightedSkillScore: number;
  isSaved: number; // 0-3 star rating (0 = not saved, 1-3 = saved with priority)
  isApplied: boolean;
  isRedflag: boolean;
  flaggedAt?: string;
  matchedNegativeKeywords?: string[]; // Deal-breaker keywords found in this job
}

export interface JobMatchingResultsResponse {
  profileId: string;
  totalMatches: number;
  profileSummary: string;
  matches: JobMatchResult[];
}

// Skill Drilldown types (Neo4j graph-based job discovery)
export interface SkillCooccurrence {
  skillName: string;
  jobCount: number;
}

export interface SkillDrilldownResponse {
  totalJobs: number;
  jobIds: string[];
  dateDistribution: Record<string, number>;
  relatedSkills: SkillCooccurrence[];
}

export interface JobListing {
  id: string;
  source: string;
  title: string;
  company: string;
  location: string;
  description: string;
  url: string;
  salaryRange?: string;
  contractType?: string;
  postedDate?: string;
  expiresDate?: string;
  fetchedAt: string;
  extractedSkills?: string; // JSON string
}

// Task Demonstration Analysis (Phase 11.3) - Task-first credibility view
export interface TaskDemonstration {
  taskId: string;
  taskName: string;
  importance: number;
  taskCategory: string;
  coverageStrength: 'STRONG' | 'MODERATE' | 'WEAK' | 'NONE';
  skillCount: number;
  lineCount: number;
  skills: SkillCoveringTask[];
  lines: TaskResumeLineDto[];
}

export interface SkillCoveringTask {
  skillName: string;
  category: string;
  isPrimary: boolean;
  lineCount: number; // how many lines demonstrate this task via this skill
}

export interface TaskResumeLineDto {
  lineId: string;
  sequence: number;
  text: string;
}

// Skill Train types
export interface SkillTrainDto {
  userSkills: string[];
  marketSkills: { [key: string]: number };
  jobCountBySkill: { [key: string]: number };
}

export interface SkillPathRequest {
  skills: string[];
  maxRelatedSkills?: number;
}

export interface SkillPathResponse {
  selectedSkills: string[];
  jobCount: number;
  relatedSkills: { [key: string]: number };
}