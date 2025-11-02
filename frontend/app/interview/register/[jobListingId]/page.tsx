'use client';

import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import AppHeader from '@/components/AppHeader';
import { useAuth } from '@/hooks/useAuth';
import { getCurrentUserId } from '@/lib/userUtils';
import { getUserCredits, getStructuredAnalysis } from '@/lib/api';
import {
  createInterviewSession,
  getJobListing,
  CreateSessionRequest,
} from '@/lib/api/interview';
import { ExperienceDto } from '@/lib/types';

export default function InterviewRegisterPage() {
  const params = useParams();
  const router = useRouter();
  const searchParams = useSearchParams();
  const jobListingId = params.jobListingId as string;
  const profileId = searchParams.get('profileId');
  const resumeId = searchParams.get('resumeId');

  const { isAuthenticated, isLoading: authLoading } = useAuth();

  // State
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [credits, setCredits] = useState(0);
  const [jobTitle, setJobTitle] = useState('');
  const [jobCompany, setJobCompany] = useState('');
  const [jobDescription, setJobDescription] = useState('');
  const [jobLocation, setJobLocation] = useState('');
  const [experiences, setExperiences] = useState<ExperienceDto[]>([]);

  // Form fields (with smart defaults)
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const tomorrowStr = tomorrow.toISOString().split('T')[0];

  const [experienceId, setExperienceId] = useState('');
  const [interviewType, setInterviewType] = useState<'TECHNICAL' | 'BEHAVIORAL' | 'LEADERSHIP'>('TECHNICAL');
  const [difficultyLevel, setDifficultyLevel] = useState<'JUNIOR' | 'MID' | 'SENIOR' | 'STAFF'>('MID');
  const [interruptLevel, setInterruptLevel] = useState<'CONSERVATIVE' | 'MODERATE' | 'AGGRESSIVE'>('MODERATE');
  const [scheduledDate, setScheduledDate] = useState(tomorrowStr);
  const [scheduledTime, setScheduledTime] = useState('10:00:00');
  const [timezone, setTimezone] = useState('UTC');
  const [totalRounds, setTotalRounds] = useState(3);
  const [roundInterval, setRoundInterval] = useState<'DAILY' | 'EVERY_2_DAYS' | 'WEEKLY'>('DAILY');

  // Fetch data on mount
  useEffect(() => {
    const fetchData = async () => {
      if (!resumeId) {
        setError('Resume ID is required. Please access this page from the job search results.');
        setLoading(false);
        return;
      }

      try {
        const userId = getCurrentUserId();

        // Fetch user credits
        const creditsData = await getUserCredits(userId);
        setCredits(creditsData.availableCredits || 0);

        // Fetch job listing
        try {
          const jobData = await getJobListing(jobListingId);
          console.log('Job listing data:', jobData); // Debug

          // Handle different possible field names from backend
          const title = jobData.title || (jobData as any).jobTitle || 'Job Position';
          const company = jobData.company || (jobData as any).companyName || '';
          const description = jobData.description || '';
          const location = jobData.location || '';

          setJobTitle(title);
          setJobCompany(company);
          setJobDescription(description);
          setJobLocation(location);
        } catch (err: any) {
          console.error('Failed to fetch job listing:', err);
          console.error('Error details:', err.response?.data || err.message);
          setJobTitle('Job Position');
        }

        // Fetch structured analysis to get experiences
        try {
          const analysisData = await getStructuredAnalysis(resumeId);
          if (analysisData?.experiences) {
            setExperiences(analysisData.experiences);
            // Auto-select first experience
            if (analysisData.experiences.length > 0) {
              setExperienceId(analysisData.experiences[0].id);
            }
          }
        } catch (err) {
          console.error('Failed to fetch resume analysis:', err);
        }

        // Detect user timezone
        try {
          const detectedTimezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
          setTimezone(detectedTimezone || 'UTC');
        } catch (err) {
          console.error('Failed to detect timezone:', err);
        }

        setLoading(false);
      } catch (err: any) {
        console.error('Failed to load data:', err);
        setError(err.message || 'Failed to load registration data');
        setLoading(false);
      }
    };

    if (!authLoading && isAuthenticated) {
      fetchData();
    }
  }, [jobListingId, resumeId, authLoading, isAuthenticated]);

  const truncateDescription = (text: string, wordLimit: number = 200) => {
    if (!text) return '';
    const words = text.split(/\s+/);
    if (words.length <= wordLimit) return text;
    return words.slice(0, wordLimit).join(' ') + '...';
  };

  const formatAnalysisDate = (dateStr: string) => {
    try {
      return new Date(dateStr).toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      });
    } catch {
      return dateStr;
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);

    try {
      const userId = getCurrentUserId();

      if (!resumeId) {
        throw new Error('Resume ID is required');
      }

      if (credits < 50) {
        throw new Error('Insufficient credits. You need 50 credits to create an interview session.');
      }

      const request: CreateSessionRequest = {
        user_id: userId,
        resume_id: resumeId,
        job_listing_id: jobListingId,
        experience_id: experienceId || undefined,
        interview_type: interviewType,
        difficulty_level: difficultyLevel,
        interrupt_level: interruptLevel,
        scheduled_date: scheduledDate,
        scheduled_time: scheduledTime,
        timezone: timezone,
        total_rounds: totalRounds,
        round_interval: roundInterval,
      };

      const response = await createInterviewSession(request);

      // Redirect to success page or first round
      router.push(`/interview/${response.session_id}/1`);
    } catch (err: any) {
      console.error('Failed to create session:', err);
      setError(err.message || 'Failed to create interview session');
      setSubmitting(false);
    }
  };

  if (authLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">Verifying authentication...</p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return null;
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50">
        <AppHeader title="Register Interview Practice" showBackButton={true} />
        <div className="max-w-3xl mx-auto px-4 py-8">
          <div className="bg-white rounded-lg shadow-sm p-8 text-center">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600 mx-auto mb-4"></div>
            <p className="text-gray-600">Loading registration form...</p>
          </div>
        </div>
      </div>
    );
  }

  if (error && !submitting) {
    return (
      <div className="min-h-screen bg-gray-50">
        <AppHeader title="Register Interview Practice" showBackButton={true} />
        <div className="max-w-3xl mx-auto px-4 py-8">
          <div className="bg-white rounded-lg shadow-sm p-8">
            <div className="text-center">
              <div className="mb-4 p-4 bg-red-50 border border-red-200 rounded text-red-700">
                {error}
              </div>
              <button
                onClick={() => router.back()}
                className="px-6 py-3 bg-gray-600 text-white rounded-lg font-medium hover:bg-gray-700 transition-colors"
              >
                Go Back
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <AppHeader title="Register Interview Practice" showBackButton={true} />

      <div className="max-w-3xl mx-auto px-4 py-8">
        {/* Page Header */}
        <div className="mb-6">
          <h1 className="text-3xl font-bold text-gray-900 mb-2">Schedule Interview Practice</h1>
          <p className="text-gray-600">
            Configure your AI-powered interview practice session for{' '}
            {loading ? (
              <span className="text-gray-400 italic">Loading job details...</span>
            ) : (
              <>
                <strong className="text-indigo-700">{jobTitle}</strong>
                {jobCompany && <span> at <strong className="text-indigo-700">{jobCompany}</strong></span>}
              </>
            )}
          </p>

          {/* Job Details Card */}
          {!loading && jobDescription && (
            <div className="mt-4 p-4 bg-gradient-to-r from-indigo-50 to-blue-50 border border-indigo-200 rounded-lg">
              <div className="flex items-start gap-3">
                <svg className="w-5 h-5 text-indigo-600 mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 13.255A23.931 23.931 0 0112 15c-3.183 0-6.22-.62-9-1.745M16 6V4a2 2 0 00-2-2h-4a2 2 0 00-2 2v2m4 6h.01M5 20h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                </svg>
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-2">
                    <h3 className="text-sm font-semibold text-indigo-900">Job Details</h3>
                    {jobLocation && (
                      <span className="text-xs text-indigo-600 bg-indigo-100 px-2 py-0.5 rounded-full">
                        {jobLocation}
                      </span>
                    )}
                  </div>
                  <p className="text-sm text-gray-700 leading-relaxed whitespace-pre-line">
                    {truncateDescription(jobDescription, 200)}
                  </p>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Credits Warning */}
        {credits < 50 && (
          <div className="mb-6 p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
            <div className="flex items-start gap-3">
              <svg className="w-5 h-5 text-yellow-600 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
              </svg>
              <div>
                <p className="text-sm font-medium text-yellow-800">Insufficient Credits</p>
                <p className="text-sm text-yellow-700 mt-1">
                  You have {credits} credits, but need 50 credits to create an interview session.{' '}
                  <a href="/credits" className="underline font-medium">Buy credits</a>
                </p>
              </div>
            </div>
          </div>
        )}

        {/* Registration Form */}
        <form onSubmit={handleSubmit} className="bg-white rounded-lg shadow-sm p-8 space-y-6">
          {/* Experience Selection */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Which job experience do you want to practice for?
            </label>
            <select
              value={experienceId}
              onChange={(e) => setExperienceId(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
              required
            >
              <option value="">Select an experience...</option>
              {experiences.map((exp) => (
                <option key={exp.id} value={exp.id}>
                  {exp.jobTitle || 'Untitled Position'}{exp.companyName ? ` at ${exp.companyName}` : ''}
                  {exp.analyzedAt ? ` (Analyzed: ${formatAnalysisDate(exp.analyzedAt)})` : ''}
                </option>
              ))}
            </select>
            <p className="text-xs text-gray-500 mt-1">
              Choose the job experience from your resume to focus the interview on
            </p>
          </div>

          {/* Interview Type */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Interview Type
            </label>
            <div className="grid grid-cols-3 gap-3">
              {(['TECHNICAL', 'BEHAVIORAL', 'LEADERSHIP'] as const).map((type) => (
                <button
                  key={type}
                  type="button"
                  onClick={() => setInterviewType(type)}
                  className={`px-4 py-2 rounded-lg border-2 font-medium transition-colors ${
                    interviewType === type
                      ? 'border-indigo-600 bg-indigo-50 text-indigo-700'
                      : 'border-gray-300 bg-white text-gray-700 hover:border-gray-400'
                  }`}
                >
                  {type}
                </button>
              ))}
            </div>
          </div>

          {/* Difficulty Level */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Difficulty Level
            </label>
            <div className="grid grid-cols-4 gap-3">
              {(['JUNIOR', 'MID', 'SENIOR', 'STAFF'] as const).map((level) => (
                <button
                  key={level}
                  type="button"
                  onClick={() => setDifficultyLevel(level)}
                  className={`px-4 py-2 rounded-lg border-2 font-medium transition-colors ${
                    difficultyLevel === level
                      ? 'border-indigo-600 bg-indigo-50 text-indigo-700'
                      : 'border-gray-300 bg-white text-gray-700 hover:border-gray-400'
                  }`}
                >
                  {level}
                </button>
              ))}
            </div>
          </div>

          {/* Interrupt Level */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              AI Coaching Level
            </label>
            <div className="grid grid-cols-3 gap-3">
              {(['CONSERVATIVE', 'MODERATE', 'AGGRESSIVE'] as const).map((level) => (
                <button
                  key={level}
                  type="button"
                  onClick={() => setInterruptLevel(level)}
                  className={`px-4 py-2 rounded-lg border-2 font-medium transition-colors ${
                    interruptLevel === level
                      ? 'border-indigo-600 bg-indigo-50 text-indigo-700'
                      : 'border-gray-300 bg-white text-gray-700 hover:border-gray-400'
                  }`}
                >
                  {level}
                </button>
              ))}
            </div>
            <p className="text-xs text-gray-500 mt-2">
              Controls how frequently the AI provides real-time feedback during your answers
            </p>
          </div>

          {/* Schedule Settings */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Start Date
              </label>
              <input
                type="date"
                value={scheduledDate}
                onChange={(e) => setScheduledDate(e.target.value)}
                min={tomorrowStr}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Start Time
              </label>
              <input
                type="time"
                value={scheduledTime.slice(0, 5)}
                onChange={(e) => setScheduledTime(e.target.value + ':00')}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                required
              />
            </div>
          </div>

          {/* Timezone */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Timezone
            </label>
            <input
              type="text"
              value={timezone}
              onChange={(e) => setTimezone(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
              placeholder="UTC"
              required
            />
            <p className="text-xs text-gray-500 mt-1">
              Auto-detected: {timezone}
            </p>
          </div>

          {/* Rounds Configuration */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Total Rounds
              </label>
              <input
                type="number"
                min="1"
                max="10"
                value={totalRounds}
                onChange={(e) => setTotalRounds(parseInt(e.target.value))}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                required
              />
              <p className="text-xs text-gray-500 mt-1">
                Number of practice rounds (1-10)
              </p>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Round Interval
              </label>
              <select
                value={roundInterval}
                onChange={(e) => setRoundInterval(e.target.value as 'DAILY' | 'EVERY_2_DAYS' | 'WEEKLY')}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                required
              >
                <option value="DAILY">Daily</option>
                <option value="EVERY_2_DAYS">Every 2 Days</option>
                <option value="WEEKLY">Weekly</option>
              </select>
              <p className="text-xs text-gray-500 mt-1">
                Time between practice rounds
              </p>
            </div>
          </div>

          {/* Cost Summary */}
          <div className="p-4 bg-indigo-50 border border-indigo-200 rounded-lg">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-indigo-900">Total Cost:</span>
              <span className="text-lg font-bold text-indigo-900">50 Credits</span>
            </div>
            <p className="text-xs text-indigo-700 mt-1">
              Available credits: {credits}
            </p>
          </div>

          {/* Error Display */}
          {error && (
            <div className="p-4 bg-red-50 border border-red-200 rounded-lg">
              <p className="text-sm text-red-700">{error}</p>
            </div>
          )}

          {/* Submit Button */}
          <div className="flex gap-4">
            <button
              type="button"
              onClick={() => router.back()}
              className="flex-1 px-6 py-3 bg-gray-100 text-gray-700 rounded-lg font-medium hover:bg-gray-200 transition-colors"
              disabled={submitting}
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting || credits < 50}
              className="flex-1 px-6 py-3 bg-indigo-600 text-white rounded-lg font-medium hover:bg-indigo-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {submitting ? 'Creating Session...' : 'Create Interview Session'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
