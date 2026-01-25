/**
 * UMPIRE Practice API client
 */

const UMPIRE_API_URL = process.env.NEXT_PUBLIC_UMPIRE_API_URL || 'http://localhost:8089/api/umpire';
const UMPIRE_WS_URL = process.env.NEXT_PUBLIC_UMPIRE_WS_URL || 'ws://localhost:8089/ws/umpire';

// Types
export interface Problem {
  id: string;
  title: string;
  description: string;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
  algorithm_type: string;
  function_signature: string;
  constraints?: string;
  test_cases: TestCase[];
  expected_clarifications?: string[];
  optimal_approaches?: Approach[];
  source?: string;
  tags?: string[];
}

export interface TestCase {
  input: Record<string, unknown>;
  expected: unknown;
}

export interface Approach {
  name: string;
  complexity: string;
  description: string;
}

export interface Session {
  id: string;
  user_id: string;
  problem_id: string;
  programming_language: string;
  scheduled_date: string;
  scheduled_time: string;
  timezone: string;
  total_rounds: number;
  round_interval: string;
  current_phase: UmpirePhase;
  phase_scores: Record<string, number>;
  status: SessionStatus;
  completed_rounds: number;
  overall_score?: number;
  created_at: string;
}

export interface Round {
  id: string;
  session_id: string;
  round_number: number;
  scheduled_date: string;
  scheduled_time?: string;
  status: RoundStatus;
  phases_completed: Record<string, boolean>;
  phase_scores: Record<string, number>;
  tests_passed: number;
  tests_total: number;
  round_score?: number;
  feedback_text?: string;
}

export type UmpirePhase = 'UNDERSTAND' | 'MATCH' | 'PLAN' | 'IMPLEMENT' | 'REVIEW' | 'EVALUATE';
export type SessionStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
export type RoundStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
export type HintLevel = 'gentle' | 'moderate' | 'detailed';

export interface CreateSessionRequest {
  user_id: string;
  problem_id: string;
  programming_language?: string;
  scheduled_date: string;
  scheduled_time: string;
  timezone?: string;
  total_rounds?: number;
  round_interval?: 'DAILY' | 'EVERY_2_DAYS' | 'WEEKLY';
}

// API functions
export async function listProblems(params?: {
  difficulty?: string;
  algorithm_type?: string;
  tags?: string[];
  skip?: number;
  limit?: number;
}): Promise<{ problems: Problem[]; total: number }> {
  const searchParams = new URLSearchParams();
  if (params?.difficulty) searchParams.set('difficulty', params.difficulty);
  if (params?.algorithm_type) searchParams.set('algorithm_type', params.algorithm_type);
  if (params?.tags) params.tags.forEach(t => searchParams.append('tags', t));
  if (params?.skip) searchParams.set('skip', String(params.skip));
  if (params?.limit) searchParams.set('limit', String(params.limit));

  const response = await fetch(`${UMPIRE_API_URL}/problems?${searchParams}`);
  if (!response.ok) throw new Error('Failed to fetch problems');
  return response.json();
}

export async function getRandomProblem(params?: {
  difficulty?: string;
  algorithm_type?: string;
}): Promise<Problem> {
  const searchParams = new URLSearchParams();
  if (params?.difficulty) searchParams.set('difficulty', params.difficulty);
  if (params?.algorithm_type) searchParams.set('algorithm_type', params.algorithm_type);

  const response = await fetch(`${UMPIRE_API_URL}/problems/random?${searchParams}`);
  if (!response.ok) throw new Error('Failed to fetch random problem');
  return response.json();
}

export async function getProblem(problemId: string): Promise<Problem> {
  const response = await fetch(`${UMPIRE_API_URL}/problems/${problemId}`);
  if (!response.ok) throw new Error('Problem not found');
  return response.json();
}

export async function createSession(request: CreateSessionRequest): Promise<Session> {
  const response = await fetch(`${UMPIRE_API_URL}/sessions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  if (!response.ok) throw new Error('Failed to create session');
  return response.json();
}

export async function getSession(sessionId: string): Promise<Session & { problem?: Problem; rounds: Round[] }> {
  const response = await fetch(`${UMPIRE_API_URL}/sessions/${sessionId}`);
  if (!response.ok) throw new Error('Session not found');
  return response.json();
}

export async function getUserActiveSessions(userId: string): Promise<Session[]> {
  const response = await fetch(`${UMPIRE_API_URL}/sessions/user/${userId}/active`);
  if (!response.ok) throw new Error('Failed to fetch sessions');
  return response.json();
}

export async function getAlgorithmTypes(): Promise<string[]> {
  const response = await fetch(`${UMPIRE_API_URL}/problems/types/list`);
  if (!response.ok) throw new Error('Failed to fetch algorithm types');
  const data = await response.json();
  return data.types;
}

// WebSocket connection helper
export function createUmpireWebSocket(sessionId: string): WebSocket {
  return new WebSocket(`${UMPIRE_WS_URL}/${sessionId}/stream`);
}

// WebSocket message types
export interface WSSessionStart {
  type: 'session_start';
  session_id: string;
  round_number: number;
  problem: {
    title: string;
    description: string;
    function_signature: string;
    constraints?: string;
    examples: TestCase[];
  };
  current_phase: UmpirePhase;
}

export interface WSPhaseIntro {
  type: 'phase_intro';
  phase: UmpirePhase;
  description: string;
  expected_duration_seconds: number;
  audio_base64?: string;
}

export interface WSTranscript {
  type: 'transcript';
  text: string;
  cumulative: boolean;
}

export interface WSAIResponse {
  type: 'ai_response';
  phase: UmpirePhase;
  text: string;
  audio_base64?: string;
  response_type: 'acknowledgement' | 'clarifying_question' | 'feedback' | 'prompt';
}

export interface WSPhaseEvaluation {
  type: 'phase_evaluation';
  phase: UmpirePhase;
  score: number;
  feedback: string;
  strengths: string[];
  improvements: string[];
  can_proceed: boolean;
  audio_base64?: string;
}

export interface WSPhaseTransition {
  type: 'phase_transition';
  from_phase: UmpirePhase;
  to_phase: UmpirePhase;
  overall_progress: number;
}

export interface WSCodeResult {
  type: 'code_result';
  passed: number;
  total: number;
  results: Array<{
    input: unknown;
    expected: unknown;
    output: unknown;
    passed: boolean;
    error?: string;
  }>;
  execution_time_ms: number;
}

export interface WSHint {
  type: 'hint';
  phase: UmpirePhase;
  hint_level: HintLevel;
  hint_text: string;
  audio_base64?: string;
}

export interface WSRoundComplete {
  type: 'round_complete';
  round_number: number;
  phase_scores: Record<string, number>;
  overall_score: number;
  feedback_summary: string;
  code_quality: {
    correctness: number;
    planning: number;
    analysis: number;
  };
  next_round_info?: {
    scheduled_date: string;
    scheduled_time: string;
  };
  audio_base64?: string;
}

export interface WSError {
  type: 'error';
  message: string;
}

export type WSMessage =
  | WSSessionStart
  | WSPhaseIntro
  | WSTranscript
  | WSAIResponse
  | WSPhaseEvaluation
  | WSPhaseTransition
  | WSCodeResult
  | WSHint
  | WSRoundComplete
  | WSError;
