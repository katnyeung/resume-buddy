'use client';

import { ATSReport } from '@/lib/types';

interface ATSQualityReportProps {
  report: ATSReport;
}

export default function ATSQualityReport({ report }: ATSQualityReportProps) {
  // Color-coded score indicator
  const getScoreColor = (score: number) => {
    if (score >= 80) return 'text-green-600 bg-green-50 border-green-200';
    if (score >= 60) return 'text-yellow-600 bg-yellow-50 border-yellow-200';
    return 'text-red-600 bg-red-50 border-red-200';
  };

  return (
    <div className="bg-white border-2 border-blue-300 rounded-lg p-4 mb-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-lg font-bold text-gray-800 flex items-center gap-2">
          <svg className="h-5 w-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          ATS Quality Report
        </h3>
        <div className={`px-4 py-2 rounded-full border-2 font-bold text-xl ${getScoreColor(report.score)}`}>
          {report.score}/100
        </div>
      </div>

      <p className="text-sm text-gray-700 mb-4">{report.summary}</p>

      {/* Strengths */}
      {report.strengths?.length > 0 && (
        <div className="mb-3">
          <h4 className="text-sm font-semibold text-green-700 mb-2 flex items-center gap-1">
            <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
            Strengths:
          </h4>
          <ul className="text-sm text-gray-600 space-y-1">
            {report.strengths.map((strength, idx) => (
              <li key={idx} className="pl-2 py-1 bg-green-50 rounded border-l-2 border-green-300">
                • {strength}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Improvements */}
      {report.improvements?.length > 0 && (
        <div className="mb-3">
          <h4 className="text-sm font-semibold text-blue-700 mb-2 flex items-center gap-1">
            <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
            </svg>
            Suggestions for Improvement:
          </h4>
          <ul className="text-sm text-gray-600 space-y-1">
            {report.improvements.map((improvement, idx) => (
              <li key={idx} className="pl-2 py-1 bg-blue-50 rounded border-l-2 border-blue-300">
                • {improvement}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Quality Metrics */}
      <div className="grid grid-cols-3 gap-3 text-sm pt-3 border-t border-gray-200">
        <div className="text-center p-2 bg-gray-50 rounded">
          <div className="text-xs text-gray-500 mb-1">Content Balance</div>
          <div className="font-semibold text-gray-800 capitalize">{report.contentBalance}</div>
        </div>
        <div className="text-center p-2 bg-gray-50 rounded">
          <div className="text-xs text-gray-500 mb-1">Readability</div>
          <div className="font-semibold text-gray-800 capitalize">{report.readability}</div>
        </div>
        <div className="text-center p-2 bg-gray-50 rounded">
          <div className="text-xs text-gray-500 mb-1">Organization</div>
          <div className="font-semibold text-gray-800 capitalize">{report.sectionOrganization}</div>
        </div>
      </div>
    </div>
  );
}
