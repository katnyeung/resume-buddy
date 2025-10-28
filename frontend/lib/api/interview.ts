/**
 * Interview Practice API Client
 * Handles HTTP requests and WebSocket connections for interview practice service
 */

const INTERVIEW_API_URL = process.env.NEXT_PUBLIC_API_URL?.replace('/api', '') || 'http://localhost:8086';

export interface SessionInfo {
  id: string;
  userId: number;
  resumeId: number;
  jobListingId?: number;
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
    throw new Error(`Failed to fetch session info: ${response.statusText}`);
  }

  return response.json();
}

/**
 * Get round information
 */
export async function getRoundInfo(sessionId: string, roundNumber: number): Promise<SessionRoundInfo> {
  const response = await fetch(
    `${INTERVIEW_API_URL}/api/interview/sessions/${sessionId}/rounds/${roundNumber}/info`
  );

  if (!response.ok) {
    throw new Error(`Failed to fetch round info: ${response.statusText}`);
  }

  return response.json();
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
