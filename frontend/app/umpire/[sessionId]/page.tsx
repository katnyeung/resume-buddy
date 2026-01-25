'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import {
  getSession,
  createUmpireWebSocket,
  UmpirePhase,
  WSMessage,
  WSSessionStart,
  WSPhaseEvaluation,
  WSCodeResult,
  WSHint,
  WSRoundComplete,
} from '@/lib/api/umpire';

const PHASE_ORDER: UmpirePhase[] = ['UNDERSTAND', 'MATCH', 'PLAN', 'IMPLEMENT', 'REVIEW', 'EVALUATE'];

const PHASE_COLORS: Record<UmpirePhase, string> = {
  UNDERSTAND: 'text-blue-400',
  MATCH: 'text-purple-400',
  PLAN: 'text-yellow-400',
  IMPLEMENT: 'text-green-400',
  REVIEW: 'text-orange-400',
  EVALUATE: 'text-pink-400',
};

interface ChatMessage {
  id: string;
  sender: 'user' | 'ai' | 'system';
  content: string;
  phase: UmpirePhase;
  type?: string;
}

export default function UmpirePracticePage() {
  const params = useParams();
  const router = useRouter();
  const { token, loading: authLoading } = useAuth();
  const sessionId = params.sessionId as string;

  // Session state
  const [problem, setProblem] = useState<WSSessionStart['problem'] | null>(null);
  const [currentPhase, setCurrentPhase] = useState<UmpirePhase>('UNDERSTAND');
  const [roundNumber, setRoundNumber] = useState(1);
  const [phaseScores, setPhaseScores] = useState<Record<string, number>>({});
  const [overallProgress, setOverallProgress] = useState(0);

  // Chat state
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [transcript, setTranscript] = useState('');

  // Code state
  const [code, setCode] = useState('');
  const [testResults, setTestResults] = useState<WSCodeResult | null>(null);

  // UI state
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isRecording, setIsRecording] = useState(false);
  const [roundComplete, setRoundComplete] = useState(false);
  const [roundSummary, setRoundSummary] = useState<WSRoundComplete | null>(null);

  // Refs
  const wsRef = useRef<WebSocket | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const chatEndRef = useRef<HTMLDivElement>(null);

  // Auto-scroll chat
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // WebSocket connection
  useEffect(() => {
    if (authLoading) return; // Wait for auth to initialize

    if (!token) {
      router.push('/login');
      return;
    }

    const ws = createUmpireWebSocket(sessionId);
    wsRef.current = ws;

    ws.onopen = () => {
      console.log('[UMPIRE] WebSocket connected');
      setLoading(false);
    };

    ws.onmessage = (event) => {
      try {
        const msg: WSMessage = JSON.parse(event.data);
        handleWSMessage(msg);
      } catch (e) {
        console.error('[UMPIRE] Failed to parse message:', e);
      }
    };

    ws.onerror = (event) => {
      console.error('[UMPIRE] WebSocket error:', event);
      setError('Connection error. Please refresh the page.');
    };

    ws.onclose = () => {
      console.log('[UMPIRE] WebSocket closed');
    };

    return () => {
      ws.close();
    };
  }, [sessionId, token, authLoading, router]);

  const handleWSMessage = useCallback((msg: WSMessage) => {
    switch (msg.type) {
      case 'session_start':
        setProblem(msg.problem);
        setCurrentPhase(msg.current_phase);
        setRoundNumber(msg.round_number);
        setCode(`# ${msg.problem.function_signature}\n\n`);
        break;

      case 'phase_intro':
        addMessage({
          sender: 'system',
          content: msg.description,
          phase: msg.phase,
          type: 'intro',
        });
        break;

      case 'transcript':
        if (msg.cumulative) {
          setTranscript(msg.text);
        } else {
          setTranscript((prev) => prev + ' ' + msg.text);
        }
        break;

      case 'ai_response':
        addMessage({
          sender: 'ai',
          content: msg.text,
          phase: msg.phase,
          type: msg.response_type,
        });
        break;

      case 'phase_evaluation':
        handlePhaseEvaluation(msg);
        break;

      case 'phase_transition':
        setCurrentPhase(msg.to_phase);
        setOverallProgress(msg.overall_progress);
        addMessage({
          sender: 'system',
          content: `Moving to ${msg.to_phase} phase`,
          phase: msg.to_phase,
          type: 'transition',
        });
        break;

      case 'code_result':
        setTestResults(msg);
        addMessage({
          sender: 'system',
          content: `Tests: ${msg.passed}/${msg.total} passed (${msg.execution_time_ms}ms)`,
          phase: currentPhase,
          type: 'code_result',
        });
        break;

      case 'hint':
        addMessage({
          sender: 'ai',
          content: `Hint: ${msg.hint_text}`,
          phase: msg.phase,
          type: 'hint',
        });
        break;

      case 'round_complete':
        setRoundComplete(true);
        setRoundSummary(msg);
        break;

      case 'error':
        setError(msg.message);
        break;
    }
  }, [currentPhase]);

  const addMessage = (msg: Omit<ChatMessage, 'id'>) => {
    setMessages((prev) => [...prev, { ...msg, id: Date.now().toString() }]);
  };

  const handlePhaseEvaluation = (msg: WSPhaseEvaluation) => {
    setPhaseScores((prev) => ({ ...prev, [msg.phase]: msg.score }));

    const content = `
**${msg.phase} Phase Evaluation**
Score: ${msg.score}/100
${msg.feedback}

**Strengths:** ${msg.strengths.join(', ')}
**Areas to improve:** ${msg.improvements.join(', ')}
${msg.can_proceed ? '✓ Ready to proceed to next phase' : '✗ Consider improving before proceeding'}
    `.trim();

    addMessage({
      sender: 'ai',
      content,
      phase: msg.phase,
      type: 'evaluation',
    });
  };

  // Voice recording
  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mediaRecorder = new MediaRecorder(stream, { mimeType: 'audio/webm' });
      mediaRecorderRef.current = mediaRecorder;

      mediaRecorder.ondataavailable = (event) => {
        if (event.data.size > 0 && wsRef.current?.readyState === WebSocket.OPEN) {
          wsRef.current.send(event.data);
        }
      };

      mediaRecorder.start(100); // Send chunks every 100ms
      setIsRecording(true);
    } catch (err) {
      console.error('Failed to start recording:', err);
      alert('Could not access microphone. Please check permissions.');
    }
  };

  const stopRecording = () => {
    if (mediaRecorderRef.current) {
      mediaRecorderRef.current.stop();
      mediaRecorderRef.current.stream.getTracks().forEach((track) => track.stop());
      setIsRecording(false);

      // Request final transcription
      sendControlMessage('transcribe_final');
    }
  };

  const sendControlMessage = (type: string, data?: Record<string, unknown>) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({ type, ...data }));
    }
  };

  const handleEndPhase = () => {
    if (transcript) {
      addMessage({
        sender: 'user',
        content: transcript,
        phase: currentPhase,
      });
    }
    sendControlMessage('end_phase');
    setTranscript('');
  };

  const handleRunCode = () => {
    sendControlMessage('run_code', { code });
  };

  const handleNeedHint = (level: 'gentle' | 'moderate' | 'detailed' = 'gentle') => {
    sendControlMessage('need_hint', { hint_type: level });
  };

  const handleTextInput = (text: string) => {
    sendControlMessage('text_input', { content: text });
    addMessage({
      sender: 'user',
      content: text,
      phase: currentPhase,
    });
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-900 text-white flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mx-auto mb-4"></div>
          <p>Connecting to UMPIRE session...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-900 text-white flex items-center justify-center">
        <div className="text-center">
          <p className="text-red-400 mb-4">{error}</p>
          <button
            onClick={() => router.push('/umpire')}
            className="bg-blue-600 hover:bg-blue-700 px-4 py-2 rounded"
          >
            Back to Problem Selection
          </button>
        </div>
      </div>
    );
  }

  if (roundComplete && roundSummary) {
    return (
      <div className="min-h-screen bg-gray-900 text-white p-8">
        <div className="max-w-2xl mx-auto bg-gray-800 rounded-lg p-8">
          <h1 className="text-2xl font-bold mb-4 text-center">Round {roundSummary.round_number} Complete!</h1>

          <div className="text-center mb-8">
            <div className="text-6xl font-bold text-blue-400 mb-2">
              {roundSummary.overall_score.toFixed(1)}
            </div>
            <p className="text-gray-400">Overall Score</p>
          </div>

          <div className="grid grid-cols-3 gap-4 mb-8">
            {PHASE_ORDER.map((phase) => (
              <div key={phase} className="text-center p-3 bg-gray-700 rounded">
                <div className={`text-lg font-bold ${PHASE_COLORS[phase]}`}>
                  {roundSummary.phase_scores[phase] || '-'}
                </div>
                <div className="text-xs text-gray-400">{phase}</div>
              </div>
            ))}
          </div>

          <p className="text-gray-300 mb-6">{roundSummary.feedback_summary}</p>

          {roundSummary.next_round_info && (
            <div className="bg-gray-700 rounded p-4 mb-6">
              <p className="text-sm text-gray-400">Next round scheduled:</p>
              <p className="font-medium">
                {roundSummary.next_round_info.scheduled_date} at {roundSummary.next_round_info.scheduled_time}
              </p>
            </div>
          )}

          <div className="flex gap-4">
            <button
              onClick={() => router.push('/umpire')}
              className="flex-1 bg-gray-600 hover:bg-gray-500 py-3 rounded"
            >
              Back to Problems
            </button>
            <button
              onClick={() => window.location.reload()}
              className="flex-1 bg-blue-600 hover:bg-blue-700 py-3 rounded"
            >
              Practice Again
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="h-screen bg-gray-900 text-white flex flex-col">
      {/* Header */}
      <div className="bg-gray-800 border-b border-gray-700 px-4 py-2">
        <div className="flex justify-between items-center">
          <div className="flex items-center gap-4">
            <h1 className="font-semibold">{problem?.title || 'UMPIRE Practice'}</h1>
            <span className="text-sm text-gray-400">Round {roundNumber}</span>
          </div>
          <div className="flex items-center gap-2">
            {PHASE_ORDER.map((phase, i) => (
              <div
                key={phase}
                className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold ${
                  currentPhase === phase
                    ? 'bg-blue-600'
                    : phaseScores[phase]
                    ? 'bg-green-600'
                    : 'bg-gray-700'
                }`}
              >
                {phase[0]}
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Main Content - Three Panels */}
      <div className="flex-1 flex overflow-hidden">
        {/* Left Panel - Problem */}
        <div className="w-1/3 border-r border-gray-700 overflow-y-auto p-4">
          <h2 className="text-lg font-semibold mb-4">{problem?.title}</h2>
          <div className="prose prose-invert prose-sm max-w-none">
            <p className="whitespace-pre-wrap">{problem?.description}</p>
            {problem?.constraints && (
              <div className="mt-4">
                <h4 className="font-medium">Constraints:</h4>
                <p className="text-gray-400">{problem.constraints}</p>
              </div>
            )}
            {problem?.examples && problem.examples.length > 0 && (
              <div className="mt-4">
                <h4 className="font-medium">Examples:</h4>
                {problem.examples.map((ex, i) => (
                  <div key={i} className="bg-gray-800 rounded p-3 mt-2">
                    <p className="text-sm">
                      <span className="text-gray-400">Input:</span> {JSON.stringify(ex.input)}
                    </p>
                    <p className="text-sm">
                      <span className="text-gray-400">Output:</span> {JSON.stringify(ex.expected)}
                    </p>
                  </div>
                ))}
              </div>
            )}
          </div>
          <div className="mt-4">
            <code className="text-green-400 text-sm block bg-gray-800 p-2 rounded">
              {problem?.function_signature}
            </code>
          </div>
        </div>

        {/* Center Panel - Code Editor */}
        <div className="w-1/3 border-r border-gray-700 flex flex-col">
          <div className="p-2 bg-gray-800 border-b border-gray-700 flex justify-between items-center">
            <span className="text-sm text-gray-400">Code Editor</span>
            <button
              onClick={handleRunCode}
              className="bg-green-600 hover:bg-green-700 px-3 py-1 rounded text-sm"
            >
              Run Code
            </button>
          </div>
          <textarea
            value={code}
            onChange={(e) => setCode(e.target.value)}
            className="flex-1 bg-gray-900 p-4 font-mono text-sm resize-none focus:outline-none"
            placeholder="Write your code here..."
          />
          {testResults && (
            <div className="p-3 bg-gray-800 border-t border-gray-700">
              <div className={`text-sm ${testResults.passed === testResults.total ? 'text-green-400' : 'text-yellow-400'}`}>
                Tests: {testResults.passed}/{testResults.total} passed
              </div>
              <div className="mt-2 space-y-1 max-h-32 overflow-y-auto">
                {testResults.results.map((result, i) => (
                  <div
                    key={i}
                    className={`text-xs p-2 rounded ${result.passed ? 'bg-green-900/30' : 'bg-red-900/30'}`}
                  >
                    <span className={result.passed ? 'text-green-400' : 'text-red-400'}>
                      {result.passed ? '✓' : '✗'}
                    </span>
                    {' '}Input: {JSON.stringify(result.input)} → {JSON.stringify(result.output)}
                    {result.error && <span className="text-red-400 block">{result.error}</span>}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Right Panel - AI Chat */}
        <div className="w-1/3 flex flex-col">
          <div className="p-2 bg-gray-800 border-b border-gray-700">
            <span className={`text-sm font-medium ${PHASE_COLORS[currentPhase]}`}>
              {currentPhase} Phase
            </span>
          </div>

          {/* Messages */}
          <div className="flex-1 overflow-y-auto p-4 space-y-4">
            {messages.map((msg) => (
              <div
                key={msg.id}
                className={`p-3 rounded ${
                  msg.sender === 'user'
                    ? 'bg-blue-600/20 ml-8'
                    : msg.sender === 'ai'
                    ? 'bg-gray-700 mr-8'
                    : 'bg-gray-800 text-center text-sm'
                }`}
              >
                {msg.sender !== 'system' && (
                  <div className="text-xs text-gray-400 mb-1">
                    {msg.sender === 'user' ? 'You' : 'AI Interviewer'} • {msg.phase}
                  </div>
                )}
                <div className="whitespace-pre-wrap">{msg.content}</div>
              </div>
            ))}
            <div ref={chatEndRef} />
          </div>

          {/* Live Transcript */}
          {transcript && (
            <div className="px-4 py-2 bg-gray-800 border-t border-gray-700">
              <div className="text-xs text-gray-400 mb-1">Live transcript:</div>
              <div className="text-sm text-gray-300 italic">{transcript}</div>
            </div>
          )}

          {/* Controls */}
          <div className="p-4 bg-gray-800 border-t border-gray-700 space-y-3">
            <div className="flex gap-2">
              <button
                onClick={isRecording ? stopRecording : startRecording}
                className={`flex-1 py-2 rounded font-medium ${
                  isRecording
                    ? 'bg-red-600 hover:bg-red-700'
                    : 'bg-blue-600 hover:bg-blue-700'
                }`}
              >
                {isRecording ? '⏹ Stop Recording' : '🎤 Start Speaking'}
              </button>
              <button
                onClick={handleEndPhase}
                className="px-4 py-2 bg-green-600 hover:bg-green-700 rounded"
              >
                Complete Phase
              </button>
            </div>
            <div className="flex gap-2">
              <button
                onClick={() => handleNeedHint('gentle')}
                className="flex-1 py-1 text-sm bg-gray-700 hover:bg-gray-600 rounded"
              >
                Gentle Hint
              </button>
              <button
                onClick={() => handleNeedHint('moderate')}
                className="flex-1 py-1 text-sm bg-gray-700 hover:bg-gray-600 rounded"
              >
                More Help
              </button>
              <button
                onClick={() => handleNeedHint('detailed')}
                className="flex-1 py-1 text-sm bg-gray-700 hover:bg-gray-600 rounded"
              >
                Detailed Hint
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
