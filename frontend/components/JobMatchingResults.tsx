'use client';

import { useState, useEffect } from 'react';
import { getMatchingResults, toggleMatchSaved, markMatchApplied, toggleMatchRedflag } from '@/lib/api';
import { JobMatchingResultsResponse, JobMatchResult, JobListing } from '@/lib/types';
import SkillFilterCloud from './SkillFilterCloud';
import SkillDrilldownModal from './SkillDrilldownModal';
import SkillHeatmap from './SkillHeatmap';

interface JobMatchingResultsProps {
  profileId: string;
}

type SortField = 'matchScore' | 'skillMatch' | 'postedDate' | 'fetchedDate';
type SortDirection = 'asc' | 'desc';

export default function JobMatchingResults({ profileId }: JobMatchingResultsProps) {
  const [results, setResults] = useState<JobMatchingResultsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [isManualRefresh, setIsManualRefresh] = useState(false); // Track manual button click
  const [error, setError] = useState<string | null>(null);
  const [sortField, setSortField] = useState<SortField>('matchScore');
  const [sortDirection, setSortDirection] = useState<SortDirection>('desc');
  const [forceRefresh, setForceRefresh] = useState(false);

  // Skill drilldown state
  const [isDrilldownModalOpen, setIsDrilldownModalOpen] = useState(false);
  const [selectedSkillForDrilldown, setSelectedSkillForDrilldown] = useState<string | undefined>();
  const [selectedSkillsForDrilldown, setSelectedSkillsForDrilldown] = useState<string[] | undefined>(); // For heatmap (multiple skills)
  const [graphDiscoveredJobs, setGraphDiscoveredJobs] = useState<JobListing[] | null>(null);
  const [selectedSkillsForDisplay, setSelectedSkillsForDisplay] = useState<string[]>([]);

  // Graph Search collapseable state
  const [isGraphSearchCollapsed, setIsGraphSearchCollapsed] = useState(() => {
    if (typeof window !== 'undefined') {
      const saved = localStorage.getItem('jobMatching_graphSearchCollapsed');
      return saved ? saved === 'true' : false; // Default to expanded
    }
    return false;
  });

  // Load topK and maxDaysOld from localStorage (persist user preferences)
  const [topK, setTopK] = useState(() => {
    if (typeof window !== 'undefined') {
      const saved = localStorage.getItem('jobMatching_topK');
      return saved ? parseInt(saved) : 200;
    }
    return 200;
  });

  const [maxDaysOld, setMaxDaysOld] = useState(() => {
    if (typeof window !== 'undefined') {
      const saved = localStorage.getItem('jobMatching_maxDaysOld');
      return saved ? parseInt(saved) : 30;
    }
    return 30;
  });

  // Save preferences to localStorage when changed
  useEffect(() => {
    if (typeof window !== 'undefined') {
      localStorage.setItem('jobMatching_topK', topK.toString());
    }
  }, [topK]);

  useEffect(() => {
    if (typeof window !== 'undefined') {
      localStorage.setItem('jobMatching_maxDaysOld', maxDaysOld.toString());
    }
  }, [maxDaysOld]);

  // Save graph search collapsed state to localStorage
  useEffect(() => {
    if (typeof window !== 'undefined') {
      localStorage.setItem('jobMatching_graphSearchCollapsed', isGraphSearchCollapsed.toString());
    }
  }, [isGraphSearchCollapsed]);

  // Auto-load existing results from job_match table on mount (fast!)
  // This shows cached results immediately without expensive vector search
  useEffect(() => {
    // Small delay to ensure backend services are fully initialized
    const timer = setTimeout(() => {
      handleSearch(false); // Load cached results from job_match table
    }, 100);
    return () => clearTimeout(timer);
  }, [profileId]);

  const handleSearch = async (refresh?: boolean, retryCount: number = 0, isManual: boolean = false) => {
    setLoading(true);
    if (isManual) {
      setIsManualRefresh(true); // User clicked button
    }
    setError(null);
    try {
      // If refresh is explicitly passed, use it; otherwise use forceRefresh checkbox state
      const shouldRefresh = refresh !== undefined ? refresh : forceRefresh;
      const data = await getMatchingResults(profileId, topK, shouldRefresh, maxDaysOld);

      // Null safety check
      if (data && data.matches) {
        setResults(data);
      } else {
        throw new Error('Invalid response from server');
      }
    } catch (err: any) {
      console.error('Failed to fetch matching results:', err);

      // Retry once after 1 second on first failure (transient errors)
      if (retryCount === 0 && err?.response?.status === 500) {
        console.log('Retrying search after 500 error...');
        setTimeout(() => {
          handleSearch(refresh, retryCount + 1, isManual);
        }, 1000);
        return; // Skip finally block for retry case
      } else {
        setError('Failed to fetch matching jobs. Please try again.');
      }
    } finally {
      // Always reset loading states (fix: button stuck in "Refreshing..." state)
      setLoading(false);
      setIsManualRefresh(false);
    }
  };

  const handleToggleSaved = async (matchId: string, newRating: number) => {
    try {
      // Directly set the rating (0-3) - no more cycling
      await toggleMatchSaved(matchId, newRating);
      // Update local state
      if (results) {
        const updatedMatches = results.matches.map(match =>
          match.matchId === matchId ? { ...match, isSaved: newRating } : match
        );
        setResults({ ...results, matches: updatedMatches });
      }
    } catch (err) {
      console.error('Failed to update saved rating:', err);
    }
  };

  const handleMarkApplied = async (matchId: string) => {
    try {
      await markMatchApplied(matchId);
      // Update local state
      if (results) {
        const updatedMatches = results.matches.map(match =>
          match.matchId === matchId ? { ...match, isApplied: true, flaggedAt: new Date().toISOString() } : match
        );
        setResults({ ...results, matches: updatedMatches });
      }
    } catch (err) {
      console.error('Failed to mark as applied:', err);
    }
  };

  const handleToggleRedflag = async (matchId: string, currentlyRedflagged: boolean) => {
    try {
      await toggleMatchRedflag(matchId, !currentlyRedflagged);
      // Update local state
      if (results) {
        const updatedMatches = results.matches.map(match =>
          match.matchId === matchId ? { ...match, isRedflag: !currentlyRedflagged } : match
        );
        setResults({ ...results, matches: updatedMatches });
      }
    } catch (err) {
      console.error('Failed to toggle redflag:', err);
    }
  };

  const getMatchLevelColor = (level: string) => {
    switch (level) {
      case 'STRONG':
        return 'bg-green-100 text-green-800 border-green-300';
      case 'GOOD':
        return 'bg-yellow-100 text-yellow-800 border-yellow-300';
      case 'MODERATE':
        return 'bg-orange-100 text-orange-800 border-orange-300';
      default:
        return 'bg-gray-100 text-gray-800 border-gray-300';
    }
  };

  const formatDate = (dateStr: string) => {
    try {
      return new Date(dateStr).toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric'
      });
    } catch {
      return dateStr;
    }
  };

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortDirection('desc');
    }
  };

  const getSortedMatches = () => {
    if (!results) return [];

    const sorted = [...results.matches].sort((a, b) => {
      let comparison = 0;

      switch (sortField) {
        case 'matchScore':
          comparison = a.similarityScore - b.similarityScore;
          break;
        case 'skillMatch':
          comparison = a.weightedSkillScore - b.weightedSkillScore;
          break;
        case 'postedDate':
          comparison = new Date(a.postedDate).getTime() - new Date(b.postedDate).getTime();
          break;
        case 'fetchedDate':
          comparison = new Date(a.fetchedAt).getTime() - new Date(b.fetchedAt).getTime();
          break;
      }

      return sortDirection === 'asc' ? comparison : -comparison;
    });

    return sorted;
  };

  const renderTableRow = (match: JobMatchResult) => {
    const isCollapsed = match.isRedflag;

    return (
      <tr
        key={match.matchId}
        onClick={() => isCollapsed && handleToggleRedflag(match.matchId, match.isRedflag)}
        className={`transition-all ${
          isCollapsed
            ? 'bg-red-50 cursor-pointer hover:bg-red-100'
            : match.isSaved > 0
            ? 'bg-yellow-50 hover:bg-yellow-100'
            : 'hover:bg-gray-50'
        }`}
      >
        {isCollapsed ? (
          /* Collapsed view - thin single column */
          <>
            <td colSpan={7} className="px-3 py-2">
              <div className="flex items-center justify-between text-sm text-red-600">
                <div className="flex items-center gap-2">
                  <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 21v-4m0 0V5a2 2 0 012-2h6.5l1 1H21l-3 6 3 6h-8.5l-1-1H5a2 2 0 00-2 2zm9-13.5V9" />
                  </svg>
                  <span className="font-medium">{match.title}</span>
                  <span className="text-gray-500">at {match.company}</span>
                </div>
                <span className="text-xs text-gray-500">Click to expand</span>
              </div>
            </td>
          </>
        ) : (
          /* Expanded view - full details */
          <>
            {/* Job Details */}
            <td className="px-3 py-2">
              <div className="max-w-xs">
                <a
                  href={match.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-sm font-medium text-indigo-600 hover:text-indigo-800 block underline line-clamp-2"
                  onClick={(e) => e.stopPropagation()}
                  title={match.title}
                >
                  {match.title}
                </a>
                <div className="text-sm text-gray-500 truncate">
                  {match.company}
                </div>
                <div className="text-xs text-gray-400 truncate mt-1">
                  {match.location}
                </div>
                {match.salaryRange && (
                  <div className="text-xs text-green-600 font-medium mt-1 truncate">
                    {match.salaryRange}
                  </div>
                )}
              </div>
            </td>

            {/* Match Score */}
            <td className="px-2 py-2 text-center">
              <div className="text-sm font-medium text-gray-900">
                {(match.similarityScore * 100).toFixed(1)}%
              </div>
            </td>

            {/* Skills Match */}
            <td className="px-2 py-2 text-center">
              <div className="flex flex-col gap-0.5">
                <div className="text-sm font-medium text-gray-900">
                  {Math.round(match.skillMatchPercentage)}%
                </div>
                <div className="text-xs text-gray-500">
                  W:{Math.round(match.weightedSkillScore)}%
                </div>
              </div>
            </td>

            {/* Matched Skills */}
            <td className="px-3 py-2">
              <div className="space-y-1">
                {/* Matched Skills */}
                <div className="text-sm text-green-700">
                  {match.matchedSkills.slice(0, 5).join(', ')}
                  {match.matchedSkills.length > 5 && (
                    <span className="text-gray-500">
                      {' '}+{match.matchedSkills.length - 5} more
                    </span>
                  )}
                </div>

                {/* Deal Breaker Keywords - shown below matched skills */}
                {match.matchedNegativeKeywords && match.matchedNegativeKeywords.length > 0 && (
                  <div className="flex flex-wrap gap-1 pt-1 border-t border-red-200">
                    {match.matchedNegativeKeywords.map((keyword, idx) => (
                      <span
                        key={idx}
                        className="inline-flex items-center gap-1 px-2 py-0.5 bg-red-100 text-red-700 border border-red-300 rounded text-xs font-medium"
                        title={`Deal-breaker: ${keyword}`}
                      >
                        <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                        </svg>
                        {keyword}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </td>

            {/* Posted Date */}
            <td className="px-3 py-2 text-sm text-gray-500 whitespace-nowrap">
              {formatDate(match.postedDate)}
            </td>

            {/* Fetched Date */}
            <td className="px-3 py-2 text-sm text-gray-500 whitespace-nowrap">
              {formatDate(match.fetchedAt)}
            </td>

            {/* Actions Column - Merged 3 buttons */}
            <td className="px-3 py-2" onClick={(e) => e.stopPropagation()}>
              <div className="flex items-center justify-center gap-2">
                {/* Saved Button - 3 Star Rating (Click individual star to set rating) */}
                <div className="flex items-center gap-0.5 p-1.5 rounded-lg bg-gray-50">
                  {[1, 2, 3].map((star) => (
                    <button
                      key={star}
                      onClick={() => handleToggleSaved(match.matchId, star === match.isSaved ? 0 : star)}
                      className={`transition-colors ${
                        star <= match.isSaved
                          ? 'text-yellow-600 hover:text-yellow-700'
                          : 'text-gray-300 hover:text-yellow-500'
                      }`}
                      title={
                        star === match.isSaved
                          ? `Remove ${star}-star rating`
                          : star <= match.isSaved
                          ? `Change to ${star}-star rating`
                          : `Set ${star}-star rating`
                      }
                    >
                      <svg
                        className="w-4 h-4"
                        fill={star <= match.isSaved ? 'currentColor' : 'none'}
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                      >
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z" />
                      </svg>
                    </button>
                  ))}
                </div>

                {/* Applied Button */}
                <button
                  onClick={() => !match.isApplied && handleMarkApplied(match.matchId)}
                  disabled={match.isApplied}
                  className={`p-1.5 rounded-lg transition-colors ${
                    match.isApplied
                      ? 'text-green-600 bg-green-50 cursor-default'
                      : 'text-gray-400 hover:text-green-600 hover:bg-green-50'
                  }`}
                  title={match.isApplied ? 'Applied (kept 20 days)' : 'Mark as applied (kept 20 days)'}
                >
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                </button>

                {/* RedFlag Button */}
                <button
                  onClick={() => handleToggleRedflag(match.matchId, match.isRedflag)}
                  className={`p-1.5 rounded-lg transition-colors ${
                    match.isRedflag
                      ? 'text-red-600 bg-red-100 hover:bg-red-200'
                      : 'text-gray-400 hover:text-red-600 hover:bg-red-50'
                  }`}
                  title={match.isRedflag ? 'Click to unflag' : 'Not interested (kept 7 days)'}
                >
                  <svg className="w-4 h-4" fill={match.isRedflag ? 'currentColor' : 'none'} stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 21v-4m0 0V5a2 2 0 012-2h6.5l1 1H21l-3 6 3 6h-8.5l-1-1H5a2 2 0 00-2 2zm9-13.5V9" />
                  </svg>
                </button>
              </div>
            </td>
          </>
        )}
      </tr>
    );
  };

  const SortIcon = ({ field }: { field: SortField }) => {
    if (sortField !== field) {
      return (
        <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4" />
        </svg>
      );
    }
    return sortDirection === 'asc' ? (
      <svg className="w-4 h-4 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 15l7-7 7 7" />
      </svg>
    ) : (
      <svg className="w-4 h-4 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
      </svg>
    );
  };

  // Skill drilldown handlers
  const handleSkillClick = (skillName: string) => {
    setSelectedSkillForDrilldown(skillName);
    setIsDrilldownModalOpen(true);
  };

  const handleViewJobsFromDrilldown = async (jobs: JobListing[], selectedSkills: string[]) => {
    // Fetch existing matches to preserve user actions (saved/applied/redflag)
    let existingMatches: JobMatchingResultsResponse | null = null;
    try {
      existingMatches = await getMatchingResults(profileId, 9999, false, 0); // Get all existing matches
    } catch (error) {
      console.log('No existing matches found, creating fresh results');
    }

    // Create a map of listingId -> existing match for quick lookup
    const existingMatchMap = new Map<string, JobMatchResult>();
    if (existingMatches) {
      existingMatches.matches.forEach(match => {
        existingMatchMap.set(match.listingId, match);
      });
    }

    // Convert JobListing to JobMatchResult format, preserving existing user actions
    const convertedResults: JobMatchingResultsResponse = {
      profileId: profileId,
      totalMatches: jobs.length,
      profileSummary: `Graph-based discovery for skills: ${selectedSkills.join(', ')}`,
      matches: jobs.map((job) => {
        const existingMatch = existingMatchMap.get(job.id);
        return {
          matchId: existingMatch?.matchId || `graph-${job.id}`,
          profileId: profileId,
          listingId: job.id,
          similarityScore: 0, // No vector score for graph-based
          matchLevel: 'MODERATE' as const,
          title: job.title,
          company: job.company,
          location: job.location,
          description: job.description,
          url: job.url,
          salaryRange: job.salaryRange,
          postedDate: job.postedDate || job.fetchedAt,
          fetchedAt: job.fetchedAt,
          matchedSkills: selectedSkills, // Show selected skills as matched
          missingSkills: [],
          skillMatchPercentage: 100, // All selected skills matched by definition
          weightedSkillScore: 100,
          // Preserve user actions from existing match
          isSaved: existingMatch?.isSaved || 0,
          isApplied: existingMatch?.isApplied || false,
          isRedflag: existingMatch?.isRedflag || false,
          flaggedAt: existingMatch?.flaggedAt,
          matchedNegativeKeywords: []
        };
      })
    };

    setResults(convertedResults);
    setSelectedSkillsForDisplay(selectedSkills);
    setGraphDiscoveredJobs(null); // Clear graph jobs since we're using results now
  };

  const handleBackToVectorSearch = () => {
    setGraphDiscoveredJobs(null);
    setSelectedSkillsForDisplay([]);
    handleSearch(false); // Reload vector search results
  };

  if (loading && !results && !graphDiscoveredJobs) {
    return (
      <div className="bg-white border border-gray-200 rounded-lg p-12 text-center">
        <div className="flex flex-col items-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600 mb-4"></div>
          <p className="text-gray-600">Loading matching jobs...</p>
        </div>
      </div>
    );
  }

  if (error && !results && !graphDiscoveredJobs) {
    return (
      <div className="bg-white border border-gray-200 rounded-lg p-8 text-center">
        <div className="mb-4 p-4 bg-red-50 border border-red-200 rounded text-red-700">
          {error}
        </div>
        <button
          onClick={() => handleSearch()}
          className="px-6 py-3 bg-indigo-600 text-white rounded-lg font-medium hover:bg-indigo-700 transition-colors"
        >
          Retry
        </button>
      </div>
    );
  }

  if (!results && !graphDiscoveredJobs) {
    // Show empty state with controls - user must click "Refresh Results" to search
    return (
      <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
        {/* Header */}
        <div className="px-6 py-4 bg-gray-50 border-b border-gray-200">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-lg font-semibold text-gray-900">
                Job Matching Results
              </h3>
              <p className="text-sm text-gray-600 mt-1">
                Configure your search and click "Refresh Results"
              </p>
            </div>
            <div className="flex items-center gap-3">
              <label className="flex items-center gap-2 text-sm text-gray-700">
                <span className="text-gray-600">Top</span>
                <select
                  value={topK}
                  onChange={(e) => setTopK(parseInt(e.target.value))}
                  className="px-3 py-1 text-sm border border-gray-300 rounded focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                >
                  <option value="50">50</option>
                  <option value="100">100</option>
                  <option value="200">200</option>
                  <option value="300">300</option>
                  <option value="500">500</option>
                  <option value="9999">ALL</option>
                </select>
                <span className="text-gray-600">jobs</span>
              </label>
              <label className="flex items-center gap-2 text-sm text-gray-700">
                <span className="text-gray-600">from last</span>
                <select
                  value={maxDaysOld}
                  onChange={(e) => setMaxDaysOld(parseInt(e.target.value))}
                  className="px-3 py-1 text-sm border border-gray-300 rounded focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                >
                  <option value="7">7 days</option>
                  <option value="14">2 weeks</option>
                  <option value="30">1 month</option>
                  <option value="60">2 months</option>
                  <option value="90">3 months</option>
                  <option value="0">All time</option>
                </select>
              </label>
              <label className="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
                <input
                  type="checkbox"
                  checked={forceRefresh}
                  onChange={(e) => setForceRefresh(e.target.checked)}
                  className="w-4 h-4 text-indigo-600 border-gray-300 rounded focus:ring-indigo-500"
                />
                <span>Force Refresh</span>
              </label>
              <button
                onClick={() => handleSearch(undefined, 0, true)}
                disabled={isManualRefresh}
                className="px-4 py-2 bg-indigo-600 text-white text-sm rounded-lg font-medium hover:bg-indigo-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isManualRefresh ? 'Searching...' : 'Refresh Results'}
              </button>
            </div>
          </div>
        </div>

        {/* Empty state message */}
        <div className="px-6 py-16 text-center">
          <svg className="mx-auto h-16 w-16 text-gray-400 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
          <h3 className="text-lg font-medium text-gray-900 mb-2">No matching results found yet</h3>
          <p className="text-gray-600 mb-6 max-w-md mx-auto">
            Click <strong>"Refresh Results"</strong> above to search for jobs that match your profile.
            <span className="block mt-2 text-sm">
              {forceRefresh ? (
                <span className="text-indigo-600">
                  ✓ Force Refresh enabled - will perform fresh vector search (~10-15s)
                </span>
              ) : (
                <span className="text-gray-500">
                  Tip: Check "Force Refresh" to perform a fresh search and update cached results
                </span>
              )}
            </span>
          </p>
          <div className="flex items-center justify-center gap-6 text-sm text-gray-500">
            <div className="flex items-center gap-2">
              <svg className="h-5 w-5 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
              <span>Vector similarity matching</span>
            </div>
            <div className="flex items-center gap-2">
              <svg className="h-5 w-5 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
              <span>Skill gap analysis</span>
            </div>
            <div className="flex items-center gap-2">
              <svg className="h-5 w-5 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
              <span>Ranked by match score</span>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <>
      {/* Graph Search Section - Collapseable */}
      <div className="bg-white border border-gray-200 rounded-lg overflow-hidden mb-6">
        {/* Header */}
        <button
          onClick={() => setIsGraphSearchCollapsed(!isGraphSearchCollapsed)}
          className="w-full px-6 py-4 bg-gradient-to-r from-purple-50 to-indigo-50 border-b border-indigo-200 hover:from-purple-100 hover:to-indigo-100 transition-colors"
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <svg className="w-6 h-6 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
              <div className="text-left">
                <h3 className="text-lg font-semibold text-gray-900">Graph Search</h3>
                <p className="text-sm text-gray-600">Discover jobs by skills and explore skill co-occurrences</p>
              </div>
            </div>
            <svg
              className={`w-5 h-5 text-gray-500 transition-transform ${isGraphSearchCollapsed ? 'rotate-180' : ''}`}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
            </svg>
          </div>
        </button>

        {/* Collapseable Content */}
        {!isGraphSearchCollapsed && (
          <div className="p-6 space-y-6">
            {/* Skill Filter Cloud */}
            <div>
              <h4 className="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-2">
                <svg className="w-4 h-4 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z" />
                </svg>
                Discover Jobs by Skills
              </h4>
              <SkillFilterCloud onSkillClick={handleSkillClick} />
            </div>

            {/* Divider */}
            <div className="border-t border-gray-200"></div>

            {/* Skill Co-occurrence Heatmap */}
            <div>
              <h4 className="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-2">
                <svg className="w-4 h-4 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                </svg>
                Skill Co-occurrence Matrix
              </h4>
              <SkillHeatmap
                onCellClick={(skill1, skill2, count) => {
                  // Open drilldown modal with both skills selected
                  setSelectedSkillsForDrilldown([skill1, skill2]);
                  setSelectedSkillForDrilldown(undefined); // Clear single skill
                  setIsDrilldownModalOpen(true);
                }}
              />
            </div>
          </div>
        )}
      </div>

      {/* Skill Drilldown Modal */}
      <SkillDrilldownModal
        isOpen={isDrilldownModalOpen}
        initialSkill={selectedSkillForDrilldown}
        initialSkills={selectedSkillsForDrilldown}
        onClose={() => {
          setIsDrilldownModalOpen(false);
          setSelectedSkillForDrilldown(undefined);
          setSelectedSkillsForDrilldown(undefined);
        }}
        onViewJobs={handleViewJobsFromDrilldown}
      />

      {/* Show graph-discovered jobs if available */}
      {/* Graph and vector search results use same table */}
      {results && (
      <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
        {/* Header */}
        <div className={`px-6 py-4 border-b border-gray-200 ${selectedSkillsForDisplay.length > 0 ? 'bg-gradient-to-r from-purple-50 to-indigo-50 border-indigo-200' : 'bg-gray-50'}`}>
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-lg font-semibold text-gray-900 flex items-center gap-2">
              {selectedSkillsForDisplay.length > 0 && (
                <svg className="w-5 h-5 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                </svg>
              )}
              {selectedSkillsForDisplay.length > 0 ? 'Neo4j Graph Discovery Results' : 'Job Matching Results'}
            </h3>
            {selectedSkillsForDisplay.length > 0 && (
              <div className="flex items-center gap-2 mt-2 mb-1">
                <span className="text-xs text-gray-600 font-medium">Selected skills:</span>
                <div className="flex flex-wrap gap-1.5">
                  {selectedSkillsForDisplay.map((skill, index) => (
                    <span key={skill} className="flex items-center gap-1">
                      {index > 0 && <span className="text-indigo-400 text-xs">+</span>}
                      <span className="px-2.5 py-1 bg-indigo-600 text-white rounded-full text-xs font-semibold shadow-sm">
                        {skill}
                      </span>
                    </span>
                  ))}
                </div>
              </div>
            )}
            <p className="text-sm text-gray-600 mt-1">
              Found {results.totalMatches} matching jobs
            </p>
          </div>
          <div className="flex items-center gap-3">
            {selectedSkillsForDisplay.length > 0 ? (
              /* Graph results - show back button */
              <button
                onClick={handleBackToVectorSearch}
                className="px-4 py-2 bg-white border border-indigo-300 rounded-lg text-sm font-medium text-indigo-700 hover:bg-indigo-50 transition-colors"
              >
                ← Back to Vector Search
              </button>
            ) : (
              /* Vector results - show filters */
              <>
                <label className="flex items-center gap-2 text-sm text-gray-700">
                  <span className="text-gray-600">Top</span>
                  <select
                    value={topK}
                    onChange={(e) => setTopK(parseInt(e.target.value))}
                    className="px-3 py-1 text-sm border border-gray-300 rounded focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                  >
                    <option value="50">50</option>
                    <option value="100">100</option>
                    <option value="200">200</option>
                    <option value="300">300</option>
                    <option value="500">500</option>
                    <option value="9999">ALL</option>
                  </select>
                  <span className="text-gray-600">jobs</span>
                </label>
                <label className="flex items-center gap-2 text-sm text-gray-700">
                  <span className="text-gray-600">from last</span>
                  <select
                    value={maxDaysOld}
                    onChange={(e) => setMaxDaysOld(parseInt(e.target.value))}
                    className="px-3 py-1 text-sm border border-gray-300 rounded focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                  >
                    <option value="7">7 days</option>
                    <option value="14">2 weeks</option>
                    <option value="30">1 month</option>
                    <option value="60">2 months</option>
                    <option value="90">3 months</option>
                    <option value="0">All time</option>
                  </select>
                </label>
                <label className="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={forceRefresh}
                    onChange={(e) => setForceRefresh(e.target.checked)}
                    className="w-4 h-4 text-indigo-600 border-gray-300 rounded focus:ring-indigo-500"
                  />
                  <span>Force Refresh</span>
                </label>
                <button
                  onClick={() => handleSearch(undefined, 0, true)}
                  disabled={isManualRefresh}
                  className="px-4 py-2 bg-indigo-600 text-white text-sm rounded-lg font-medium hover:bg-indigo-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {isManualRefresh ? 'Refreshing...' : 'Refresh Results'}
                </button>
              </>
            )}
          </div>
        </div>
      </div>

      {/* Job Matches Table */}
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider w-80">
                Job Details
              </th>
              <th
                className="px-2 py-2 text-center text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100 w-16"
                onClick={() => handleSort('matchScore')}
              >
                <div className="flex items-center justify-center gap-1">
                  Match
                  <SortIcon field="matchScore" />
                </div>
              </th>
              <th
                className="px-2 py-2 text-center text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100 w-16"
                onClick={() => handleSort('skillMatch')}
              >
                <div className="flex items-center justify-center gap-1">
                  Skills
                  <SortIcon field="skillMatch" />
                </div>
              </th>
              <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider w-40">
                Matched Skills
              </th>
              <th
                className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100 w-24"
                onClick={() => handleSort('postedDate')}
              >
                <div className="flex items-center gap-1">
                  Posted
                  <SortIcon field="postedDate" />
                </div>
              </th>
              <th
                className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100 w-24"
                onClick={() => handleSort('fetchedDate')}
              >
                <div className="flex items-center gap-1">
                  Fetched
                  <SortIcon field="fetchedDate" />
                </div>
              </th>
              <th className="px-3 py-2 text-center text-xs font-medium text-gray-500 uppercase tracking-wider w-32">
                Actions
              </th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {getSortedMatches().map((match: JobMatchResult) => renderTableRow(match))}
          </tbody>
        </table>
      </div>

      {/* Empty State */}
      {results.matches.length === 0 && (
        <div className="px-6 py-12 text-center">
          <svg className="mx-auto h-12 w-12 text-gray-400 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <p className="text-gray-500">No matching jobs found. Try updating your profile or skills.</p>
        </div>
      )}
    </div>
      )}
    </>
  );
}
