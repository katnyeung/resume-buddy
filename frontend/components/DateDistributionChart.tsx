'use client';

interface DateDistributionChartProps {
  dateDistribution: Record<string, number>;
}

export default function DateDistributionChart({ dateDistribution }: DateDistributionChartProps) {
  // Convert dateDistribution to sorted array
  const entries = Object.entries(dateDistribution).sort((a, b) => a[0].localeCompare(b[0]));

  if (entries.length === 0) {
    return (
      <div className="text-sm text-gray-500 text-center py-4">
        No date information available
      </div>
    );
  }

  const maxCount = Math.max(...entries.map(([, count]) => count));

  return (
    <div className="space-y-2">
      <h4 className="text-sm font-medium text-gray-700">Job Postings Over Time</h4>
      <div className="space-y-1">
        {entries.map(([date, count]) => {
          const percentage = (count / maxCount) * 100;
          const formattedDate = new Date(date).toLocaleDateString('en-US', {
            month: 'short',
            year: 'numeric'
          });

          return (
            <div key={date} className="flex items-center gap-2">
              <span className="text-xs text-gray-600 w-20 text-right">{formattedDate}</span>
              <div className="flex-1 bg-gray-200 rounded-full h-4 overflow-hidden">
                <div
                  className="bg-indigo-600 h-full rounded-full transition-all duration-300"
                  style={{ width: `${percentage}%` }}
                ></div>
              </div>
              <span className="text-xs font-medium text-gray-700 w-8">{count}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
