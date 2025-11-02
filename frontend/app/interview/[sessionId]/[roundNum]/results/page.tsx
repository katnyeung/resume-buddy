'use client';

import { useEffect, useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import AppHeader from '@/components/AppHeader';
import { getRoundInfo, SessionRoundInfo } from '@/lib/api/interview';

export default function RoundResultsPage() {
  const router = useRouter();
  const params = useParams();
  const sessionId = params.sessionId as string;
  const roundNum = parseInt(params.roundNum as string);

  const [round, setRound] = useState<SessionRoundInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadRoundDetails();
  }, [sessionId, roundNum]);

  const loadRoundDetails = async () => {
    try {
      setLoading(true);
      const data = await getRoundInfo(sessionId, roundNum);
      setRound(data);
      setError(null);
    } catch (err) {
      console.error('Failed to load round details:', err);
      setError('Failed to load round details');
    } finally {
      setLoading(false);
    }
  };

  const getScoreColor = (score: number) => {
    if (score >= 80) return 'text-green-600';
    if (score >= 60) return 'text-yellow-600';
    return 'text-red-600';
  };

  const getScoreBgColor = (score: number) => {
    if (score >= 80) return 'bg-green-50 border-green-200';
    if (score >= 60) return 'bg-yellow-50 border-yellow-200';
    return 'bg-red-50 border-red-200';
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <AppHeader title={`Round ${roundNum} Results`} showBackButton={true} />

      <div className="max-w-4xl mx-auto px-4 py-8">
        {loading ? (
          <div className="flex justify-center items-center py-12">
            <div className="text-gray-600">Loading round details...</div>
          </div>
        ) : error ? (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700">
            {error}
          </div>
        ) : round ? (
          <>
            {/* Score Card */}
            {round.score !== null && round.score !== undefined && (
              <div className={`rounded-lg border-2 p-6 mb-6 ${getScoreBgColor(round.score)}`}>
                <div className="flex items-center justify-between">
                  <div>
                    <h2 className="text-lg font-semibold text-gray-700 mb-1">Final Score</h2>
                    <p className="text-sm text-gray-600">
                      Completed on {new Date(round.scheduledDate).toLocaleDateString()}
                    </p>
                  </div>
                  <div className={`text-5xl font-bold ${getScoreColor(round.score)}`}>
                    {Math.round(round.score)}/100
                  </div>
                </div>
              </div>
            )}

            {/* Question Section */}
            {round.questionText && (
              <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-6">
                <div className="flex items-center gap-2 mb-3">
                  <div className="w-8 h-8 rounded-full bg-blue-500 flex items-center justify-center text-white font-semibold">
                    AI
                  </div>
                  <h3 className="text-lg font-semibold text-gray-900">Interview Question</h3>
                </div>
                <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                  <p className="text-gray-900 leading-relaxed whitespace-pre-wrap">{round.questionText}</p>
                </div>
              </div>
            )}

            {/* Answer Section */}
            {round.answerText && (
              <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-6">
                <div className="flex items-center gap-2 mb-3">
                  <div className="w-8 h-8 rounded-full bg-gray-500 flex items-center justify-center text-white font-semibold">
                    YOU
                  </div>
                  <h3 className="text-lg font-semibold text-gray-900">Your Answer</h3>
                </div>
                <div className="bg-gray-50 border border-gray-200 rounded-lg p-4">
                  <p className="text-gray-900 leading-relaxed whitespace-pre-wrap">{round.answerText}</p>
                </div>
              </div>
            )}

            {/* Feedback Section */}
            {(round.score !== null || round.feedbackText) && (
              <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-6">
                <div className="flex items-center gap-2 mb-3">
                  <div className="w-8 h-8 rounded-full bg-purple-500 flex items-center justify-center text-white text-lg">
                    ✨
                  </div>
                  <h3 className="text-lg font-semibold text-gray-900">Feedback & Evaluation</h3>
                </div>
                <div className={`border-2 rounded-lg p-4 ${round.score !== null && round.score !== undefined ? getScoreBgColor(round.score) : 'bg-gray-50 border-gray-200'}`}>
                  {round.score !== null && round.score !== undefined && (
                    <div className="flex items-center gap-3 mb-3">
                      <span className="px-3 py-1 bg-white border border-gray-300 rounded-lg text-sm font-semibold">
                        EVALUATION
                      </span>
                      <span className={`text-2xl font-bold ${getScoreColor(round.score)}`}>
                        {Math.round(round.score)}/100
                      </span>
                    </div>
                  )}
                  <p className="text-gray-900 leading-relaxed whitespace-pre-wrap">
                    {round.feedbackText || `Your score is ${Math.round(round.score || 0)} out of 100. This round has been evaluated based on your response quality and technical depth.`}
                  </p>
                </div>
              </div>
            )}

            {/* Action Buttons */}
            <div className="flex gap-4 justify-center">
              <button
                onClick={() => router.push('/interviews')}
                className="px-6 py-3 bg-gray-200 text-gray-700 rounded-lg font-medium hover:bg-gray-300 transition-colors"
              >
                Back to History
              </button>
              <button
                onClick={() => router.push(`/interview/${sessionId}/${roundNum + 1}`)}
                className="px-6 py-3 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 transition-colors"
              >
                Next Round
              </button>
            </div>
          </>
        ) : (
          <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4 text-yellow-700">
            Round not found
          </div>
        )}
      </div>
    </div>
  );
}
