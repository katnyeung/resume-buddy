import axios from 'axios';
import { Resume, ResumeLine, ResumeLineUpdateDto, BatchUpdateResponse } from './types';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
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