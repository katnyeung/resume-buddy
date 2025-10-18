'use client';

import React, { useEffect, useState } from 'react';
import { getJobStatus } from '@/lib/api';

interface JobStatusIndicatorProps {
  jobId: string;
  onComplete?: (result: any) => void;
  onError?: (error: string) => void;
  pollIntervalMs?: number;
}

export default function JobStatusIndicator({
  jobId,
  onComplete,
  onError,
  pollIntervalMs = 3000
}: JobStatusIndicatorProps) {
  const [status, setStatus] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let interval: NodeJS.Timeout;
    let isMounted = true;

    const pollStatus = async () => {
      try {
        const jobStatus = await getJobStatus(jobId);

        if (!isMounted) return;

        setStatus(jobStatus);
        setLoading(false);

        if (jobStatus.status === 'COMPLETED') {
          if (onComplete) {
            onComplete(jobStatus.result);
          }
          if (interval) clearInterval(interval);
        } else if (jobStatus.status === 'FAILED') {
          if (onError) {
            onError(jobStatus.errorMessage || 'Job failed');
          }
          if (interval) clearInterval(interval);
        }
      } catch (err: any) {
        console.error('Error polling job status:', err);
        if (isMounted) {
          setLoading(false);
          if (onError) {
            onError(err.message || 'Failed to fetch job status');
          }
        }
      }
    };

    // Initial poll
    pollStatus();

    // Set up polling interval
    interval = setInterval(pollStatus, pollIntervalMs);

    return () => {
      isMounted = false;
      if (interval) clearInterval(interval);
    };
  }, [jobId, pollIntervalMs]);

  if (loading || !status) {
    return (
      <div className="flex items-center gap-3 p-4 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg">
        <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-blue-600"></div>
        <div>
          <div className="text-sm font-medium text-blue-900 dark:text-blue-100">
            Loading job status...
          </div>
          <div className="text-xs text-blue-700 dark:text-blue-300">
            Job ID: {jobId}
          </div>
        </div>
      </div>
    );
  }

  const statusColor = {
    QUEUED: 'yellow',
    PROCESSING: 'blue',
    COMPLETED: 'green',
    FAILED: 'red',
    CANCELLED: 'gray'
  }[status.status] || 'gray';

  const statusIcon = {
    QUEUED: '⏳',
    PROCESSING: '⚙️',
    COMPLETED: '✅',
    FAILED: '❌',
    CANCELLED: '🚫'
  }[status.status] || '❓';

  const statusText = {
    QUEUED: 'Queued',
    PROCESSING: 'Processing',
    COMPLETED: 'Completed',
    FAILED: 'Failed',
    CANCELLED: 'Cancelled'
  }[status.status] || status.status;

  return (
    <div className={`p-4 rounded-lg border bg-${statusColor}-50 border-${statusColor}-200 dark:bg-${statusColor}-900/20 dark:border-${statusColor}-800`}>
      <div className="flex items-start gap-3">
        {/* Status icon */}
        <div className="text-2xl">{statusIcon}</div>

        <div className="flex-1">
          {/* Status header */}
          <div className="flex items-center justify-between mb-2">
            <h3 className={`text-sm font-semibold text-${statusColor}-900 dark:text-${statusColor}-100`}>
              {statusText}
            </h3>
            {status.status === 'QUEUED' && status.queuePosition > 0 && (
              <span className={`text-xs px-2 py-1 rounded-full bg-${statusColor}-100 text-${statusColor}-700 dark:bg-${statusColor}-800 dark:text-${statusColor}-200`}>
                Position: {status.queuePosition}
              </span>
            )}
          </div>

          {/* Job details */}
          <div className="space-y-1 text-xs text-gray-700 dark:text-gray-300">
            <div className="flex items-center gap-2">
              <span className="font-medium">Type:</span>
              <span>{status.jobType?.replace(/_/g, ' ')}</span>
            </div>

            {status.estimatedCredits && (
              <div className="flex items-center gap-2">
                <span className="font-medium">Credits:</span>
                <span>{status.actualCreditsUsed || status.estimatedCredits}</span>
              </div>
            )}

            {status.queuedAt && (
              <div className="flex items-center gap-2">
                <span className="font-medium">Queued:</span>
                <span>{new Date(status.queuedAt).toLocaleTimeString()}</span>
              </div>
            )}

            {status.startedAt && (
              <div className="flex items-center gap-2">
                <span className="font-medium">Started:</span>
                <span>{new Date(status.startedAt).toLocaleTimeString()}</span>
              </div>
            )}

            {status.completedAt && (
              <div className="flex items-center gap-2">
                <span className="font-medium">Completed:</span>
                <span>{new Date(status.completedAt).toLocaleTimeString()}</span>
              </div>
            )}

            {status.retryCount > 0 && (
              <div className="flex items-center gap-2">
                <span className="font-medium">Retries:</span>
                <span>{status.retryCount} / {status.maxRetries}</span>
              </div>
            )}
          </div>

          {/* Error message */}
          {status.status === 'FAILED' && status.errorMessage && (
            <div className="mt-3 p-2 bg-red-100 dark:bg-red-900/30 rounded text-xs text-red-700 dark:text-red-300">
              <div className="font-medium mb-1">Error:</div>
              <div>{status.errorMessage}</div>
              {status.estimatedCredits && (
                <div className="mt-2 text-green-600 dark:text-green-400">
                  ✓ {status.estimatedCredits} credits refunded
                </div>
              )}
            </div>
          )}

          {/* Processing animation */}
          {status.status === 'PROCESSING' && (
            <div className="mt-3">
              <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2">
                <div className="h-2 bg-blue-500 rounded-full animate-pulse" style={{ width: '70%' }}></div>
              </div>
              <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                Analyzing with AI... This may take 1-2 minutes.
              </div>
            </div>
          )}

          {/* Queue position indicator */}
          {status.status === 'QUEUED' && status.queuePosition > 0 && (
            <div className="mt-3 text-xs text-yellow-700 dark:text-yellow-300">
              {status.queuePosition === 1
                ? 'Next in queue'
                : `${status.queuePosition - 1} job${status.queuePosition > 2 ? 's' : ''} ahead`}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
