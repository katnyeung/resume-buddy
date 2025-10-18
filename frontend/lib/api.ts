import axios from 'axios';
import { Resume, ResumeLine, ResumeLineUpdateDto, BatchUpdateResponse, ResumeAnalysisDto } from './types';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add request interceptor to include auth token
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('authToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Resume Management
export const uploadResume = async (file: File): Promise<Resume> => {
  const formData = new FormData();
  formData.append('file', file);

  const response = await apiClient.post<Resume>('/resumes/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });

  return response.data;
};

export const parseResume = async (id: string): Promise<Resume> => {
  const response = await apiClient.post<Resume>(`/resumes/${id}/parse`);
  return response.data;
};

export const getResume = async (id: string): Promise<Resume> => {
  const response = await apiClient.get<Resume>(`/resumes/${id}`);
  return response.data;
};

export const listResumes = async (): Promise<Resume[]> => {
  const response = await apiClient.get<Resume[]>('/resumes');
  return response.data;
};

export const deleteResume = async (id: string): Promise<void> => {
  await apiClient.delete(`/resumes/${id}`);
};

// Resume Line Management
export const getResumeLines = async (id: string): Promise<ResumeLine[]> => {
  const response = await apiClient.get<ResumeLine[]>(`/resumes/${id}/lines`);
  return response.data;
};

export const processResumeLines = async (id: string): Promise<{ success: boolean; lineCount: number }> => {
  const response = await apiClient.post(`/resumes/${id}/process-lines`);
  return response.data;
};

export const updateLine = async (
  id: string,
  lineNumber: number,
  content: string
): Promise<ResumeLine> => {
  const response = await apiClient.put<ResumeLine>(
    `/resumes/${id}/lines/${lineNumber}`,
    { content }
  );
  return response.data;
};

export const batchUpdateLines = async (
  id: string,
  updates: ResumeLineUpdateDto[]
): Promise<BatchUpdateResponse> => {
  const response = await apiClient.put<BatchUpdateResponse>(
    `/resumes/${id}/lines/batch`,
    updates
  );
  return response.data;
};

export const getLineCount = async (id: string): Promise<number> => {
  const response = await apiClient.get<{ lineCount: number }>(`/resumes/${id}/lines/count`);
  return response.data.lineCount;
};

// Editor State Management
export const saveEditorState = async (id: string, editorState: string): Promise<Resume> => {
  const response = await apiClient.put<Resume>(
    `/resumes/${id}/editor-state`,
    editorState,
    {
      headers: {
        'Content-Type': 'application/json',
      },
    }
  );
  return response.data;
};

export const getEditorState = async (id: string): Promise<string | null> => {
  const response = await apiClient.get<string>(`/resumes/${id}/editor-state`, {
    transformResponse: [(data) => data], // Prevent Axios from parsing JSON
  });

  // response.data is now the raw string from backend
  if (response.data === 'null' || !response.data) {
    return null;
  }

  // If it's already a JSON string, return it as-is
  return response.data;
};

// AI Analysis (Synchronous - Legacy)
export const analyzeResume = async (id: string): Promise<any> => {
  const response = await apiClient.post(`/resumes/${id}/analyze`);
  return response.data;
};

// AI Analysis (Async with Job Queue)
export const analyzeResumeAsync = async (id: string, userId: string = 'default_user', priority?: number): Promise<any> => {
  const response = await apiClient.post(`/jobs/resumes/${id}/analyze/async`, null, {
    params: { priority },
    headers: {
      'X-User-Id': userId
    }
  });
  return response.data;
};

export const getAnalysisStatus = async (id: string): Promise<{ resumeId: string; isAnalyzed: boolean }> => {
  const response = await apiClient.get(`/resumes/${id}/analysis-status`);
  return response.data;
};

export const getStructuredAnalysis = async (id: string): Promise<ResumeAnalysisDto | null> => {
  try {
    const response = await apiClient.get<ResumeAnalysisDto>(`/resumes/${id}/structured-analysis`);
    return response.data;
  } catch (error) {
    return null;
  }
};

export const checkAnalysisExists = async (id: string): Promise<boolean> => {
  const response = await apiClient.get<boolean>(`/resumes/${id}/analysis-exists`);
  return response.data;
};

// Job Analysis (Synchronous - Legacy)
export const analyzeJob = async (resumeId: string, experienceId: string): Promise<any> => {
  const response = await apiClient.post(`/resumes/${resumeId}/experiences/${experienceId}/analyze`);
  return response.data;
};

// Job Analysis (Async with Job Queue)
export const analyzeJobAsync = async (resumeId: string, experienceId: string, userId: string = 'default_user', priority?: number): Promise<any> => {
  const response = await apiClient.post(`/jobs/resumes/${resumeId}/experiences/${experienceId}/analyze/async`, null, {
    params: { priority },
    headers: {
      'X-User-Id': userId
    }
  });
  return response.data;
};

export const getJobAnalysis = async (resumeId: string, experienceId: string): Promise<any> => {
  try {
    const response = await apiClient.get(`/resumes/${resumeId}/experiences/${experienceId}/analysis`);
    return response.data;
  } catch (error) {
    return null;
  }
};

export const checkJobAnalysisExists = async (resumeId: string, experienceId: string): Promise<boolean> => {
  const response = await apiClient.get<boolean>(`/resumes/${resumeId}/experiences/${experienceId}/job-analysis/exists`);
  return response.data;
};

export const deleteJobAnalysis = async (resumeId: string, experienceId: string): Promise<void> => {
  await apiClient.delete(`/resumes/${resumeId}/experiences/${experienceId}/job-analysis`);
};

// Job Search Service (port 8085)
const JOB_SEARCH_API_URL = process.env.NEXT_PUBLIC_JOB_SEARCH_API_URL || 'http://localhost:8085/api';

const jobSearchClient = axios.create({
  baseURL: JOB_SEARCH_API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add request interceptor to include auth token
jobSearchClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('authToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export const createJobSearchProfile = async (resumeId: string, experienceIds: string[]): Promise<any> => {
  const response = await jobSearchClient.post('/job-search/profiles', {
    resumeId,
    experienceIds
  });
  return response.data;
};

export const updateJobPost = async (profileId: string, editedJobPost: string): Promise<any> => {
  const response = await jobSearchClient.put(`/job-search/profiles/${profileId}`, {
    editedJobPost
  });
  return response.data;
};

export const getJobSearchProfilesByResume = async (resumeId: string): Promise<any[]> => {
  const response = await jobSearchClient.get(`/job-search/profiles?resumeId=${resumeId}`);
  return response.data;
};

export const getJobSearchProfile = async (profileId: string): Promise<any> => {
  const response = await jobSearchClient.get(`/job-search/profiles/${profileId}`);
  return response.data;
};

export const searchMatchingJobs = async (profileId: string, topK: number = 20): Promise<any[]> => {
  const response = await jobSearchClient.post(`/job-search/profiles/${profileId}/search?topK=${topK}`);
  return response.data;
};

export const getJobSearchProfileLines = async (profileId: string): Promise<any[]> => {
  const response = await jobSearchClient.get(`/job-search/profiles/${profileId}/lines`);
  return response.data;
};

export const getProfileSkills = async (profileId: string): Promise<any[]> => {
  const response = await jobSearchClient.get(`/job-search/profiles/${profileId}/skills`);
  return response.data;
};

export const addProfileSkill = async (profileId: string, skillName: string, skillCategory: string = '', proficiencyScore: number = 50): Promise<any> => {
  const response = await jobSearchClient.post(`/job-search/profiles/${profileId}/skills`, {
    skillName,
    skillCategory,
    proficiencyScore
  });
  return response.data;
};

export const updateSkillProficiency = async (profileId: string, skillId: string, proficiencyScore: number): Promise<any> => {
  const response = await jobSearchClient.patch(`/job-search/profiles/${profileId}/skills/${skillId}/proficiency`, {
    proficiencyScore
  });
  return response.data;
};

export const removeProfileSkill = async (profileId: string, skillId: string): Promise<void> => {
  await jobSearchClient.delete(`/job-search/profiles/${profileId}/skills/${skillId}`);
};

export const updateProfileMetadata = async (profileId: string, location: string, experienceLevel: string, desiredJobTitle?: string): Promise<any> => {
  const response = await jobSearchClient.put(`/job-search/profiles/${profileId}/metadata`, {
    location,
    experienceLevel,
    desiredJobTitle
  });
  return response.data;
};

export const getMatchingResults = async (profileId: string, topK: number = 20, refresh: boolean = false, maxDaysOld: number = 30): Promise<any> => {
  const response = await jobSearchClient.get(`/job-search/profiles/${profileId}/matching-results?topK=${topK}&refresh=${refresh}&maxDaysOld=${maxDaysOld}`);
  return response.data;
};

export const toggleMatchSaved = async (matchId: string, rating: number): Promise<any> => {
  const response = await jobSearchClient.patch(`/job-search/matches/${matchId}/save`, {
    rating
  });
  return response.data;
};

export const markMatchApplied = async (matchId: string): Promise<any> => {
  const response = await jobSearchClient.patch(`/job-search/matches/${matchId}/apply`);
  return response.data;
};

export const toggleMatchRedflag = async (matchId: string, redflag: boolean): Promise<any> => {
  const response = await jobSearchClient.patch(`/job-search/matches/${matchId}/redflag`, {
    redflag
  });
  return response.data;
};

export const recordProfileVisit = async (profileId: string): Promise<any> => {
  const response = await jobSearchClient.patch(`/job-search/profiles/${profileId}/visit`);
  return response.data;
};

// Job Queue Management
export const getJobStatus = async (jobId: string): Promise<any> => {
  const response = await apiClient.get(`/jobs/${jobId}/status`);
  return response.data;
};

export const pollJobUntilComplete = async (
  jobId: string,
  onProgress?: (status: any) => void,
  pollIntervalMs: number = 3000,
  maxWaitMs: number = 300000 // 5 minutes
): Promise<any> => {
  const startTime = Date.now();

  while (true) {
    const status = await getJobStatus(jobId);

    if (onProgress) {
      onProgress(status);
    }

    if (status.status === 'COMPLETED') {
      return status;
    }

    if (status.status === 'FAILED') {
      throw new Error(status.errorMessage || 'Job failed');
    }

    if (status.status === 'CANCELLED') {
      throw new Error('Job was cancelled');
    }

    // Check timeout
    if (Date.now() - startTime > maxWaitMs) {
      throw new Error('Job polling timeout exceeded');
    }

    // Wait before next poll
    await new Promise(resolve => setTimeout(resolve, pollIntervalMs));
  }
};

// User Credit Management
export const getUserCredits = async (userId: string = 'default_user'): Promise<any> => {
  const response = await apiClient.get(`/users/${userId}/credits`);
  return response.data;
};

export const getCreditTransactions = async (userId: string = 'default_user'): Promise<any[]> => {
  const response = await apiClient.get(`/users/${userId}/credits/transactions`);
  return response.data;
};

export const grantCredits = async (userId: string, amount: number, reason: string): Promise<any> => {
  const response = await apiClient.post(`/users/admin/credits/grant`, {
    userId,
    amount,
    reason
  });
  return response.data;
};

// Job Crawl Activity History
export const getCrawlActivityHistory = async (limit: number = 5): Promise<any[]> => {
  const response = await jobSearchClient.get(`/job-search/admin/crawl/history?limit=${limit}`);
  return response.data;
};