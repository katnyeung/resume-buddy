'use client';

import React, { useEffect, useState } from 'react';
import { getUserCredits } from '@/lib/api';

interface CreditBalanceProps {
  userId?: string;
  compact?: boolean;
}

export default function CreditBalance({ userId = 'default_user', compact = false }: CreditBalanceProps) {
  const [credits, setCredits] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchCredits = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await getUserCredits(userId);
      setCredits(data);
    } catch (err: any) {
      console.error('Failed to fetch credits:', err);
      // Don't show error for first load - just log it
      if (credits) {
        setError(err.message || 'Failed to load credits');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCredits();

    // Refresh every 30 seconds
    const interval = setInterval(fetchCredits, 30000);
    return () => clearInterval(interval);
  }, [userId]);

  if (loading && !credits) {
    return (
      <div className={`animate-pulse ${compact ? 'text-sm' : 'text-base'}`}>
        <div className="bg-gray-200 dark:bg-gray-700 rounded h-6 w-24"></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className={`text-red-500 ${compact ? 'text-sm' : 'text-base'}`}>
        Error loading credits
      </div>
    );
  }

  if (!credits) {
    return null;
  }

  const available = parseFloat(credits.availableCredits || 0);
  const isLow = available < 50;
  const isCritical = available < 20;

  if (compact) {
    return (
      <div
        className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-full text-sm font-medium cursor-pointer ${
          isCritical
            ? 'bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-200'
            : isLow
            ? 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900 dark:text-yellow-200'
            : 'bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-200'
        }`}
        title={`Total: ${parseFloat(credits.totalCredits || 0).toFixed(0)} | Used: ${parseFloat(credits.usedCredits || 0).toFixed(0)} | Available: ${available.toFixed(0)}`}
      >
        <svg
          className="w-4 h-4"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
          />
        </svg>
        <div className="flex flex-col">
          <span className="font-bold">{available.toFixed(0)} credits</span>
          <span className="text-xs opacity-75">Used: {parseFloat(credits.usedCredits || 0).toFixed(0)}</span>
        </div>
        {isLow && (
          <span className="ml-1" title="Low balance">⚠️</span>
        )}
      </div>
    );
  }

  return (
    <div className={`p-4 rounded-lg border ${
      isCritical
        ? 'bg-red-50 border-red-200 dark:bg-red-900/20 dark:border-red-800'
        : isLow
        ? 'bg-yellow-50 border-yellow-200 dark:bg-yellow-900/20 dark:border-yellow-800'
        : 'bg-green-50 border-green-200 dark:bg-green-900/20 dark:border-green-800'
    }`}>
      <div className="flex items-center justify-between mb-2">
        <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300">
          Token Credits
        </h3>
        <button
          onClick={fetchCredits}
          className="text-xs text-blue-600 hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300"
          title="Refresh"
        >
          ↻ Refresh
        </button>
      </div>

      <div className="space-y-1">
        <div className="flex items-baseline gap-2">
          <span className={`text-2xl font-bold ${
            isCritical
              ? 'text-red-700 dark:text-red-300'
              : isLow
              ? 'text-yellow-700 dark:text-yellow-300'
              : 'text-green-700 dark:text-green-300'
          }`}>
            {available.toFixed(0)}
          </span>
          <span className="text-sm text-gray-600 dark:text-gray-400">
            available
          </span>
        </div>

        <div className="text-xs text-gray-500 dark:text-gray-400 space-y-0.5">
          <div>Total: {parseFloat(credits.totalCredits || 0).toFixed(0)}</div>
          <div>Used: {parseFloat(credits.usedCredits || 0).toFixed(0)}</div>
        </div>

        {/* Progress bar */}
        <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2 mt-2">
          <div
            className={`h-2 rounded-full transition-all ${
              isCritical
                ? 'bg-red-500'
                : isLow
                ? 'bg-yellow-500'
                : 'bg-green-500'
            }`}
            style={{
              width: `${Math.min(100, (available / parseFloat(credits.totalCredits || 1)) * 100)}%`
            }}
          ></div>
        </div>

        {/* Warning messages */}
        {isCritical && (
          <div className="mt-2 p-2 bg-red-100 dark:bg-red-900/30 rounded text-xs text-red-700 dark:text-red-300">
            ⚠️ Critical: Low credit balance! Contact admin to purchase more.
          </div>
        )}
        {isLow && !isCritical && (
          <div className="mt-2 p-2 bg-yellow-100 dark:bg-yellow-900/30 rounded text-xs text-yellow-700 dark:text-yellow-300">
            ⚠️ Warning: Credit balance is running low.
          </div>
        )}

        {/* Cost reference */}
        <div className="mt-3 pt-3 border-t border-gray-300 dark:border-gray-600">
          <div className="text-xs text-gray-500 dark:text-gray-400 space-y-1">
            <div className="font-medium mb-1">Cost per analysis:</div>
            <div>• Resume analysis: 100 credits</div>
            <div>• Job experience: 50 credits</div>
            <div>• Job profile: 25 credits</div>
          </div>
        </div>
      </div>
    </div>
  );
}
