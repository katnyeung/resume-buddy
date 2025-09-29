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