/**
 * Interview Practice API Client
 * Handles HTTP requests and WebSocket connections for interview practice service
 */

// Interview practice service runs on separate port (8086)
const INTERVIEW_API_URL = process.env.NEXT_PUBLIC_INTERVIEW_API_URL || 'http://localhost:8086';

export interface SessionInfo {
  id: string;
  userId: string;  // UUID
  resumeId: string;  // UUID
  experienceId?: string;  // UUID (optional)
  jobListingId?: string;  // UUID (optional)
  interviewType: string;
  difficultyLevel: string;
  interruptLevel: string;
  scheduledDate: string;
  scheduledTime: string;
  totalRounds: number;
  currentRound: number;
  status: string;
}

export interface SessionRoundInfo {
  id: string;
  sessionId: string;
  roundNumber: number;
  scheduledDate: string;
  scheduledTime: string;
  status: string;
  questionText?: string;
  answerText?: string;
  score?: number;
}

/**
 * Get session information
 */
export async function getSessionInfo(sessionId: string): Promise<SessionInfo> {
  const response = await fetch(`${INTERVIEW_API_URL}/api/interview/sessions/${sessionId}/info`);

  if (!response.ok) {
    const errorText = await response.text();
    console.error('Session info error:', errorText);
    throw new Error(`Failed to fetch session info: ${response.statusText}`);
  }

  const data = await response.json();
  console.log('Session info response:', data);
  return data;
}

/**
 * Get round information
 */
export async function getRoundInfo(sessionId: string, roundNumber: number): Promise<SessionRoundInfo> {
  const response = await fetch(
    `${INTERVIEW_API_URL}/api/interview/sessions/${sessionId}/rounds/${roundNumber}/info`
  );

  if (!response.ok) {
    const errorText = await response.text();
    console.error('Round info error:', errorText);
    throw new Error(`Failed to fetch round info: ${response.statusText}`);
  }

  const data = await response.json();
  console.log('Round info response:', data);
  return data;
}

/**
 * Create WebSocket connection for streaming interview
 */
export function createInterviewWebSocket(
  sessionId: string,
  roundNumber: number,
  onMessage: (data: any) => void,
  onError?: (error: Event) => void,
  onClose?: (event: CloseEvent) => void
): WebSocket {
  // Use wss:// for production, ws:// for local dev
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const host = process.env.NODE_ENV === 'production'
    ? window.location.host
    : 'localhost:8086';

  const wsUrl = `${protocol}//${host}/ws/interview/${sessionId}/rounds/${roundNumber}/stream`;

  const ws = new WebSocket(wsUrl);

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      onMessage(data);
    } catch (error) {
      console.error('Failed to parse WebSocket message:', error);
    }
  };

  ws.onerror = (error) => {
    console.error('WebSocket error:', error);
    if (onError) {
      onError(error);
    }
  };

  ws.onclose = (event) => {
    console.log('WebSocket closed:', event.code, event.reason);
    if (onClose) {
      onClose(event);
    }
  };

  return ws;
}

/**
 * Get conversation history for a specific round
 * Tries Redis first, falls back to database
 */
export interface ConversationInteraction {
  interaction_num: number;
  question: string;
  answer_transcript?: string;
  satisfaction_level?: number;
  needs_followup?: boolean;
  feedback?: string;
  followup_question?: string;
  final_summary?: string;
  timestamp: string;
  updated_at?: string;
  interrupt_count?: number;
}

export interface ConversationHistory {
  session_id: string;
  round_number: number;
  status: string;
  interactions: ConversationInteraction[];
  interrupts: any[];
  final_score?: number;
  total_interactions: number;
  created_at?: string;
  completed_at?: string;
}

export async function getConversationHistory(
  sessionId: string,
  roundNumber: number
): Promise<ConversationHistory> {
  const response = await fetch(
    `${INTERVIEW_API_URL}/api/interview/sessions/${sessionId}/rounds/${roundNumber}/conversation`
  );

  if (!response.ok) {
    throw new Error(`Failed to fetch conversation history: ${response.statusText}`);
  }

  return response.json();
}

/**
 * Send regenerate question message via WebSocket
 */
export function regenerateQuestion(ws: WebSocket) {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ text: "regenerate_question" }));
  } else {
    throw new Error("WebSocket is not connected");
  }
}

/**
 * Get job listing from job-search-service
 */
export interface JobListing {
  id: string;
  title: string;
  company?: string;
  location?: string;
  description: string;
  postedDate?: string;
  salary?: string;
}

export async function getJobListing(jobListingId: string): Promise<JobListing> {
  // Use resume API URL base but call job-search service
  const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
  const jobSearchUrl = baseUrl.replace(':8080', ':8085');

  const response = await fetch(`${jobSearchUrl}/job-search/listings/${jobListingId}`);

  if (!response.ok) {
    throw new Error(`Failed to fetch job listing: ${response.statusText}`);
  }

  return response.json();
}

/**
 * Get resume experience analysis from resume service
 */
export interface ResumeExperience {
  id: string;
  jobTitle: string;
  seniorityLevel: string;
  normalizedTitle?: string;
  descriptionLines: Array<{
    text: string;
    value?: string;
  }>;
  extractedSkills?: Array<{
    skill: string;
    proficiency?: string;
  }>;
  potentialQuestions?: string[];
  starsAnalysis?: string[];
}

export async function getResumeExperience(
  resumeId: string,
  experienceId: string
): Promise<ResumeExperience> {
  const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

  const response = await fetch(
    `${baseUrl}/resumes/${resumeId}/experiences/${experienceId}/analysis`
  );

  if (!response.ok) {
    throw new Error(`Failed to fetch resume experience: ${response.statusText}`);
  }

  return response.json();
}
