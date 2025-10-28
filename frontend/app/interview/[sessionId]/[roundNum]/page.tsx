"use client";

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import InterviewPractice from '@/components/InterviewPractice';
import { getSessionInfo, getRoundInfo, SessionInfo, SessionRoundInfo } from '@/lib/api/interview';
import AppHeader from '@/components/AppHeader';

export default function InterviewPracticePage() {
  const params = useParams();
  const router = useRouter();
  const sessionId = params.sessionId as string;
  const roundNum = parseInt(params.roundNum as string);

  const [sessionInfo, setSessionInfo] = useState<SessionInfo | null>(null);
  const [roundInfo, setRoundInfo] = useState<SessionRoundInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);

        // Fetch session and round info
        const [session, round] = await Promise.all([
          getSessionInfo(sessionId),
          getRoundInfo(sessionId, roundNum)
        ]);

        setSessionInfo(session);
        setRoundInfo(round);
        setLoading(false);
      } catch (err: any) {
        console.error('Failed to fetch session/round info:', err);
        setError(err.message || 'Failed to load interview session');
        setLoading(false);
      }
    };

    if (sessionId && roundNum) {
      fetchData();
    }
  }, [sessionId, roundNum]);

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50">
        <AppHeader />
        <div className="flex items-center justify-center h-screen">
          <div className="text-center">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mx-auto mb-4"></div>
            <p className="text-gray-600">Loading interview session...</p>
          </div>
        </div>
      </div>
    );
  }

  if (error || !sessionInfo || !roundInfo) {
    return (
      <div className="min-h-screen bg-gray-50">
        <AppHeader />
        <div className="flex items-center justify-center h-screen">
          <div className="bg-white p-8 rounded-lg shadow-md max-w-md">
            <div className="text-red-500 text-5xl mb-4">⚠️</div>
            <h1 className="text-2xl font-bold mb-4">Session Not Found</h1>
            <p className="text-gray-600 mb-6">
              {error || 'The interview session you are looking for does not exist or has expired.'}
            </p>
            <button
              onClick={() => router.push('/')}
              className="w-full px-6 py-3 bg-blue-500 text-white rounded hover:bg-blue-600"
            >
              Go to Home
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <AppHeader />

      {/* Session Header */}
      <div className="bg-gradient-to-r from-blue-500 to-purple-600 text-white py-8">
        <div className="max-w-6xl mx-auto px-6">
          <h1 className="text-3xl font-bold mb-2">
            Interview Practice - Round {roundNum}
          </h1>
          <div className="flex items-center gap-6 text-sm">
            <div>
              <span className="opacity-80">Type:</span>{' '}
              <span className="font-semibold">{sessionInfo.interviewType}</span>
            </div>
            <div>
              <span className="opacity-80">Level:</span>{' '}
              <span className="font-semibold">{sessionInfo.difficultyLevel}</span>
            </div>
            <div>
              <span className="opacity-80">Coaching:</span>{' '}
              <span className="font-semibold">{sessionInfo.interruptLevel}</span>
            </div>
            <div>
              <span className="opacity-80">Progress:</span>{' '}
              <span className="font-semibold">
                Round {roundNum} of {sessionInfo.totalRounds}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Interview Component */}
      <InterviewPractice sessionId={sessionId} roundNumber={roundNum} />

      {/* Help Section */}
      <div className="max-w-6xl mx-auto px-6 py-8">
        <div className="bg-blue-50 border border-blue-200 rounded-lg p-6">
          <h3 className="text-lg font-semibold mb-3 text-blue-900">💡 How it works</h3>
          <ul className="space-y-2 text-blue-800">
            <li>1. Click <strong>Connect</strong> to establish connection with the interview service</li>
            <li>2. Listen to the question (played automatically)</li>
            <li>3. Click <strong>Start Recording</strong> when you're ready to answer</li>
            <li>4. Speak your answer - the AI will provide live feedback if needed</li>
            <li>5. Click <strong>Stop Recording</strong> when finished, or wait for timeout/silence detection</li>
            <li>6. Review your score and feedback</li>
          </ul>

          <div className="mt-4 p-3 bg-yellow-50 border border-yellow-200 rounded">
            <p className="text-sm text-yellow-800">
              <strong>Tip:</strong> Use headphones to prevent audio feedback. Ensure you're in a quiet environment
              for best transcription quality.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
