'use client';

import { useEffect, useState } from 'react';
import { getCrawlActivityHistory } from '@/lib/api';

interface CrawlActivity {
  id: string;
  source: string;
  keywords: string;
  location: string;
  totalFetched: number;
  totalSaved: number;
  duplicatesUpdated: number;
  duplicatesSkipped: number;
  failedToProcess: number;
  status: string;
  errorMessage?: string;
  processingTimeMs: number;
  crawledAt: string;
}

export default function CrawlActivityHistory() {
  const [activities, setActivities] = useState<CrawlActivity[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isExpanded, setIsExpanded] = useState(false);

  const fetchHistory = async () => {
    try {
      setLoading(true);
      const data = await getCrawlActivityHistory(5);
      setActivities(data);
      setError(null);
    } catch (err) {
      console.error('Failed to fetch crawl activity history:', err);
      setError('Failed to load crawl history');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchHistory();
  }, []);

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;

    return date.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  const getSourceBadgeColor = (source: string) => {
    switch (source.toUpperCase()) {
      case 'ADZUNA':
        return 'bg-blue-100 text-blue-800';
      case 'REED':
        return 'bg-purple-100 text-purple-800';
      case 'JSEARCH':
        return 'bg-green-100 text-green-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  // Calculate summary stats
  const getSummaryStats = () => {
    if (activities.length === 0) return { lastUpdate: 'Never', totalNew: 0, totalUpdated: 0, sources: [] };

    const latest = activities[0];
    const totalNew = activities.reduce((sum, a) => sum + (a.status === 'SUCCESS' ? a.totalSaved : 0), 0);
    const totalUpdated = activities.reduce((sum, a) => sum + (a.status === 'SUCCESS' ? a.duplicatesUpdated : 0), 0);
    const sources = [...new Set(activities.map(a => a.source))];

    return {
      lastUpdate: formatDate(latest.crawledAt),
      totalNew,
      totalUpdated,
      sources
    };
  };

  const summary = getSummaryStats();

  if (loading && activities.length === 0) {
    return (
      <div className="bg-white rounded-lg shadow p-4 mb-6">
        <div className="animate-pulse flex items-center justify-between">
          <div className="h-5 w-48 bg-gray-200 rounded"></div>
          <div className="h-4 w-24 bg-gray-200 rounded"></div>
        </div>
      </div>
    );
  }

  if (error || activities.length === 0) {
    return (
      <div className="bg-white rounded-lg shadow p-4 mb-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-gray-600">📊</span>
            <span className="text-sm text-gray-600">
              {error || 'No job board updates yet'}
            </span>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-lg shadow mb-6">
      {/* Collapsed Summary View */}
      <div className="p-4 flex items-center justify-between">
        <button
          onClick={() => setIsExpanded(!isExpanded)}
          className="flex items-center gap-3 flex-1 hover:opacity-80 transition-opacity"
        >
          <span className="text-xl">📊</span>
          <div className="text-left">
            <div className="text-sm font-medium text-gray-900">
              Job Board Last Updated: {summary.lastUpdate}
            </div>
            <div className="text-xs text-gray-500 mt-0.5">
              {summary.totalNew} new, {summary.totalUpdated} updated from {summary.sources.join(', ')}
            </div>
          </div>
        </button>
        <div className="flex items-center gap-2">
          <button
            onClick={fetchHistory}
            className="text-xs text-blue-600 hover:text-blue-800 px-2 py-1 rounded"
            disabled={loading}
          >
            {loading ? 'Refreshing...' : 'Refresh'}
          </button>
          <button
            onClick={() => setIsExpanded(!isExpanded)}
            className="p-1 hover:bg-gray-100 rounded transition-colors"
          >
            <svg
              className={`h-5 w-5 text-gray-400 transition-transform ${isExpanded ? 'rotate-180' : ''}`}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
            </svg>
          </button>
        </div>
      </div>

      {/* Expanded Detail Table */}
      {isExpanded && (
        <div className="border-t border-gray-200 p-4">
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead>
                <tr className="bg-gray-50">
                  <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase">Time</th>
                  <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase">Source</th>
                  <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase">Keywords</th>
                  <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase">Location</th>
                  <th className="px-3 py-2 text-right text-xs font-medium text-gray-500 uppercase">Fetched</th>
                  <th className="px-3 py-2 text-right text-xs font-medium text-gray-500 uppercase">New</th>
                  <th className="px-3 py-2 text-right text-xs font-medium text-gray-500 uppercase">Updated</th>
                  <th className="px-3 py-2 text-center text-xs font-medium text-gray-500 uppercase">Status</th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {activities.map((activity) => (
                  <tr key={activity.id} className="hover:bg-gray-50">
                    <td className="px-3 py-2 text-xs text-gray-600 whitespace-nowrap">
                      {formatDate(activity.crawledAt)}
                    </td>
                    <td className="px-3 py-2 text-xs">
                      <span className={`inline-flex px-2 py-0.5 rounded text-xs font-medium ${getSourceBadgeColor(activity.source)}`}>
                        {activity.source}
                      </span>
                    </td>
                    <td className="px-3 py-2 text-xs text-gray-900">
                      {activity.keywords || '-'}
                    </td>
                    <td className="px-3 py-2 text-xs text-gray-600">
                      {activity.location || '-'}
                    </td>
                    <td className="px-3 py-2 text-xs text-gray-900 text-right">
                      {activity.totalFetched}
                    </td>
                    <td className="px-3 py-2 text-xs text-green-600 text-right font-medium">
                      {activity.totalSaved}
                    </td>
                    <td className="px-3 py-2 text-xs text-yellow-600 text-right font-medium">
                      {activity.duplicatesUpdated}
                    </td>
                    <td className="px-3 py-2 text-xs text-center">
                      {activity.status === 'SUCCESS' ? (
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-green-100 text-green-800">
                          ✓
                        </span>
                      ) : (
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-red-100 text-red-800" title={activity.errorMessage}>
                          ✗
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-3 pt-3 border-t border-gray-200">
            <p className="text-xs text-gray-500 text-center">
              Historical record - use Refresh button to update
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
