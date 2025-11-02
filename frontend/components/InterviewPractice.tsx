"use client";

import React, { useState, useEffect, useRef } from 'react';
import { createInterviewWebSocket } from '@/lib/api/interview';

interface InterviewPracticeProps {
  sessionId: string;
  roundNumber: number;
}

interface LogEntry {
  timestamp: string;
  message: string;
  type: 'info' | 'success' | 'warning' | 'error';
}

interface InterruptEvent {
  type: string;
  reason: string;
  text: string;
  audio_base64?: string;
  restart_recording?: boolean;
}

export default function InterviewPractice({ sessionId, roundNumber }: InterviewPracticeProps) {
  // WebSocket and recording state
  const [ws, setWs] = useState<WebSocket | null>(null);
  const [mediaRecorder, setMediaRecorder] = useState<MediaRecorder | null>(null);
  const [audioStream, setAudioStream] = useState<MediaStream | null>(null);
  const [status, setStatus] = useState<'disconnected' | 'connected' | 'recording'>('disconnected');

  // Interview data
  const [question, setQuestion] = useState<string>('Waiting for question...');
  const [transcript, setTranscript] = useState<string>('Start recording to see transcript...');
  const [interrupts, setInterrupts] = useState<InterruptEvent[]>([]);
  const [score, setScore] = useState<number | null>(null);
  const [feedback, setFeedback] = useState<string>('');
  const [showEvaluation, setShowEvaluation] = useState(false);

  // Context selection state
  const [showSetup, setShowSetup] = useState<boolean>(true);
  const [contextOptions, setContextOptions] = useState({
    use_job_data: true,
    use_description_lines: true,
    use_coach_questions: true,
    use_stars_analysis: true
  });

  // Logs
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const logContainerRef = useRef<HTMLDivElement>(null);

  // Audio element ref for playing audio
  const audioRef = useRef<HTMLAudioElement | null>(null);

  // Add log entry
  const addLog = (message: string, type: LogEntry['type'] = 'info') => {
    const timestamp = new Date().toLocaleTimeString();
    setLogs(prev => [...prev, { timestamp, message, type }]);

    // Auto-scroll logs
    setTimeout(() => {
      if (logContainerRef.current) {
        logContainerRef.current.scrollTop = logContainerRef.current.scrollHeight;
      }
    }, 100);
  };

  // Update status
  const updateStatus = (newStatus: typeof status) => {
    setStatus(newStatus);
  };

  // Play audio from base64
  const playAudio = (base64Audio: string): Promise<void> => {
    return new Promise((resolve, reject) => {
      const audio = new Audio('data:audio/mp3;base64,' + base64Audio);
      audioRef.current = audio;

      audio.onended = () => {
        resolve();
      };

      audio.onerror = (err) => {
        addLog(`Audio play error: ${err}`, 'error');
        reject(err);
      };

      audio.play().catch(err => {
        addLog(`Audio play error: ${err}`, 'error');
        reject(err);
      });
    });
  };

  // Handle WebSocket messages
  const handleMessage = async (msg: any) => {
    addLog(`Received: ${msg.type}`, 'info');

    switch (msg.type) {
      case 'question':
        setQuestion(msg.text);
        addLog(`Question: ${msg.text}`, 'success');

        // Pause recording during audio playback (avoid system noise)
        if (msg.pause_recording && mediaRecorder && mediaRecorder.state === 'recording') {
          mediaRecorder.pause();
          addLog('Recording paused during question playback', 'warning');
        }

        // Play audio if provided
        if (msg.audio_base64) {
          try {
            await playAudio(msg.audio_base64);

            // Resume recording when audio finishes
            if (mediaRecorder && mediaRecorder.state === 'paused') {
              mediaRecorder.resume();
              addLog('Recording resumed', 'success');
            }
          } catch (error) {
            // Audio play failed, continue anyway
          }
        }
        break;

      case 'transcript':
        setTranscript(prev => prev === 'Start recording to see transcript...' ? msg.text + ' ' : prev + msg.text + ' ');
        addLog(`Transcript: ${msg.text}`, 'info');
        break;

      case 'interrupt':
        handleInterrupt(msg);
        break;

      case 'evaluation':
        setScore(msg.score);
        setFeedback(msg.feedback);
        setShowEvaluation(true);
        addLog(`Final Score: ${msg.score}`, 'success');
        stopRecording();
        break;

      case 'completion':
        addLog(`✅ SESSION COMPLETE: ${msg.message}`, 'success');
        updateStatus('connected');
        if (msg.audio_base64) {
          await playAudio(msg.audio_base64);
        }
        alert(msg.message);
        break;

      case 'error':
        addLog(`ERROR: ${msg.message}`, 'error');
        alert(`Error: ${msg.message}`);
        break;

      case 'ai_thinking':
        addLog(msg.message, 'warning');
        updateStatus('recording');
        break;

      case 'silent_restart':
        addLog('✅ AI DECIDED NOT TO INTERRUPT - Silently restarting recording...', 'success');
        updateStatus('recording');
        await handleSilentRestart();
        break;

      default:
        addLog(`Unknown message type: ${msg.type}`, 'warning');
    }
  };

  // Handle AI interrupt
  const handleInterrupt = async (msg: InterruptEvent) => {
    addLog(`INTERRUPT (${msg.type}): ${msg.text}`, 'warning');

    // Add to interrupts list
    setInterrupts(prev => [...prev, msg]);

    // CRITICAL: Stop MediaRecorder BEFORE playing audio
    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
      mediaRecorder.stop();
      addLog('Stopped MediaRecorder before playing interrupt audio', 'warning');
    }

    // Play audio
    if (msg.audio_base64) {
      try {
        await playAudio(msg.audio_base64);

        // Restart recording when interrupt audio finishes
        if (msg.restart_recording && audioStream) {
          setTimeout(() => {
            restartMediaRecorder();
          }, 100);
        }
      } catch (error) {
        // Audio play failed, continue anyway
      }
    }
  };

  // Handle silent restart (AI decided not to interrupt)
  const handleSilentRestart = async () => {
    // Silently restart MediaRecorder to reset WebM stream
    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
      mediaRecorder.stop();
      addLog('Stopped MediaRecorder for silent restart', 'info');
    }

    // Wait 50ms then restart
    setTimeout(() => {
      restartMediaRecorder();
    }, 50);
  };

  // Restart MediaRecorder with fresh state
  const restartMediaRecorder = () => {
    if (!audioStream || !ws || ws.readyState !== WebSocket.OPEN) {
      return;
    }

    const newRecorder = new MediaRecorder(audioStream, {
      mimeType: 'audio/webm;codecs=opus',
      audioBitsPerSecond: 16000
    });

    newRecorder.ondataavailable = (event) => {
      if (event.data.size > 0 && ws && ws.readyState === WebSocket.OPEN) {
        ws.send(event.data);
      }
    };

    newRecorder.start(30); // 30ms chunks
    setMediaRecorder(newRecorder);
    addLog('Restarted MediaRecorder with fresh state', 'success');

    // Tell server we're ready to accept chunks again
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send('recording_restarted');
      addLog('Notified server: recording restarted', 'info');
    }
  };

  // Connect to WebSocket with context options
  const connect = () => {
    // Build query params from context options
    const params = new URLSearchParams();
    Object.entries(contextOptions).forEach(([key, value]) => {
      params.append(key, String(value));
    });

    const wsUrl = `ws://localhost:8086/ws/interview/${sessionId}/rounds/${roundNumber}/stream?${params.toString()}`;
    addLog(`Connecting to ${wsUrl}...`, 'info');

    const newWs = createInterviewWebSocket(
      sessionId,
      roundNumber,
      handleMessage,
      (error) => {
        addLog(`WebSocket error: ${error}`, 'error');
        updateStatus('disconnected');
      },
      (event) => {
        addLog('WebSocket closed', 'warning');
        updateStatus('disconnected');
      },
      params.toString() // Pass query params to WebSocket helper
    );

    newWs.onopen = () => {
      addLog('WebSocket connected!', 'success');
      updateStatus('connected');
      setShowSetup(false); // Hide setup UI after successful connection
    };

    setWs(newWs);
  };

  // Start recording
  const startRecording = async () => {
    try {
      addLog('Requesting microphone access...', 'info');
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          sampleRate: 16000
        }
      });

      addLog('Microphone access granted', 'success');
      setAudioStream(stream);

      const recorder = new MediaRecorder(stream, {
        mimeType: 'audio/webm;codecs=opus',
        audioBitsPerSecond: 16000
      });

      recorder.ondataavailable = (event) => {
        if (event.data.size > 0 && ws && ws.readyState === WebSocket.OPEN) {
          ws.send(event.data);
        }
      };

      recorder.start(30); // 30ms chunks
      setMediaRecorder(recorder);
      addLog('Recording started (30ms chunks)', 'success');

      updateStatus('recording');
      setTranscript('');
      setInterrupts([]);

    } catch (error: any) {
      addLog(`Microphone error: ${error}`, 'error');
      alert('Could not access microphone: ' + error.message);
    }
  };

  // Stop recording
  const stopRecording = () => {
    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
      mediaRecorder.stop();
      addLog('Recording stopped', 'warning');
    }

    if (audioStream) {
      audioStream.getTracks().forEach(track => track.stop());
      setAudioStream(null);
    }

    // Send "end_answer" message to server
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send('end_answer');
      addLog('Sent end signal to server', 'info');
    }

    updateStatus('connected');
  };

  // Disconnect
  const disconnect = () => {
    stopRecording();

    if (ws) {
      ws.close();
      setWs(null);
    }

    addLog('Disconnected', 'warning');
  };

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      disconnect();
    };
  }, []);

  // Get status class
  const getStatusClass = () => {
    switch (status) {
      case 'connected':
        return 'bg-green-100 text-green-700';
      case 'recording':
        return 'bg-yellow-100 text-yellow-700';
      case 'disconnected':
      default:
        return 'bg-red-100 text-red-700';
    }
  };

  // Get status text
  const getStatusText = () => {
    switch (status) {
      case 'connected':
        return 'Connected - Waiting for question...';
      case 'recording':
        return 'Recording...';
      case 'disconnected':
      default:
        return 'Disconnected';
    }
  };

  return (
    <div className="max-w-6xl mx-auto p-6 bg-gray-50 min-h-screen">
      <div className="bg-white rounded-lg shadow-md p-8">
        <h1 className="text-3xl font-bold mb-4">🎙️ Interview Practice</h1>

        {/* Status */}
        <div className={`p-3 rounded mb-6 font-semibold ${getStatusClass()}`}>
          {getStatusText()}
        </div>

        {/* Context Selection Setup (shown before connecting) */}
        {showSetup && status === 'disconnected' && (
          <div className="mb-6 p-6 bg-blue-50 border border-blue-200 rounded-lg">
            <h2 className="text-xl font-semibold mb-3 text-blue-900">
              📋 Customize Your Interview Focus
            </h2>
            <p className="text-sm text-gray-700 mb-4">
              Select which context to include when generating interview questions.
              This helps you focus on specific areas you want to practice.
            </p>

            <div className="space-y-3">
              <label className="flex items-start gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={contextOptions.use_job_data}
                  onChange={(e) => setContextOptions({
                    ...contextOptions,
                    use_job_data: e.target.checked
                  })}
                  className="mt-1 w-4 h-4"
                />
                <div>
                  <div className="font-medium text-gray-900">Target Job Description</div>
                  <div className="text-sm text-gray-600">
                    Include requirements from the job posting you're applying for
                  </div>
                </div>
              </label>

              <label className="flex items-start gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={contextOptions.use_description_lines}
                  onChange={(e) => setContextOptions({
                    ...contextOptions,
                    use_description_lines: e.target.checked
                  })}
                  className="mt-1 w-4 h-4"
                />
                <div>
                  <div className="font-medium text-gray-900">Your Actual Accomplishments</div>
                  <div className="text-sm text-gray-600">
                    Include the specific bullet points from your resume experience
                  </div>
                </div>
              </label>

              <label className="flex items-start gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={contextOptions.use_coach_questions}
                  onChange={(e) => setContextOptions({
                    ...contextOptions,
                    use_coach_questions: e.target.checked
                  })}
                  className="mt-1 w-4 h-4"
                />
                <div>
                  <div className="font-medium text-gray-900">Coach Questions</div>
                  <div className="text-sm text-gray-600">
                    Include suggested areas to probe from the career coach analysis
                  </div>
                </div>
              </label>

              <label className="flex items-start gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={contextOptions.use_stars_analysis}
                  onChange={(e) => setContextOptions({
                    ...contextOptions,
                    use_stars_analysis: e.target.checked
                  })}
                  className="mt-1 w-4 h-4"
                />
                <div>
                  <div className="font-medium text-gray-900">STARS Improvement Areas</div>
                  <div className="text-sm text-gray-600">
                    Include suggestions to add more depth (Situation, Task, Action, Result, Scale)
                  </div>
                </div>
              </label>
            </div>

            <div className="mt-4 p-3 bg-yellow-50 border border-yellow-200 rounded text-sm text-yellow-800">
              💡 <strong>Tip:</strong> For more focused practice, disable areas you've already covered.
              All options are enabled by default for comprehensive questions.
            </div>
          </div>
        )}

        {/* Controls */}
        <div className="flex gap-3 mb-6">
          <button
            onClick={connect}
            disabled={status !== 'disconnected'}
            className="px-6 py-3 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {showSetup ? 'Start Interview' : 'Connect'}
          </button>
          <button
            onClick={startRecording}
            disabled={status !== 'connected'}
            className="px-6 py-3 bg-green-500 text-white rounded hover:bg-green-600 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Start Recording
          </button>
          <button
            onClick={stopRecording}
            disabled={status !== 'recording'}
            className="px-6 py-3 bg-red-500 text-white rounded hover:bg-red-600 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Stop Recording
          </button>
          <button
            onClick={disconnect}
            disabled={status === 'disconnected'}
            className="px-6 py-3 bg-gray-500 text-white rounded hover:bg-gray-600 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Disconnect
          </button>
        </div>

        {/* Question */}
        <div className="mb-6">
          <h3 className="text-lg font-semibold mb-2">📝 Question</h3>
          <div className="p-4 bg-gray-100 rounded border border-gray-300 font-mono whitespace-pre-wrap">
            {question}
          </div>
        </div>

        {/* Live Transcript */}
        <div className="mb-6">
          <h3 className="text-lg font-semibold mb-2">💬 Live Transcript</h3>
          <div className="p-4 bg-white rounded border border-gray-300 min-h-[100px] font-mono whitespace-pre-wrap">
            {transcript}
          </div>
        </div>

        {/* Interrupts */}
        <div className="mb-6">
          <h3 className="text-lg font-semibold mb-2">⚡ AI Interrupts</h3>
          <div className="max-h-[200px] overflow-y-auto">
            {interrupts.length === 0 ? (
              <div className="text-gray-500 italic">No interrupts yet...</div>
            ) : (
              interrupts.map((interrupt, index) => (
                <div
                  key={index}
                  className={`p-3 mb-2 bg-white rounded border-l-4 ${
                    interrupt.type === 'gentle' ? 'border-green-500' :
                    interrupt.type === 'moderate' ? 'border-yellow-500' :
                    'border-red-500'
                  }`}
                >
                  <strong>{interrupt.type.toUpperCase()}</strong> - {interrupt.reason}<br />
                  {interrupt.text}
                </div>
              ))
            )}
          </div>
        </div>

        {/* Final Evaluation */}
        {showEvaluation && (
          <div className="mb-6">
            <h3 className="text-lg font-semibold mb-2">🎯 Final Evaluation</h3>
            <div
              className={`text-5xl font-bold text-center my-4 ${
                score! >= 80 ? 'text-green-500' :
                score! >= 60 ? 'text-yellow-500' :
                'text-red-500'
              }`}
            >
              {score?.toFixed(1)}
            </div>
            <div className="p-4 bg-gray-100 rounded font-mono whitespace-pre-wrap">
              {feedback}
            </div>
          </div>
        )}

        {/* Debug Log */}
        <div>
          <h3 className="text-lg font-semibold mb-2">🔍 Debug Log</h3>
          <div
            ref={logContainerRef}
            className="max-h-[300px] overflow-y-auto bg-gray-900 text-gray-300 p-4 rounded font-mono text-sm"
          >
            {logs.map((log, index) => (
              <div
                key={index}
                className={`mb-1 ${
                  log.type === 'info' ? 'text-blue-400' :
                  log.type === 'success' ? 'text-green-400' :
                  log.type === 'warning' ? 'text-yellow-400' :
                  'text-red-400'
                }`}
              >
                [{log.timestamp}] {log.message}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
