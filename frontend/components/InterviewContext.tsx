"use client";

import React, { useState } from 'react';

interface JobListing {
  title: string;
  company?: string;
  location?: string;
  description: string;
}

interface ResumeExperience {
  jobTitle: string;
  seniorityLevel: string;
  descriptionLines: Array<{
    text: string;
  }>;
  potentialQuestions?: string[];
  starsAnalysis?: string[];
}

interface InterviewContextProps {
  jobListing: JobListing | null;
  resumeExperience: ResumeExperience | null;
  loading?: boolean;
}

export default function InterviewContext({
  jobListing,
  resumeExperience,
  loading
}: InterviewContextProps) {
  const [jobExpanded, setJobExpanded] = useState(true);
  const [resumeExpanded, setResumeExpanded] = useState(true);

  if (loading) {
    return (
      <div className="w-full lg:w-[30%] bg-white border-r border-gray-200 p-6">
        <div className="animate-pulse">
          <div className="h-6 bg-gray-200 rounded mb-4"></div>
          <div className="h-4 bg-gray-200 rounded mb-2"></div>
          <div className="h-4 bg-gray-200 rounded mb-2"></div>
          <div className="h-4 bg-gray-200 rounded"></div>
        </div>
      </div>
    );
  }

  return (
    <div className="w-full lg:w-[30%] bg-white border-r border-gray-200 overflow-y-auto">
      <div className="sticky top-0 bg-white border-b border-gray-200 p-4 z-10">
        <h2 className="text-lg font-bold text-gray-800">Interview Context</h2>
        <p className="text-sm text-gray-500">Reference material for your answers</p>
      </div>

      <div className="p-4 space-y-4">
        {/* Target Job Section */}
        <div className="bg-blue-50 rounded-lg overflow-hidden border border-blue-200">
          <button
            onClick={() => setJobExpanded(!jobExpanded)}
            className="w-full flex items-center justify-between p-4 hover:bg-blue-100 transition-colors"
          >
            <div className="flex items-center gap-2">
              <span className="text-xl">📄</span>
              <h3 className="font-semibold text-blue-900">Target Job</h3>
            </div>
            <svg
              className={`w-5 h-5 text-blue-600 transition-transform ${jobExpanded ? 'rotate-180' : ''}`}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
            </svg>
          </button>

          {jobExpanded && jobListing && (
            <div className="px-4 pb-4 space-y-2">
              <div>
                <p className="font-semibold text-gray-900">{jobListing.title}</p>
                {jobListing.company && (
                  <p className="text-sm text-gray-600">{jobListing.company}</p>
                )}
                {jobListing.location && (
                  <p className="text-sm text-gray-500">{jobListing.location}</p>
                )}
              </div>

              <div className="mt-3">
                <p className="text-xs font-semibold text-gray-700 mb-1">Description:</p>
                <p className="text-sm text-gray-600 leading-relaxed max-h-64 overflow-y-auto">
                  {jobListing.description.length > 800
                    ? `${jobListing.description.slice(0, 800)}...`
                    : jobListing.description}
                </p>
              </div>
            </div>
          )}

          {jobExpanded && !jobListing && (
            <div className="px-4 pb-4">
              <p className="text-sm text-gray-500 italic">No job listing available</p>
            </div>
          )}
        </div>

        {/* Your Experience Section */}
        <div className="bg-green-50 rounded-lg overflow-hidden border border-green-200">
          <button
            onClick={() => setResumeExpanded(!resumeExpanded)}
            className="w-full flex items-center justify-between p-4 hover:bg-green-100 transition-colors"
          >
            <div className="flex items-center gap-2">
              <span className="text-xl">👤</span>
              <h3 className="font-semibold text-green-900">Your Experience</h3>
            </div>
            <svg
              className={`w-5 h-5 text-green-600 transition-transform ${resumeExpanded ? 'rotate-180' : ''}`}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
            </svg>
          </button>

          {resumeExpanded && resumeExperience && (
            <div className="px-4 pb-4 space-y-3">
              <div>
                <p className="font-semibold text-gray-900">{resumeExperience.jobTitle}</p>
                <p className="text-sm text-gray-600">
                  {resumeExperience.seniorityLevel} level
                </p>
              </div>

              <div>
                <p className="text-xs font-semibold text-gray-700 mb-2">What you actually did:</p>
                <ul className="space-y-1.5">
                  {resumeExperience.descriptionLines.slice(0, 8).map((line, idx) => (
                    <li key={idx} className="text-sm text-gray-600 leading-relaxed flex gap-2">
                      <span className="text-green-600 flex-shrink-0">•</span>
                      <span>{line.text}</span>
                    </li>
                  ))}
                </ul>
              </div>

              {resumeExperience.potentialQuestions && resumeExperience.potentialQuestions.length > 0 && (
                <div className="mt-3 pt-3 border-t border-green-200">
                  <p className="text-xs font-semibold text-gray-700 mb-2">💡 Coach Suggestions:</p>
                  <ul className="space-y-1.5">
                    {resumeExperience.potentialQuestions.slice(0, 3).map((question, idx) => (
                      <li key={idx} className="text-xs text-gray-600 leading-relaxed flex gap-2">
                        <span className="text-yellow-600 flex-shrink-0">→</span>
                        <span>{question}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {resumeExperience.starsAnalysis && resumeExperience.starsAnalysis.length > 0 && (
                <div className="mt-3 pt-3 border-t border-green-200">
                  <p className="text-xs font-semibold text-gray-700 mb-2">🎯 Areas to Strengthen:</p>
                  <ul className="space-y-1.5">
                    {resumeExperience.starsAnalysis.slice(0, 3).map((analysis, idx) => (
                      <li key={idx} className="text-xs text-gray-600 leading-relaxed flex gap-2">
                        <span className="text-red-600 flex-shrink-0">!</span>
                        <span>{analysis}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}

          {resumeExpanded && !resumeExperience && (
            <div className="px-4 pb-4">
              <p className="text-sm text-gray-500 italic">No resume experience available</p>
            </div>
          )}
        </div>

        {/* Help Card */}
        <div className="bg-gray-50 rounded-lg p-4 border border-gray-200">
          <div className="flex gap-2 items-start">
            <span className="text-lg flex-shrink-0">💡</span>
            <div>
              <p className="text-xs font-semibold text-gray-700 mb-1">Interview Tip</p>
              <p className="text-xs text-gray-600 leading-relaxed">
                Use these details to craft specific, impactful answers.
                Reference exact numbers, technologies, and outcomes from your experience.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
