'use client';

import { useState, useEffect } from 'react';
import { getMatchingResults, toggleMatchSaved, markMatchApplied, toggleMatchRedflag } from '@/lib/api';
import { JobMatchingResultsResponse, JobMatchResult } from '@/lib/types';

interface JobMatchingResultsProps {
  profileId: string;
}

type SortField = 'matchScore' | 'skillMatch' | 'postedDate';
type SortDirection = 'asc' | 'desc';

export default function JobMatchingResults({ profileId }: JobMatchingResultsProps) {
  const [results, setResults] = useState<JobMatchingResultsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [sortField, setSortField] = useState<SortField>('matchScore');
  const [sortDirection, setSortDirection] = useState<SortDirection>('desc');
  const [forceRefresh, setForceRefresh] = useState(false);

  // Auto-fetch results on mount
  useEffect(() => {
    handleSearch();
  }, [profileId]);

  const handleSearch = async (refresh?: boolean) => {
    setLoading(true);
    setError(null);
    try {
      const shouldRefresh = refresh !== undefined ? refresh : forceRefresh;
      const data = await getMatchingResults(profileId, 50, shouldRefresh);
      setResults(data);
    } catch (err) {
      console.error('Failed to fetch matching results:', err);
      setError('Failed to fetch matching jobs. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleToggleSaved = async (matchId: string, currentlySaved: boolean) => {
    try {
      await toggleMatchSaved(matchId, !currentlySaved);
      // Update local state
      if (results) {
        const updatedMatches = results.matches.map(match =>
          match.matchId === matchId ? { ...match, isSaved: !currentlySaved } : match
        );
        setResults({ ...results, matches: updatedMatches });
      }
    } catch (err) {
      console.error('Failed to toggle saved status:', err);
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
            : match.isSaved
            ? 'bg-yellow-50 hover:bg-yellow-100'
            : 'hover:bg-gray-50'
        }`}
      >
        {isCollapsed ? (
          /* Collapsed view - thin single column */
          <>
            <td colSpan={7} className="px-6 py-2">
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
            <td className="px-6 py-4">
              <div className="max-w-xs">
                <div className="text-sm font-medium text-gray-900 truncate">
                  {match.title}
                </div>
                <div className="text-sm text-gray-500 truncate">
                  {match.company}
                </div>
                <div className="text-xs text-gray-400 truncate mt-1">
                  {match.location}
                </div>
                {match.salaryRange && (
                  <div className="text-xs text-green-600 font-medium mt-1">
                    {match.salaryRange}
                  </div>
                )}
              </div>
            </td>

            {/* Match Score */}
            <td className="px-6 py-4">
              <div className="text-sm font-medium text-gray-900">
                {(match.similarityScore * 100).toFixed(1)}%
              </div>
            </td>

            {/* Skills Match */}
            <td className="px-6 py-4">
              <div className="flex flex-col gap-1">
                <div className="text-sm font-medium text-gray-900">
                  {Math.round(match.skillMatchPercentage)}%
                </div>
                <div className="text-xs text-gray-500">
                  Weighted: {Math.round(match.weightedSkillScore)}%
                </div>
              </div>
            </td>

            {/* Matched Skills */}
            <td className="px-6 py-4">
              <div className="text-sm text-green-700 max-w-xs">
                {match.matchedSkills.slice(0, 5).join(', ')}
                {match.matchedSkills.length > 5 && (
                  <span className="text-gray-500">
                    {' '}+{match.matchedSkills.length - 5} more
                  </span>
                )}
              </div>
            </td>

            {/* Posted Date */}
            <td className="px-6 py-4 text-sm text-gray-500 whitespace-nowrap">
              {formatDate(match.postedDate)}
            </td>

            {/* Actions Column - Merged 3 buttons */}
            <td className="px-6 py-4" onClick={(e) => e.stopPropagation()}>
              <div className="flex items-center justify-center gap-2">
                {/* Saved Button */}
                <button
                  onClick={() => handleToggleSaved(match.matchId, match.isSaved)}
                  className={`p-1.5 rounded-lg transition-colors ${
                    match.isSaved
                      ? 'text-yellow-600 bg-yellow-50 hover:bg-yellow-100'
                      : 'text-gray-400 hover:text-yellow-600 hover:bg-yellow-50'
                  }`}
                  title="Save (kept 14 days)"
                >
                  <svg className="w-4 h-4" fill={match.isSaved ? 'currentColor' : 'none'} stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z" />
                  </svg>
                </button>

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

            {/* View Job Button */}
            <td className="px-6 py-4 text-right whitespace-nowrap" onClick={(e) => e.stopPropagation()}>
              <a
                href={match.url}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center px-3 py-1.5 border border-transparent text-xs font-medium rounded-md text-indigo-700 bg-indigo-100 hover:bg-indigo-200 transition-colors"
              >
                View Job
                <svg className="ml-1 h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                </svg>
              </a>
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

  if (loading && !results) {
    return (
      <div className="bg-white border border-gray-200 rounded-lg p-12 text-center">
        <div className="flex flex-col items-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600 mb-4"></div>
          <p className="text-gray-600">Loading matching jobs...</p>
        </div>
      </div>
    );
  }

  if (error && !results) {
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

  if (!results) {
    return null;
  }

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
              Found {results.totalMatches} matching jobs
            </p>
          </div>
          <div className="flex items-center gap-3">
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
              onClick={() => handleSearch()}
              disabled={loading}
              className="px-4 py-2 bg-indigo-600 text-white text-sm rounded-lg font-medium hover:bg-indigo-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? 'Refreshing...' : 'Refresh Results'}
            </button>
          </div>
        </div>
      </div>

      {/* Job Matches Table */}
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Job Details
              </th>
              <th
                className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100"
                onClick={() => handleSort('matchScore')}
              >
                <div className="flex items-center gap-1">
                  Match Score
                  <SortIcon field="matchScore" />
                </div>
              </th>
              <th
                className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100"
                onClick={() => handleSort('skillMatch')}
              >
                <div className="flex items-center gap-1">
                  Skills Match
                  <SortIcon field="skillMatch" />
                </div>
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Matched Skills
              </th>
              <th
                className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100"
                onClick={() => handleSort('postedDate')}
              >
                <div className="flex items-center gap-1">
                  Posted
                  <SortIcon field="postedDate" />
                </div>
              </th>
              <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider">
                Actions
              </th>
              <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                View
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
  );
}
