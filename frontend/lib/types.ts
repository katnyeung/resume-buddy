export interface Resume {
  id: string;
  filename: string;
  contentType: string;
  fileSize: number;
  status: string;
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

export interface AnalysisResultDto {
  resumeId: string;
  analyzedAt: string;
  totalLines: number;
  analyzedLines: number;
  lineAnalyses: LineAnalysisDto[];
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