'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import AppHeader from '@/components/AppHeader';
import { listResumes, getUserCredits, tailorResume, getTailoringCost, getTailoredHistory, getSavedTailoredResume, downloadTailoredResume } from '@/lib/api';
import { getCurrentUserId } from '@/lib/userUtils';
import ReactMarkdown from 'react-markdown';

interface Resume {
  id: string;
  filename: string;
  status: string;
}

interface TailoredResponse {
  id?: string;
  resumeId: string;
  tailoredContent: string;
  coverLetter?: string;
  highlightedSkills: string[];
  matchedKeywords: string[];
  relevanceScore: number;
  creditsUsed: number;
  createdAt?: string;
  jobDescriptionPreview?: string;
}

export default function TailorPage() {
  const router = useRouter();
  const { user, loading: authLoading } = useAuth();

  // Form state
  const [resumes, setResumes] = useState<Resume[]>([]);
  const [selectedResumeId, setSelectedResumeId] = useState<string>('');
  const [jobDescription, setJobDescription] = useState<string>('');
  const [guidelines, setGuidelines] = useState<string>('');

  // UI state
  const [loading, setLoading] = useState(false);
  const [loadingResumes, setLoadingResumes] = useState(true);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<TailoredResponse | null>(null);
  const [history, setHistory] = useState<TailoredResponse[]>([]);
  const [showCoverLetter, setShowCoverLetter] = useState(false);
  const [copiedCoverLetter, setCopiedCoverLetter] = useState(false);

  // Credit state
  const [credits, setCredits] = useState<any>(null);
  const [tailoringCost, setTailoringCost] = useState<number>(30);

  // Redirect if not authenticated
  useEffect(() => {
    if (!authLoading && !user) {
      router.push('/login');
    }
  }, [authLoading, user, router]);

  // Load resumes and credits
  useEffect(() => {
    if (user) {
      loadResumesAndCredits();
    }
  }, [user]);

  // Load history when resume is selected
  useEffect(() => {
    if (selectedResumeId) {
      loadHistory(selectedResumeId);
    } else {
      setHistory([]);
    }
  }, [selectedResumeId]);

  const loadResumesAndCredits = async () => {
    try {
      const [resumeList, userCredits, costResponse] = await Promise.all([
        listResumes(),
        getUserCredits(getCurrentUserId()),
        getTailoringCost()
      ]);

      // Filter to only ANALYZED resumes
      const analyzedResumes = resumeList.filter((r: Resume) => r.status === 'ANALYZED');
      setResumes(analyzedResumes);
      setCredits(userCredits);
      setTailoringCost(costResponse.cost);

      // Auto-select first resume if only one
      if (analyzedResumes.length === 1) {
        setSelectedResumeId(analyzedResumes[0].id);
      }
    } catch (err) {
      console.error('Failed to load data:', err);
      setError('Failed to load resumes');
    } finally {
      setLoadingResumes(false);
    }
  };

  const loadHistory = async (resumeId: string) => {
    try {
      setLoadingHistory(true);
      const historyData = await getTailoredHistory(resumeId);
      setHistory(historyData);
    } catch (err) {
      console.error('Failed to load history:', err);
      // Don't show error - history is not critical
    } finally {
      setLoadingHistory(false);
    }
  };

  const handleTailor = async () => {
    if (!selectedResumeId) {
      setError('Please select a resume');
      return;
    }
    if (!jobDescription || jobDescription.length < 50) {
      setError('Job description must be at least 50 characters');
      return;
    }

    setLoading(true);
    setError(null);
    setResult(null);
    setShowCoverLetter(false);

    try {
      const response = await tailorResume(selectedResumeId, jobDescription, guidelines);
      setResult(response);

      // Reload credits and history
      const [updatedCredits] = await Promise.all([
        getUserCredits(getCurrentUserId()),
        loadHistory(selectedResumeId)
      ]);
      setCredits(updatedCredits);
    } catch (err: any) {
      console.error('Tailoring failed:', err);
      if (err.response?.status === 402) {
        setError(err.response.data.message || 'Insufficient credits');
      } else {
        setError(err.response?.data?.error || 'Failed to tailor resume');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleDownload = async () => {
    if (!selectedResumeId || !result) return;

    try {
      setLoading(true);
      // Send the already-generated result - no LLM call, no credits
      await downloadTailoredResume(selectedResumeId, result);
    } catch (err: any) {
      console.error('Download failed:', err);
      setError('Failed to download resume');
    } finally {
      setLoading(false);
    }
  };

  const handleLoadSaved = async (tailoredId: string) => {
    if (!selectedResumeId) return;

    try {
      setLoading(true);
      setError(null);
      const saved = await getSavedTailoredResume(selectedResumeId, tailoredId);
      setResult(saved);
      setShowCoverLetter(false);
    } catch (err: any) {
      console.error('Failed to load saved result:', err);
      setError('Failed to load saved result');
    } finally {
      setLoading(false);
    }
  };

  const handleCopyCoverLetter = async () => {
    if (!result?.coverLetter) return;

    try {
      await navigator.clipboard.writeText(result.coverLetter);
      setCopiedCoverLetter(true);
      setTimeout(() => setCopiedCoverLetter(false), 2000);
    } catch (err) {
      console.error('Failed to copy:', err);
    }
  };

  const formatDate = (dateStr: string | undefined) => {
    if (!dateStr) return '';
    try {
      return new Date(dateStr).toLocaleDateString('en-GB', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      });
    } catch {
      return dateStr;
    }
  };

  // Show loading if auth is still loading
  if (authLoading || !user) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  const hasEnoughCredits = credits && parseFloat(credits.availableCredits) >= tailoringCost;

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100">
      <AppHeader title="Tailor Resume" showBackButton={true} />

      <div className="max-w-7xl mx-auto px-4 py-8">
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Left Panel - Input Form */}
          <div className="bg-white rounded-xl shadow-lg p-6">
            <h2 className="text-2xl font-bold text-gray-900 mb-6">Tailor Your Resume</h2>

            {/* Resume Selection */}
            <div className="mb-6">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Select Resume
              </label>
              {loadingResumes ? (
                <div className="animate-pulse h-10 bg-gray-200 rounded-lg"></div>
              ) : resumes.length === 0 ? (
                <div className="p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
                  <p className="text-yellow-800">
                    No analyzed resumes found. Please{' '}
                    <button
                      onClick={() => router.push('/upload')}
                      className="text-blue-600 underline"
                    >
                      upload and analyze a resume
                    </button>{' '}
                    first.
                  </p>
                </div>
              ) : (
                <select
                  value={selectedResumeId}
                  onChange={(e) => {
                    setSelectedResumeId(e.target.value);
                    setResult(null);
                  }}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                >
                  <option value="">Select a resume...</option>
                  {resumes.map((resume) => (
                    <option key={resume.id} value={resume.id}>
                      {resume.filename}
                    </option>
                  ))}
                </select>
              )}
            </div>

            {/* History Panel */}
            {selectedResumeId && (
              <div className="mb-6">
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Previous Tailorings {history.length > 0 && `(${history.length})`}
                </label>
                {loadingHistory ? (
                  <div className="animate-pulse h-20 bg-gray-100 rounded-lg"></div>
                ) : history.length === 0 ? (
                  <p className="text-sm text-gray-500 italic">No previous tailorings for this resume</p>
                ) : (
                  <div className="max-h-40 overflow-y-auto border border-gray-200 rounded-lg">
                    {history.map((item) => (
                      <button
                        key={item.id}
                        onClick={() => handleLoadSaved(item.id!)}
                        className={`w-full text-left px-3 py-2 hover:bg-gray-50 border-b border-gray-100 last:border-b-0 ${
                          result?.id === item.id ? 'bg-blue-50' : ''
                        }`}
                      >
                        <div className="flex justify-between items-center">
                          <span className={`text-sm font-medium ${
                            item.relevanceScore >= 75 ? 'text-green-600' :
                            item.relevanceScore >= 50 ? 'text-yellow-600' : 'text-red-600'
                          }`}>
                            {item.relevanceScore}%
                          </span>
                          <span className="text-xs text-gray-400">{formatDate(item.createdAt)}</span>
                        </div>
                        <p className="text-xs text-gray-500 truncate mt-1">
                          {item.jobDescriptionPreview || 'Job description...'}
                        </p>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* Job Description */}
            <div className="mb-6">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Job Description <span className="text-red-500">*</span>
              </label>
              <textarea
                value={jobDescription}
                onChange={(e) => setJobDescription(e.target.value)}
                placeholder="Paste the full job description here..."
                rows={10}
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none"
              />
              <p className="text-xs text-gray-500 mt-1">
                {jobDescription.length} / 20000 characters (min 50)
              </p>
            </div>

            {/* Guidelines (Optional) */}
            <div className="mb-6">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Guidelines (Optional)
              </label>
              <textarea
                value={guidelines}
                onChange={(e) => setGuidelines(e.target.value)}
                placeholder="E.g., 'Focus on AI/ML experience', 'Emphasize leadership'..."
                rows={2}
                maxLength={500}
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none"
              />
            </div>

            {/* Credit Info */}
            <div className={`p-4 rounded-lg border mb-6 ${hasEnoughCredits ? 'bg-blue-50 border-blue-200' : 'bg-yellow-50 border-yellow-300'}`}>
              <div className="flex items-center justify-between">
                <div>
                  <p className={`font-medium ${hasEnoughCredits ? 'text-blue-900' : 'text-yellow-900'}`}>
                    Cost: {tailoringCost} credits
                  </p>
                  {credits && (
                    <p className={`text-sm ${hasEnoughCredits ? 'text-blue-700' : 'text-yellow-700'}`}>
                      Available: {parseFloat(credits.availableCredits || 0).toFixed(0)} credits
                    </p>
                  )}
                </div>
                {!hasEnoughCredits && (
                  <button
                    onClick={() => router.push('/credits')}
                    className="px-4 py-2 bg-yellow-600 text-white rounded-lg text-sm font-medium hover:bg-yellow-700"
                  >
                    Buy Credits
                  </button>
                )}
              </div>
            </div>

            {/* Error Message */}
            {error && (
              <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg">
                <p className="text-red-800">{error}</p>
              </div>
            )}

            {/* Generate Button */}
            <button
              onClick={handleTailor}
              disabled={loading || !selectedResumeId || jobDescription.length < 50 || !hasEnoughCredits}
              className="w-full px-6 py-3 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
            >
              {loading ? (
                <span className="flex items-center justify-center">
                  <svg className="animate-spin -ml-1 mr-3 h-5 w-5 text-white" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  Tailoring...
                </span>
              ) : (
                `Generate Tailored Resume (${tailoringCost} credits)`
              )}
            </button>
          </div>

          {/* Middle Panel - Results */}
          <div className="bg-white rounded-xl shadow-lg p-6">
            <h2 className="text-2xl font-bold text-gray-900 mb-6">Tailored Resume</h2>

            {!result && !loading && (
              <div className="flex flex-col items-center justify-center h-96 text-gray-400">
                <svg className="w-16 h-16 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                </svg>
                <p>Your tailored resume will appear here</p>
              </div>
            )}

            {loading && (
              <div className="flex flex-col items-center justify-center h-96">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mb-4"></div>
                <p className="text-gray-600">Analyzing and tailoring your resume...</p>
                <p className="text-sm text-gray-400 mt-2">This may take 10-15 seconds</p>
              </div>
            )}

            {result && (
              <div className="space-y-6">
                {/* Relevance Score */}
                <div className="flex items-center justify-between p-4 bg-gray-50 rounded-lg">
                  <div>
                    <p className="text-sm text-gray-600">Relevance Score</p>
                    <p className={`text-3xl font-bold ${
                      result.relevanceScore >= 75 ? 'text-green-600' :
                      result.relevanceScore >= 50 ? 'text-yellow-600' : 'text-red-600'
                    }`}>
                      {result.relevanceScore}%
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-sm text-gray-600">Credits Used</p>
                    <p className="text-xl font-semibold text-gray-900">{result.creditsUsed}</p>
                  </div>
                </div>

                {/* Highlighted Skills */}
                {result.highlightedSkills && result.highlightedSkills.length > 0 && (
                  <div>
                    <h3 className="text-sm font-medium text-gray-700 mb-2">Highlighted Skills</h3>
                    <div className="flex flex-wrap gap-2">
                      {result.highlightedSkills.map((skill, idx) => (
                        <span
                          key={idx}
                          className="px-3 py-1 bg-green-100 text-green-800 text-sm rounded-full"
                        >
                          {skill}
                        </span>
                      ))}
                    </div>
                  </div>
                )}

                {/* Matched Keywords */}
                {result.matchedKeywords && result.matchedKeywords.length > 0 && (
                  <div>
                    <h3 className="text-sm font-medium text-gray-700 mb-2">Matched Keywords</h3>
                    <div className="flex flex-wrap gap-2">
                      {result.matchedKeywords.map((keyword, idx) => (
                        <span
                          key={idx}
                          className="px-2 py-1 bg-blue-100 text-blue-800 text-xs rounded"
                        >
                          {keyword}
                        </span>
                      ))}
                    </div>
                  </div>
                )}

                {/* Download Button */}
                <button
                  onClick={handleDownload}
                  disabled={loading}
                  className="w-full px-6 py-3 bg-green-600 text-white rounded-lg font-medium hover:bg-green-700 disabled:bg-gray-400 transition-colors flex items-center justify-center gap-2"
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                  </svg>
                  Download as Word (.docx)
                </button>

                {/* Tailored Content Preview */}
                <div>
                  <h3 className="text-sm font-medium text-gray-700 mb-2">Resume Preview</h3>
                  <div className="border border-gray-200 rounded-lg p-4 max-h-80 overflow-y-auto bg-white prose prose-sm max-w-none">
                    <ReactMarkdown>{result.tailoredContent}</ReactMarkdown>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Right Panel - Cover Letter */}
          <div className="bg-white rounded-xl shadow-lg p-6">
            <h2 className="text-2xl font-bold text-gray-900 mb-6">Cover Letter</h2>

            {!result && !loading && (
              <div className="flex flex-col items-center justify-center h-96 text-gray-400">
                <svg className="w-16 h-16 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                </svg>
                <p>Your cover letter will appear here</p>
              </div>
            )}

            {loading && (
              <div className="flex flex-col items-center justify-center h-96">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-purple-600 mb-4"></div>
                <p className="text-gray-600">Generating cover letter...</p>
              </div>
            )}

            {result && (
              <div className="space-y-6">
                {result.coverLetter ? (
                  <>
                    {/* Copy Button */}
                    <button
                      onClick={handleCopyCoverLetter}
                      className="w-full px-4 py-2 bg-purple-600 text-white rounded-lg font-medium hover:bg-purple-700 transition-colors flex items-center justify-center gap-2"
                    >
                      {copiedCoverLetter ? (
                        <>
                          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                          </svg>
                          Copied!
                        </>
                      ) : (
                        <>
                          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 5H6a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2v-1M8 5a2 2 0 002 2h2a2 2 0 002-2M8 5a2 2 0 012-2h2a2 2 0 012 2m0 0h2a2 2 0 012 2v3m2 4H10m0 0l3-3m-3 3l3 3" />
                          </svg>
                          Copy Cover Letter
                        </>
                      )}
                    </button>

                    {/* Cover Letter Content */}
                    <div className="border border-gray-200 rounded-lg p-4 max-h-96 overflow-y-auto bg-gray-50">
                      <div className="whitespace-pre-wrap text-sm text-gray-700 font-serif">
                        {result.coverLetter}
                      </div>
                    </div>
                  </>
                ) : (
                  <div className="flex flex-col items-center justify-center h-64 text-gray-400">
                    <p>No cover letter generated</p>
                    <p className="text-sm mt-2">Try generating a new tailored resume</p>
                  </div>
                )}

                {/* Saved indicator */}
                {result.id && (
                  <div className="flex items-center gap-2 text-sm text-gray-500">
                    <svg className="w-4 h-4 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                    </svg>
                    <span>Saved - access anytime from history (no credits)</span>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
