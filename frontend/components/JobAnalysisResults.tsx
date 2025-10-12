'use client';

import { JobAnalysisResult } from '@/lib/types';

interface JobAnalysisResultsProps {
  analysis: JobAnalysisResult;
  onClose: () => void;
}

export default function JobAnalysisResults({ analysis, onClose }: JobAnalysisResultsProps) {
  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 z-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-xl shadow-2xl max-w-4xl w-full max-h-[90vh] overflow-hidden">
        {/* Header */}
        <div className="bg-gradient-to-r from-indigo-600 to-purple-600 text-white p-6">
          <div className="flex justify-between items-start">
            <div>
              <h2 className="text-2xl font-bold mb-2">Job Analysis Results</h2>
              <p className="text-indigo-100">
                {analysis.normalizedTitle} • {analysis.seniorityLevel} Level
              </p>
              <p className="text-indigo-200 text-sm mt-1">
                SOC Code: {analysis.primarySocCode}
              </p>
            </div>
            <button
              onClick={onClose}
              className="text-white hover:bg-white/20 p-2 rounded-lg transition-colors"
            >
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          {/* Score Cards */}
          <div className="grid grid-cols-4 gap-4 mt-6">
            <div className="bg-white/10 rounded-lg p-3">
              <div className="text-3xl font-bold">{analysis.overallScore.toFixed(1)}/10</div>
              <div className="text-sm text-indigo-100">Overall Score</div>
            </div>
            <div className="bg-white/10 rounded-lg p-3">
              <div className="text-3xl font-bold">{analysis.impactScore.toFixed(1)}/10</div>
              <div className="text-sm text-indigo-100">Impact</div>
            </div>
            <div className="bg-white/10 rounded-lg p-3">
              <div className="text-3xl font-bold">{analysis.technicalDepthScore.toFixed(1)}/10</div>
              <div className="text-sm text-indigo-100">Technical</div>
            </div>
            <div className="bg-white/10 rounded-lg p-3">
              <div className="text-3xl font-bold">{analysis.leadershipScore.toFixed(1)}/10</div>
              <div className="text-sm text-indigo-100">Leadership</div>
            </div>
          </div>
        </div>

        {/* Content */}
        <div className="p-6 overflow-y-auto max-h-[60vh]">
          {/* Recruiter Summary */}
          <div className="mb-6">
            <h3 className="text-lg font-semibold text-gray-800 mb-3 flex items-center gap-2">
              <svg className="w-5 h-5 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              Recruiter Evaluation
            </h3>
            <div className="bg-gray-50 rounded-lg p-4">
              <p className="text-gray-700 leading-relaxed">{analysis.recruiterSummary}</p>
            </div>
          </div>

          {/* Key Strengths */}
          <div className="mb-6">
            <h3 className="text-lg font-semibold text-gray-800 mb-3 flex items-center gap-2">
              <svg className="w-5 h-5 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              Key Strengths
            </h3>
            <div className="space-y-2">
              {analysis.keyStrengths.map((strength, index) => (
                <div key={index} className="bg-green-50 border-l-4 border-green-500 p-3 rounded">
                  <p className="text-gray-700 text-sm">{strength}</p>
                </div>
              ))}
            </div>
          </div>

          {/* Improvement Areas */}
          <div className="mb-6">
            <h3 className="text-lg font-semibold text-gray-800 mb-3 flex items-center gap-2">
              <svg className="w-5 h-5 text-orange-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
              Areas for Improvement
            </h3>
            <div className="space-y-2">
              {analysis.improvementAreas.map((area, index) => (
                <div key={index} className="bg-orange-50 border-l-4 border-orange-500 p-3 rounded">
                  <p className="text-gray-700 text-sm">{area}</p>
                </div>
              ))}
            </div>
          </div>

          {/* Work Activities (if available) */}
          {analysis.workActivities && analysis.workActivities.length > 0 && (
            <div>
              <h3 className="text-lg font-semibold text-gray-800 mb-3 flex items-center gap-2">
                <svg className="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 13.255A23.931 23.931 0 0112 15c-3.183 0-6.22-.62-9-1.745M16 6V4a2 2 0 00-2-2h-4a2 2 0 00-2 2v2m4 6h.01M5 20h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                </svg>
                Work Activities from O*NET
              </h3>
              <div className="space-y-2">
                {analysis.workActivities.map((activity, index) => (
                  <div key={index} className="bg-blue-50 p-3 rounded">
                    <div className="flex justify-between items-start">
                      <div>
                        <p className="font-medium text-gray-800">{activity.name}</p>
                        <p className="text-xs text-gray-600 mt-1">{activity.category}</p>
                      </div>
                      <span className="text-sm font-semibold text-blue-600">
                        {activity.importance.toFixed(0)}%
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="bg-gray-50 px-6 py-4 flex justify-end gap-3">
          <button
            onClick={onClose}
            className="px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
