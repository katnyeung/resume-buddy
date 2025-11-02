'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import AppHeader from '@/components/AppHeader';
import { getUserSessions, SessionInfo } from '@/lib/api/interview';
import { getCurrentUserId } from '@/lib/userUtils';

export default function InterviewsPage() {
  const router = useRouter();
  const [sessions, setSessions] = useState<SessionInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const userId = getCurrentUserId();

  useEffect(() => {
    loadSessions();
  }, []);

  const loadSessions = async () => {
    try {
      setLoading(true);
      const data = await getUserSessions(userId);
      setSessions(data);
      setError(null);
    } catch (err) {
      console.error('Failed to load sessions:', err);
      setError('Failed to load interview sessions');
    } finally {
      setLoading(false);
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return 'bg-green-100 text-green-800 border-green-300';
      case 'IN_PROGRESS':
        return 'bg-blue-100 text-blue-800 border-blue-300';
      case 'PENDING':
        return 'bg-yellow-100 text-yellow-800 border-yellow-300';
      default:
        return 'bg-gray-100 text-gray-800 border-gray-300';
    }
  };

  const getScoreColor = (score: number) => {
    if (score >= 80) return 'text-green-600';
    if (score >= 60) return 'text-yellow-600';
    return 'text-red-600';
  };

  // Separate sessions into upcoming and completed
  const upcomingSessions = sessions.filter(s => s.status !== 'COMPLETED');
  const completedSessions = sessions.filter(s => s.status === 'COMPLETED');

  return (
    <div className="min-h-screen bg-gray-50">
      <AppHeader title="Interview Practice History" showBackButton={true} />

      <div className="max-w-6xl mx-auto px-4 py-8">
        {loading ? (
          <div className="flex justify-center items-center py-12">
            <div className="text-gray-600">Loading interview sessions...</div>
          </div>
        ) : error ? (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700">
            {error}
          </div>
        ) : sessions.length === 0 ? (
          <div className="bg-white rounded-lg shadow-sm p-8 text-center">
            <div className="text-gray-500 mb-4">
              <svg className="w-16 h-16 mx-auto mb-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" />
              </svg>
              <p className="text-lg font-medium">No Interview Sessions Yet</p>
              <p className="mt-2">Create your first interview session from Job Search</p>
            </div>
            <button
              onClick={() => router.push('/')}
              className="mt-4 px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              Go to Dashboard
            </button>
          </div>
        ) : (
          <>
            {/* Upcoming Rounds Section */}
            {upcomingSessions.length > 0 && (
              <div className="mb-8">
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Upcoming Practice</h2>
                <div className="grid gap-4">
                  {upcomingSessions.map((session: any) => {
                    const nextRound = session.rounds?.find((r: any) => r.status === 'PENDING');
                    if (!nextRound) return null;

                    return (
                      <div key={session.id} className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
                        <div className="flex items-start justify-between">
                          <div className="flex-1">
                            <div className="flex items-center gap-3 mb-2">
                              <span className={`px-3 py-1 rounded-full text-sm font-medium border ${getStatusColor(session.status)}`}>
                                {session.interviewType}
                              </span>
                              <span className="text-sm text-gray-500">{session.difficultyLevel}</span>
                            </div>
                            <p className="text-gray-700 mb-2">
                              <span className="font-medium">Round {nextRound.roundNumber} of {session.totalRounds}</span>
                              {' '}- Scheduled for {new Date(nextRound.scheduledDate).toLocaleDateString()}
                            </p>
                            <p className="text-sm text-gray-600">
                              Completed: {session.completedRounds} / {session.totalRounds}
                            </p>
                          </div>
                          <button
                            onClick={() => router.push(`/interview/${session.id}/${nextRound.roundNumber}`)}
                            className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium"
                          >
                            Start Round {nextRound.roundNumber}
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Completed Sessions Section */}
            {completedSessions.length > 0 && (
              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Completed Sessions</h2>
                <div className="grid gap-4">
                  {completedSessions.map((session: any) => (
                    <div key={session.id} className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
                      <div className="flex items-start justify-between mb-4">
                        <div className="flex-1">
                          <div className="flex items-center gap-3 mb-2">
                            <span className={`px-3 py-1 rounded-full text-sm font-medium border ${getStatusColor(session.status)}`}>
                              {session.interviewType}
                            </span>
                            <span className="text-sm text-gray-500">{session.difficultyLevel}</span>
                            {session.overallScore !== null && (
                              <span className={`text-2xl font-bold ${getScoreColor(session.overallScore)}`}>
                                {Math.round(session.overallScore)}/100
                              </span>
                            )}
                          </div>
                          <p className="text-sm text-gray-600">
                            Completed on {new Date(session.scheduledDate).toLocaleDateString()}
                          </p>
                        </div>
                      </div>

                      {/* Rounds Summary */}
                      <div className="mt-4 space-y-2">
                        <h4 className="text-sm font-medium text-gray-700">Rounds:</h4>
                        {session.rounds?.map((round: any) => (
                          <button
                            key={round.id}
                            onClick={() => round.status === 'COMPLETED' && router.push(`/interview/${session.id}/${round.roundNumber}/results`)}
                            className={`w-full flex items-center justify-between bg-gray-50 rounded-lg p-3 transition-colors ${
                              round.status === 'COMPLETED' ? 'hover:bg-gray-100 cursor-pointer' : 'cursor-default'
                            }`}
                          >
                            <div className="flex items-center gap-3">
                              <span className="text-sm font-medium text-gray-700">Round {round.roundNumber}</span>
                              <span className={`px-2 py-0.5 rounded text-xs font-medium ${getStatusColor(round.status)}`}>
                                {round.status}
                              </span>
                              {round.status === 'COMPLETED' && (
                                <span className="text-xs text-gray-500">Click to view details</span>
                              )}
                            </div>
                            {round.score !== null && (
                              <span className={`text-lg font-bold ${getScoreColor(round.score)}`}>
                                {Math.round(round.score)}/100
                              </span>
                            )}
                          </button>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
