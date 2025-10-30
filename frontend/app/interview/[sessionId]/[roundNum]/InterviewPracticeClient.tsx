"use client";

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import InterviewPracticeRefactored from '@/components/InterviewPracticeRefactored';
import {
  getSessionInfo,
  getRoundInfo,
  getJobListing,
  getResumeExperience,
  SessionInfo,
  SessionRoundInfo,
  JobListing,
  ResumeExperience
} from '@/lib/api/interview';
import AppHeader from '@/components/AppHeader';

interface InterviewPracticeClientProps {
  sessionId: string;
  roundNum: number;
}

export default function InterviewPracticeClient({ sessionId, roundNum }: InterviewPracticeClientProps) {
  const router = useRouter();

  const [sessionInfo, setSessionInfo] = useState<SessionInfo | null>(null);
  const [roundInfo, setRoundInfo] = useState<SessionRoundInfo | null>(null);
  const [jobListing, setJobListing] = useState<JobListing | null>(null);
  const [resumeExperience, setResumeExperience] = useState<ResumeExperience | null>(null);
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

        // Fetch job listing if available
        if (session.jobListingId) {
          try {
            const job = await getJobListing(session.jobListingId.toString());
            setJobListing(job);
          } catch (jobErr) {
            console.error('Failed to fetch job listing:', jobErr);
            // Continue without job listing
          }
        }

        // Fetch resume experience if available
        if (session.resumeId && session.experienceId) {
          try {
            const experience = await getResumeExperience(
              session.resumeId.toString(),
              session.experienceId
            );
            setResumeExperience(experience);
          } catch (expErr) {
            console.error('Failed to fetch resume experience:', expErr);
            // Continue without resume experience
          }
        }

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
      <InterviewPracticeRefactored
        sessionId={sessionId}
        roundNumber={roundNum}
        jobListing={jobListing}
        resumeExperience={resumeExperience}
      />
    </div>
  );
}
