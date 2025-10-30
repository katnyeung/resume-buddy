"use client";

import React, { useState, useEffect, useRef } from 'react';
import { createInterviewWebSocket, regenerateQuestion, JobListing, ResumeExperience } from '@/lib/api/interview';
import InterviewContext from './InterviewContext';
import InterviewChat, { ChatMessage } from './InterviewChat';

interface InterviewPracticeProps {
  sessionId: string;
  roundNumber: number;
  jobListing: JobListing | null;
  resumeExperience: ResumeExperience | null;
}

export default function InterviewPractice({
  sessionId,
  roundNumber,
  jobListing,
  resumeExperience
}: InterviewPracticeProps) {
  // WebSocket and recording state
  const [ws, setWs] = useState<WebSocket | null>(null);
  const [mediaRecorder, setMediaRecorder] = useState<MediaRecorder | null>(null);
  const [audioStream, setAudioStream] = useState<MediaStream | null>(null);
  const [status, setStatus] = useState<'disconnected' | 'connected' | 'recording'>('disconnected');

  // Chat messages
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [currentTranscript, setCurrentTranscript] = useState<string>('');

  // Microphone controls
  const [micLevel, setMicLevel] = useState<number>(0);
  const [sensitivity, setSensitivity] = useState<number>(8);
  const [regenerating, setRegenerating] = useState(false);

  // Audio element ref
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);

  // Refs for VAD (to avoid stale closures)
  const wsRef = useRef<WebSocket | null>(null);
  const statusRef = useRef<string>('disconnected');
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioStreamRef = useRef<MediaStream | null>(null);
  const [isAIPlaying, setIsAIPlaying] = useState<boolean>(false);
  const isAIPlayingRef = useRef<boolean>(false);

  // Keep refs in sync with state
  useEffect(() => {
    wsRef.current = ws;
  }, [ws]);

  useEffect(() => {
    statusRef.current = status;
  }, [status]);

  useEffect(() => {
    mediaRecorderRef.current = mediaRecorder;
  }, [mediaRecorder]);

  useEffect(() => {
    audioStreamRef.current = audioStream;
  }, [audioStream]);

  useEffect(() => {
    isAIPlayingRef.current = isAIPlaying;
  }, [isAIPlaying]);

  // Helper: Create and start MediaRecorder (extracted from startRecording)
  const createAndStartMediaRecorder = (): MediaRecorder | null => {
    if (!audioStreamRef.current) {
      console.error('[MediaRecorder] No audio stream available');
      return null;
    }

    const recorder = new MediaRecorder(audioStreamRef.current, {
      mimeType: 'audio/webm;codecs=opus',
      audioBitsPerSecond: 16000
    });

    recorder.ondataavailable = (event) => {
      const currentWs = wsRef.current;
      if (event.data.size > 0 && currentWs && currentWs.readyState === WebSocket.OPEN) {
        currentWs.send(event.data);
      }
    };

    recorder.onerror = (event: any) => {
      console.error('[MediaRecorder] Error:', event.error);
    };

    recorder.start(30); // 30ms chunks
    console.log('[MediaRecorder] Started');
    return recorder;
  };

  // Play audio from base64
  const playAudio = (base64Audio: string): Promise<void> => {
    return new Promise((resolve, reject) => {
      // MediaRecorder already stopped by caller (handleMessage stops before calling playAudio)
      // Just track if we were recording to know whether to restart
      const wasRecording = (statusRef.current === 'recording');

      console.log('[DEBUG] playAudio called: statusRef.current =', statusRef.current, ', wasRecording =', wasRecording);

      // Pause VAD
      setIsAIPlaying(true);
      isAIPlayingRef.current = true;
      console.log('[Audio] VAD paused (playing TTS)');

      const audio = new Audio('data:audio/mp3;base64,' + base64Audio);
      audioRef.current = audio;

      audio.onended = async () => {
        console.log('[Audio] TTS playback ended');
        setIsAIPlaying(false);
        isAIPlayingRef.current = false;
        console.log('[Audio] VAD resumed');

        // Debug: Check restart conditions
        console.log('[DEBUG] Restart check: wasRecording =', wasRecording, ', audioStreamRef =', !!audioStreamRef.current);

        // Restart MediaRecorder (match test_streaming.html)
        if (wasRecording && audioStreamRef.current) {
          console.log('[Audio] Waiting 2s before restart...');

          // Wait 2000ms (2 seconds) - match test_streaming.html line 517
          // Longer delay ensures audio output fully stops and prevents echo
          await new Promise(res => setTimeout(res, 2000));

          console.log('[Audio] Creating new MediaRecorder...');

          // Create and START MediaRecorder FIRST
          const newRecorder = createAndStartMediaRecorder();
          if (newRecorder) {
            setMediaRecorder(newRecorder);
            console.log('[Audio] Restarted MediaRecorder after 2s delay');

            // Restart VAD (critical - without this, silence detection won't work for followup answers)
            startSilenceDetection();
            console.log('[Audio] Restarted VAD for followup answer');

            // THEN send recording_restarted (AFTER starting - match test_streaming.html line 1073)
            const currentWs = wsRef.current;
            if (currentWs && currentWs.readyState === WebSocket.OPEN) {
              currentWs.send(JSON.stringify({ text: 'recording_restarted' }));
              console.log('[Audio] Sent recording_restarted signal');
            }
          } else {
            console.error('[Audio] createAndStartMediaRecorder returned null!');
          }
        } else {
          console.warn('[Audio] NOT restarting MediaRecorder - conditions not met');
        }

        resolve();
      };

      audio.onerror = async (err) => {
        console.error('[Audio] TTS playback error:', err);
        setIsAIPlaying(false);
        isAIPlayingRef.current = false;

        // Same restart logic on error
        if (wasRecording && audioStream) {
          await new Promise(res => setTimeout(res, 2000));

          const newRecorder = createAndStartMediaRecorder();
          if (newRecorder) {
            setMediaRecorder(newRecorder);
            console.log('[Audio] Restarted MediaRecorder after error');

            const currentWs = wsRef.current;
            if (currentWs && currentWs.readyState === WebSocket.OPEN) {
              currentWs.send(JSON.stringify({ text: 'recording_restarted' }));
              console.log('[Audio] Sent recording_restarted signal');
            }
          }
        }

        reject(err);
      };

      audio.play().catch(reject);
    });
  };

  // Handle WebSocket messages and convert to chat messages
  const handleMessage = async (msg: any) => {
    console.log('Received:', msg.type, msg);

    switch (msg.type) {
      case 'question':
      case 'followup':
        console.log(`[DEBUG] Received ${msg.type}, MediaRecorder state:`, mediaRecorderRef.current?.state);

        // CRITICAL: Stop MediaRecorder BEFORE playing audio (match test_streaming.html line 505)
        // This prevents echo and reduces stale chunks in WebSocket buffer
        if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') {
          mediaRecorderRef.current.stop();
          console.log('[Audio] Stopped MediaRecorder before TTS');
        }

        // Add question message to chat
        setMessages(prev => [...prev, {
          type: 'question',
          text: msg.text || msg.question,
          canRegenerate: msg.can_regenerate,
          isRegenerated: msg.is_regenerated
        }]);

        // Play audio if provided
        if (msg.audio_base64) {
          try {
            await playAudio(msg.audio_base64);
          } catch (error) {
            console.error('Audio play error:', error);
          }
        }

        setRegenerating(false);
        break;

      case 'transcript':
        // Update current transcript (partial)
        setCurrentTranscript(prev => prev + msg.text + ' ');

        // Update or add transcript message
        setMessages(prev => {
          const lastMsg = prev[prev.length - 1];
          if (lastMsg && lastMsg.type === 'transcript') {
            // Update existing transcript
            return [
              ...prev.slice(0, -1),
              { type: 'transcript', text: lastMsg.text + msg.text + ' ', isPartial: true }
            ];
          } else {
            // Add new transcript message
            return [...prev, { type: 'transcript', text: msg.text + ' ', isPartial: true }];
          }
        });
        break;

      case 'interrupt':
        // CRITICAL: Stop MediaRecorder BEFORE playing audio (prevent echo)
        if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') {
          mediaRecorderRef.current.stop();
          console.log('[Audio] Stopped MediaRecorder before TTS (interrupt)');
        }

        setMessages(prev => [...prev, {
          type: 'interrupt',
          interruptType: msg.interrupt_type,
          reason: msg.reason,
          text: msg.text
        }]);

        if (msg.audio_base64) {
          try {
            await playAudio(msg.audio_base64);
          } catch (error) {
            console.error('Audio play error:', error);
          }
        }
        break;

      case 'feedback':
        // CRITICAL: Stop MediaRecorder BEFORE playing audio (prevent echo)
        if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') {
          mediaRecorderRef.current.stop();
          console.log('[Audio] Stopped MediaRecorder before TTS (feedback)');
        }

        // Followup with feedback
        setMessages(prev => [...prev, {
          type: 'followup',
          feedback: msg.feedback || msg.reasoning,
          question: msg.question || msg.followup_question,
          satisfactionLevel: msg.satisfaction_level || msg.score
        }]);

        if (msg.audio_base64) {
          try {
            await playAudio(msg.audio_base64);
          } catch (error) {
            console.error('Audio play error:', error);
          }
        }

        // Reset transcript for next answer
        setCurrentTranscript('');
        break;

      case 'evaluation':
        // CRITICAL: Stop MediaRecorder BEFORE playing audio (prevent echo)
        if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') {
          mediaRecorderRef.current.stop();
          console.log('[Audio] Stopped MediaRecorder before TTS (evaluation)');
        }

        // Mark last transcript as complete (not partial)
        setMessages(prev => {
          const lastMsg = prev[prev.length - 1];
          if (lastMsg && lastMsg.type === 'transcript') {
            return [
              ...prev.slice(0, -1),
              { ...lastMsg, isPartial: false }
            ];
          }
          return prev;
        });

        // Add evaluation message
        setMessages(prev => [...prev, {
          type: 'evaluation',
          score: msg.score,
          feedback: msg.feedback
        }]);

        if (msg.audio_base64) {
          try {
            await playAudio(msg.audio_base64);
          } catch (error) {
            console.error('Audio play error:', error);
          }
        }

        stopRecording();
        break;

      case 'completion':
        setMessages(prev => [...prev, {
          type: 'completion',
          message: msg.message
        }]);
        setStatus('connected');
        break;

      case 'error':
        console.error('WebSocket error:', msg.message);
        alert(`Error: ${msg.message}`);
        break;

      default:
        console.log('Unknown message type:', msg.type);
    }
  };

  // Setup microphone level monitoring
  const setupMicrophoneMonitoring = async (stream: MediaStream) => {
    try {
      const audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
      const analyser = audioContext.createAnalyser();
      const source = audioContext.createMediaStreamSource(stream);

      analyser.fftSize = 2048;
      analyser.smoothingTimeConstant = 0.8;
      source.connect(analyser);

      audioContextRef.current = audioContext;
      analyserRef.current = analyser;

      // Monitor levels
      const dataArray = new Uint8Array(analyser.frequencyBinCount);
      const updateLevel = () => {
        if (!analyserRef.current) return;

        analyser.getByteTimeDomainData(dataArray);
        let sum = 0;
        for (let i = 0; i < dataArray.length; i++) {
          const normalized = (dataArray[i] - 128) / 128;
          sum += normalized * normalized;
        }
        const rms = Math.sqrt(sum / dataArray.length) * 100;
        setMicLevel(Math.min(rms, 100));

        requestAnimationFrame(updateLevel);
      };

      updateLevel();
    } catch (error) {
      console.error('Failed to setup microphone monitoring:', error);
    }
  };

  // Connect and start interview
  const connect = async () => {
    try {
      setStatus('connected');

      // Request microphone access
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          sampleRate: 16000
        }
      });

      setAudioStream(stream);
      setupMicrophoneMonitoring(stream);

      // Create WebSocket
      const newWs = createInterviewWebSocket(
        sessionId,
        roundNumber,
        handleMessage,
        (error) => {
          console.error('WebSocket error:', error);
          setStatus('disconnected');
        },
        (event) => {
          console.log('WebSocket closed');
          setStatus('disconnected');
        }
      );

      newWs.onopen = () => {
        console.log('WebSocket connected');
        setStatus('connected');
      };

      setWs(newWs);
    } catch (error: any) {
      console.error('Failed to connect:', error);
      alert('Could not access microphone: ' + error.message);
      setStatus('disconnected');
    }
  };

  // Start recording
  const startRecording = () => {
    console.log('[Frontend] startRecording clicked');
    console.log('[Frontend] audioStream:', audioStream);
    console.log('[Frontend] ws:', ws);
    console.log('[Frontend] ws.readyState:', ws?.readyState);

    if (!audioStream || !ws) {
      console.error('Cannot start recording - no stream or WebSocket');
      return;
    }

    if (ws.readyState !== WebSocket.OPEN) {
      console.error('WebSocket is not open, state:', ws.readyState);
      alert('WebSocket is not connected. Please reconnect.');
      return;
    }

    console.log('[Frontend] Creating MediaRecorder...');
    const recorder = new MediaRecorder(audioStream, {
      mimeType: 'audio/webm;codecs=opus',
      audioBitsPerSecond: 16000
    });

    recorder.ondataavailable = (event) => {
      const currentWs = wsRef.current;
      if (event.data.size > 0 && currentWs && currentWs.readyState === WebSocket.OPEN) {
        currentWs.send(event.data);
      } else {
        console.warn('[Frontend] Cannot send chunk - ws state:', currentWs?.readyState);
      }
    };

    recorder.onerror = (event: any) => {
      console.error('[Frontend] MediaRecorder error:', event);
      console.error('[Frontend] Error details:', event.error);
    };

    recorder.onstart = () => {
      console.log('[Frontend] MediaRecorder onstart event fired');
    };

    recorder.onstop = () => {
      console.log('[Frontend] MediaRecorder onstop event fired');
    };

    recorder.start(30); // 30ms chunks
    setMediaRecorder(recorder);
    setStatus('recording');
    setCurrentTranscript('');

    // IMPORTANT: Update statusRef immediately for VAD
    statusRef.current = 'recording';

    console.log('[Frontend] MediaRecorder started');

    // Start VAD silence detection (statusRef.current is now 'recording')
    startSilenceDetection();
  };

  // Voice Activity Detection - triggers transcription requests
  const startSilenceDetection = () => {
    console.log('[VAD] Starting silence detection');

    const PAUSE_DURATION_FOR_TRANSCRIPTION = 2000; // 2s pause → transcribe_now
    const SILENCE_DURATION = 10000; // 10s silence → transcribe_final (end answer)
    const MIN_TIME_BETWEEN_TRANSCRIPTIONS = 3000; // Don't spam backend
    const SILENCE_THRESHOLD = 8; // RMS threshold (same as test_streaming.html)

    let silenceStart: number | null = null;
    let lastTranscriptionRequest = 0;
    let vadActive = true;
    let hasSpokenYet = false; // Track if user started speaking
    let checkCount = 0; // Track how many times we check

    const checkSilence = () => {
      // Skip VAD when AI is speaking (prevent echo detection)
      if (isAIPlayingRef.current) {
        setTimeout(checkSilence, 100);
        return;
      }

      checkCount++;

      if (!vadActive || !analyserRef.current || statusRef.current !== 'recording') {
        return; // Stopped or not recording
      }

      // Get time-domain data (waveform) for RMS calculation
      const dataArray = new Uint8Array(analyserRef.current.fftSize);
      analyserRef.current.getByteTimeDomainData(dataArray);

      // Calculate RMS (Root Mean Square) - measure of audio energy
      let sum = 0;
      for (let i = 0; i < dataArray.length; i++) {
        const normalized = (dataArray[i] - 128) / 128;
        sum += normalized * normalized;
      }
      const rms = Math.sqrt(sum / dataArray.length) * 100;
      const isSpeaking = rms >= SILENCE_THRESHOLD;
      const now = Date.now();

      if (isSpeaking) {
        if (!hasSpokenYet) {
          hasSpokenYet = true;
          console.log(`[VAD] User started speaking (RMS: ${rms.toFixed(1)})`);
        }
        // Speech detected - reset silence timer
        silenceStart = null;
      } else if (hasSpokenYet) {
        // Only monitor silence AFTER user has spoken
        if (!silenceStart) {
          silenceStart = now;
        }

        const silenceDuration = now - silenceStart;
        const timeSinceLastTranscription = now - lastTranscriptionRequest;

        // Request transcription after 2s pause
        if (
          silenceDuration >= PAUSE_DURATION_FOR_TRANSCRIPTION &&
          silenceDuration < 5000 && // Stop at 5s (longer silence = done speaking)
          timeSinceLastTranscription >= MIN_TIME_BETWEEN_TRANSCRIPTIONS
        ) {
          console.log(`[VAD] 2s pause detected - silenceDuration: ${silenceDuration}ms, timeSinceLast: ${timeSinceLastTranscription}ms`);
          const currentWs = wsRef.current;
          if (currentWs && currentWs.readyState === WebSocket.OPEN) {
            console.log('[VAD] Sending transcribe_now to backend');
            currentWs.send(JSON.stringify({ text: 'transcribe_now' }));
            lastTranscriptionRequest = now;
            console.log('[VAD] transcribe_now sent successfully');
          } else {
            console.error('[VAD] Cannot send transcribe_now - ws state:', currentWs?.readyState);
          }
        }

        // Request FINAL transcription after 10s silence
        if (
          silenceDuration >= SILENCE_DURATION &&
          silenceDuration < SILENCE_DURATION + 500 && // Only trigger once (10.0-10.5s window)
          timeSinceLastTranscription >= MIN_TIME_BETWEEN_TRANSCRIPTIONS
        ) {
          console.log(`[VAD] 10s silence detected - silenceDuration: ${silenceDuration}ms`);
          const currentWs = wsRef.current;
          if (currentWs && currentWs.readyState === WebSocket.OPEN) {
            console.log('[VAD] Sending transcribe_final to backend');
            currentWs.send(JSON.stringify({ text: 'transcribe_final' }));
            lastTranscriptionRequest = now;
            vadActive = false; // Stop VAD after final transcription
            console.log('[VAD] transcribe_final sent, VAD stopped');
          } else {
            console.error('[VAD] Cannot send transcribe_final - ws state:', currentWs?.readyState);
          }
        }
      }

      // Check again in 100ms
      if (vadActive) {
        setTimeout(checkSilence, 100);
      }
    };

    checkSilence();
  };

  // Stop recording
  const stopRecording = () => {
    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
      mediaRecorder.stop();
    }

    if (audioStream) {
      audioStream.getTracks().forEach(track => track.stop());
      setAudioStream(null);
    }

    if (audioContextRef.current && audioContextRef.current.state !== 'closed') {
      audioContextRef.current.close();
      audioContextRef.current = null;
      analyserRef.current = null;
    }

    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ text: "end_answer" }));
    }

    setStatus('connected');
    setMicLevel(0);
  };

  // Handle regenerate question
  const handleRegenerate = () => {
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      alert('WebSocket is not connected');
      return;
    }

    setRegenerating(true);
    regenerateQuestion(ws);
  };

  // Cleanup on unmount ONLY (not on every state change!)
  useEffect(() => {
    console.log('[Frontend] Cleanup effect registered');
    return () => {
      console.log('[Frontend] CLEANUP TRIGGERED!');
      if (mediaRecorder && mediaRecorder.state !== 'inactive') {
        try {
          console.log('[Frontend] Stopping mediaRecorder in cleanup');
          mediaRecorder.stop();
        } catch (e) {
          console.error('Error stopping mediaRecorder:', e);
        }
      }
      if (audioStream) {
        try {
          console.log('[Frontend] Stopping audioStream in cleanup');
          audioStream.getTracks().forEach(track => track.stop());
        } catch (e) {
          console.error('Error stopping audioStream:', e);
        }
      }
      if (ws && ws.readyState !== WebSocket.CLOSED && ws.readyState !== WebSocket.CLOSING) {
        try {
          console.log('[Frontend] Closing WebSocket in cleanup');
          ws.close();
        } catch (e) {
          console.error('Error closing WebSocket:', e);
        }
      }
      if (audioContextRef.current && audioContextRef.current.state !== 'closed') {
        try {
          console.log('[Frontend] Closing audioContext in cleanup');
          audioContextRef.current.close();
        } catch (e) {
          console.error('Error closing audioContext:', e);
        }
      }
    };
  }, []); // EMPTY DEPS - only cleanup on unmount!

  // Get button states
  const isDisconnected = status === 'disconnected';
  const isConnected = status === 'connected';
  const isRecording = status === 'recording';

  return (
    <div className="flex h-[calc(100vh-200px)] bg-gray-50">
      {/* Left Panel: Context (30%) */}
      <InterviewContext
        jobListing={jobListing}
        resumeExperience={resumeExperience}
      />

      {/* Right Panel: Chat + Controls (70%) */}
      <div className="flex-1 flex flex-col">
        {/* Microphone Controls */}
        <div className="bg-white border-b border-gray-200 p-4">
          <div className="max-w-4xl mx-auto space-y-3">
            {/* Status Indicator */}
            <div className={`inline-flex items-center gap-2 px-3 py-1 rounded-full text-sm font-medium ${
              isDisconnected ? 'bg-red-100 text-red-700' :
              isRecording ? 'bg-yellow-100 text-yellow-700' :
              'bg-green-100 text-green-700'
            }`}>
              <div className={`w-2 h-2 rounded-full ${
                isDisconnected ? 'bg-red-500' :
                isRecording ? 'bg-yellow-500 animate-pulse' :
                'bg-green-500'
              }`}></div>
              {isDisconnected ? 'Disconnected' : isRecording ? 'Recording...' : 'Connected'}
            </div>

            {/* Microphone Level */}
            {!isDisconnected && (
              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="text-sm font-medium text-gray-700">🎤 Microphone Level</label>
                  <span className="text-sm text-gray-500">{micLevel.toFixed(0)}%</span>
                </div>
                <div className="h-3 bg-gray-200 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-gradient-to-r from-green-500 via-yellow-500 to-red-500 transition-all duration-100"
                    style={{ width: `${micLevel}%` }}
                  ></div>
                </div>
              </div>
            )}

            {/* Sensitivity Slider */}
            {!isDisconnected && (
              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="text-sm font-medium text-gray-700">Sensitivity</label>
                  <span className="text-sm text-gray-500">{sensitivity}</span>
                </div>
                <input
                  type="range"
                  min="3"
                  max="20"
                  value={sensitivity}
                  onChange={(e) => setSensitivity(parseInt(e.target.value))}
                  className="w-full"
                />
                <p className="text-xs text-gray-500 mt-1">
                  Lower = less sensitive (better for noisy rooms)
                </p>
              </div>
            )}

            {/* Control Buttons */}
            <div className="flex gap-2">
              <button
                onClick={connect}
                disabled={!isDisconnected}
                className="px-6 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed font-medium"
              >
                Start Interview
              </button>

              {isConnected && (
                <button
                  onClick={startRecording}
                  className="px-6 py-2 bg-green-500 text-white rounded-lg hover:bg-green-600 font-medium"
                >
                  Start Recording
                </button>
              )}

              {isRecording && (
                <button
                  onClick={stopRecording}
                  className="px-6 py-2 bg-red-500 text-white rounded-lg hover:bg-red-600 font-medium"
                >
                  Stop Recording
                </button>
              )}
            </div>
          </div>
        </div>

        {/* Chat Messages */}
        <InterviewChat
          messages={messages}
          onRegenerate={handleRegenerate}
          regenerating={regenerating}
        />
      </div>
    </div>
  );
}
