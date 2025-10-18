'use client';

import { ResumeAnalysisDto } from '@/lib/types';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import {
  createJobSearchProfile,
  getJobSearchProfilesByResume,
  getStructuredAnalysis,
  analyzeJobAsync
} from '@/lib/api';
import { getCurrentUserId } from '@/lib/userUtils';
import JobStatusIndicator from './JobStatusIndicator';

interface AnalysisSummaryProps {
  analysis: ResumeAnalysisDto;
  resumeId: string;
  onAnalyzeJob?: (experienceId: string) => void;
  onFindJobs?: (experienceId: string) => void;
}

export default function AnalysisSummary({ analysis, resumeId, onAnalyzeJob, onFindJobs }: AnalysisSummaryProps) {
  const router = useRouter();
  const [analyzingJobId, setAnalyzingJobId] = useState<string | null>(null);
  const [generatingProfile, setGeneratingProfile] = useState(false);
  const [selectedExperiences, setSelectedExperiences] = useState<Set<string>>(new Set());
  const [currentProfile, setCurrentProfile] = useState<any>(null);
  const [jobAnalysisJobIds, setJobAnalysisJobIds] = useState<Record<string, string>>({}); // experienceId -> jobQueueId
  const [localAnalysis, setLocalAnalysis] = useState<ResumeAnalysisDto>(analysis);

  // Update local analysis when prop changes
  useEffect(() => {
    setLocalAnalysis(analysis);
  }, [analysis]);

  // Build analyzed experiences map from the isAnalyzed flag in each experience
  const analyzedExperiences: Record<string, boolean> = {};
  localAnalysis.experiences.forEach(exp => {
    analyzedExperiences[exp.id] = exp.isAnalyzed || false;
  });

  // Load existing profile on mount (lightweight check only)
  useEffect(() => {
    const loadProfile = async () => {
      try {
        const profiles = await getJobSearchProfilesByResume(resumeId);
        if (profiles && profiles.length > 0) {
          setCurrentProfile(profiles[0]);
        }
      } catch (error) {
        console.error('Failed to load job search profile:', error);
      }
    };
    loadProfile();
  }, [resumeId]);

  const handleViewAnalysis = (analysisId: string) => {
    router.push(`/analysis/${analysisId}`);
  };

  const handleAnalyzeJobClick = async (experienceId: string) => {
    try {
      // Queue the job analysis
      const response = await analyzeJobAsync(resumeId, experienceId, getCurrentUserId());

      // Store the job queue ID for this experience
      setJobAnalysisJobIds(prev => ({ ...prev, [experienceId]: response.jobId }));

      console.log('Job analysis queued:', response);
    } catch (error: any) {
      console.error('Failed to queue job analysis:', error);
      if (error.response?.status === 402) {
        alert('Insufficient credits! Need 50 credits for job analysis.');
      } else {
        alert('Failed to queue job analysis: ' + (error.message || 'Unknown error'));
      }
    }
  };

  const handleJobAnalysisComplete = async (experienceId: string) => {
    console.log('Job analysis complete for experience:', experienceId);

    // Wait a moment for backend to finish saving
    await new Promise(resolve => setTimeout(resolve, 1000));

    // Reload analysis to get updated isAnalyzed flag and analysisId
    try {
      const updatedAnalysis = await getStructuredAnalysis(resumeId);
      if (updatedAnalysis) {
        console.log('Reloaded analysis:', updatedAnalysis);
        setLocalAnalysis(updatedAnalysis);
      }
    } catch (error) {
      console.error('Failed to reload analysis:', error);
    }

    // Remove job queue ID after reload completes
    setJobAnalysisJobIds(prev => {
      const newIds = { ...prev };
      delete newIds[experienceId];
      return newIds;
    });
  };

  const handleJobAnalysisError = (experienceId: string) => {
    // Remove job queue ID on error
    setJobAnalysisJobIds(prev => {
      const newIds = { ...prev };
      delete newIds[experienceId];
      return newIds;
    });
  };

  const toggleExperienceSelection = (experienceId: string) => {
    const newSelection = new Set(selectedExperiences);
    if (newSelection.has(experienceId)) {
      newSelection.delete(experienceId);
    } else {
      newSelection.add(experienceId);
    }
    setSelectedExperiences(newSelection);
  };

  const handleGenerateJobPost = async () => {
    if (selectedExperiences.size === 0) {
      alert('Please select at least one experience to generate a job post');
      return;
    }

    setGeneratingProfile(true);
    try {
      const profile = await createJobSearchProfile(resumeId, Array.from(selectedExperiences));
      setCurrentProfile(profile);

      // Redirect to job search page
      router.push(`/job-search/${profile.id}`);
    } catch (error) {
      console.error('Failed to generate job post:', error);
      alert('Failed to generate job post. Please try again.');
    } finally {
      setGeneratingProfile(false);
    }
  };

  return (
    <>
      {/* Job Search Profile - Always visible, compact version */}
      <div className="bg-gradient-to-r from-green-50 to-teal-50 border border-green-200 rounded-lg p-4 mb-4 shadow-sm">
        <div className="flex items-start justify-between gap-4">
          <div className="flex-1">
            <div className="flex items-center gap-2 mb-1">
              <svg className="h-4 w-4 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 13.255A23.931 23.931 0 0112 15c-3.183 0-6.22-.62-9-1.745M16 6V4a2 2 0 00-2-2h-4a2 2 0 00-2 2v2m4 6h.01M5 20h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
              </svg>
              <h3 className="text-sm font-bold text-gray-800">Job Search Profile</h3>
              <span className="px-2 py-0.5 bg-purple-100 text-purple-700 text-xs font-semibold rounded-full">ALPHA</span>
            </div>
            {currentProfile ? (
              <p className="text-xs text-gray-600">
                Profile created! View and edit on the Job Search page to find matching jobs.
              </p>
            ) : (
              <p className="text-xs text-gray-600">
                💡 <span className="font-medium">Tip:</span> Select work experiences below and click <span className="font-semibold">"Generate Mock Job Post"</span> to create a job search profile and find matching opportunities.
              </p>
            )}
          </div>
          {currentProfile && (
            <button
              onClick={() => router.push(`/job-search/${currentProfile.id}`)}
              className="px-6 py-3 bg-green-600 text-white rounded-lg font-semibold hover:bg-green-700 transition-colors flex items-center gap-2 shadow-md"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
              Go to Job Search
            </button>
          )}
        </div>
      </div>

      <div className="bg-gradient-to-r from-blue-50 to-purple-50 border border-blue-200 rounded-lg p-6 mb-4">
        <div className="flex items-center gap-2 mb-4">
          <svg className="h-6 w-6 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
          <h3 className="text-xl font-bold text-gray-800">ATS Analysis Summary</h3>
        </div>

        <div className="space-y-4">
        {/* Contact Information */}
        {(analysis.name || analysis.email || analysis.phone) && (
          <div className="bg-white rounded-lg p-4 shadow-sm">
            <h4 className="font-semibold text-gray-700 mb-2 flex items-center gap-2">
              <svg className="h-4 w-4 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
              </svg>
              Contact Information
            </h4>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-sm">
              {analysis.name && (
                <div><span className="font-medium text-gray-600">Name:</span> {analysis.name}</div>
              )}
              {analysis.email && (
                <div><span className="font-medium text-gray-600">Email:</span> {analysis.email}</div>
              )}
              {analysis.phone && (
                <div><span className="font-medium text-gray-600">Phone:</span> {analysis.phone}</div>
              )}
              {analysis.linkedinUrl && (
                <div><span className="font-medium text-gray-600">LinkedIn:</span> <a href={analysis.linkedinUrl} target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline">Profile</a></div>
              )}
              {analysis.githubUrl && (
                <div><span className="font-medium text-gray-600">GitHub:</span> <a href={analysis.githubUrl} target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline">Profile</a></div>
              )}
              {analysis.websiteUrl && (
                <div><span className="font-medium text-gray-600">Website:</span> <a href={analysis.websiteUrl} target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline">Link</a></div>
              )}
            </div>
          </div>
        )}

        {/* Professional Summary */}
        {analysis.summary && (
          <div className="bg-white rounded-lg p-4 shadow-sm">
            <h4 className="font-semibold text-gray-700 mb-2 flex items-center gap-2">
              <svg className="h-4 w-4 text-purple-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              Professional Summary
            </h4>
            <p className="text-sm text-gray-700 leading-relaxed">{analysis.summary}</p>
          </div>
        )}

        {/* Statistics Grid */}
        <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
          {/* Experience Count */}
          <div className="bg-white rounded-lg p-3 shadow-sm text-center">
            <div className="text-2xl font-bold text-blue-600">{analysis.experiences.length}</div>
            <div className="text-xs text-gray-600 mt-1">Experiences</div>
          </div>

          {/* Skills Count */}
          <div className="bg-white rounded-lg p-3 shadow-sm text-center">
            <div className="text-2xl font-bold text-green-600">{analysis.skills.length}</div>
            <div className="text-xs text-gray-600 mt-1">Skills</div>
          </div>

          {/* Education Count */}
          <div className="bg-white rounded-lg p-3 shadow-sm text-center">
            <div className="text-2xl font-bold text-purple-600">{analysis.educations.length}</div>
            <div className="text-xs text-gray-600 mt-1">Education</div>
          </div>

          {/* Certifications Count */}
          <div className="bg-white rounded-lg p-3 shadow-sm text-center">
            <div className="text-2xl font-bold text-orange-600">{analysis.certifications.length}</div>
            <div className="text-xs text-gray-600 mt-1">Certifications</div>
          </div>

          {/* Projects Count */}
          <div className="bg-white rounded-lg p-3 shadow-sm text-center">
            <div className="text-2xl font-bold text-indigo-600">{analysis.projects.length}</div>
            <div className="text-xs text-gray-600 mt-1">Projects</div>
          </div>
        </div>

        {/* Detailed Sections - Collapsible */}
        <details open className="bg-white rounded-lg shadow-sm">
          <summary className="cursor-pointer p-4 font-semibold text-gray-700 hover:bg-gray-50 rounded-lg">
            Detailed Analysis
          </summary>
          <div className="p-4 space-y-4 border-t">
            {/* Experiences */}
            {analysis.experiences.length > 0 && (
              <div>
                <div className="flex items-center justify-between mb-3">
                  <h5 className="font-semibold text-gray-700">Work Experience</h5>
                  <button
                    onClick={handleGenerateJobPost}
                    disabled={generatingProfile || selectedExperiences.size === 0}
                    className="px-4 py-2 bg-indigo-600 text-white rounded-md text-sm font-medium hover:bg-indigo-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors flex items-center gap-2"
                  >
                    {generatingProfile ? (
                      <>
                        <svg className="animate-spin h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                        </svg>
                        Generating...
                      </>
                    ) : (
                      <>
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                        </svg>
                        Generate Mock Job Post ({selectedExperiences.size} selected)
                      </>
                    )}
                  </button>
                </div>
                <div className="space-y-3">
                  {analysis.experiences.map((exp, index) => (
                    <div key={exp.id} className="text-sm border-l-4 border-blue-400 pl-4 py-2 bg-blue-50 rounded-r relative">
                      {/* Checkbox for Job Post Generation */}
                      <div className="flex items-center gap-2 mb-2">
                        <input
                          type="checkbox"
                          checked={selectedExperiences.has(exp.id)}
                          onChange={() => toggleExperienceSelection(exp.id)}
                          id={`exp-${exp.id}`}
                          className="h-4 w-4 text-indigo-600 rounded cursor-pointer"
                        />
                        <label
                          htmlFor={`exp-${exp.id}`}
                          className="text-xs text-gray-600 font-medium cursor-pointer select-none"
                        >
                          Include in Mock Job Post Generation
                        </label>
                      </div>

                      <div className="flex items-start justify-between mb-1">
                        <div className="font-bold text-gray-900">
                          {exp.jobTitle || 'Position not specified'}
                          {exp.companyName && <span className="font-normal text-gray-700"> at {exp.companyName}</span>}
                        </div>
                        <span className="text-xs bg-blue-200 text-blue-800 px-2 py-0.5 rounded-full ml-2">
                          Job {index + 1}
                        </span>
                      </div>
                      {(exp.startDate || exp.endDate) && (
                        <div className="text-gray-600 text-xs mb-2 flex items-center gap-1">
                          <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                          </svg>
                          {exp.startDate || 'Start date unknown'} - {exp.endDate || 'Present'}
                        </div>
                      )}
                      {exp.description && (
                        <div className="text-gray-700 text-xs leading-relaxed mt-2 mb-3 whitespace-pre-wrap">
                          {exp.description.split('\\n').join('\n')}
                        </div>
                      )}

                      {/* Action Buttons or Job Status */}
                      <div className="mt-3 pt-2 border-t border-blue-200">
                        {/* Show JobStatusIndicator if this job is queued */}
                        {jobAnalysisJobIds[exp.id] ? (
                          <JobStatusIndicator
                            jobId={jobAnalysisJobIds[exp.id]}
                            onComplete={() => handleJobAnalysisComplete(exp.id)}
                            onError={() => handleJobAnalysisError(exp.id)}
                          />
                        ) : (
                          <div className="flex gap-2 flex-wrap">
                            {(() => {
                              const localExp = localAnalysis.experiences.find(e => e.id === exp.id);
                              return localExp?.isAnalyzed ? (
                              <>
                                {/* View Detail Report Button (Primary) */}
                                <button
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    if (localExp.analysisId) {
                                      handleViewAnalysis(localExp.analysisId);
                                    }
                                  }}
                                  className="px-3 py-1.5 bg-indigo-600 text-white rounded-md text-xs font-medium hover:bg-indigo-700 transition-colors flex items-center gap-1.5 shadow-sm disabled:bg-gray-400 disabled:cursor-not-allowed"
                                  title="View detail analysis report"
                                  disabled={!localExp.analysisId}
                                >
                                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                                  </svg>
                                  View generated detail experience report
                                </button>

                                {/* Re-analyze Button (Secondary) */}
                                <button
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    if (confirm('This job has already been analyzed. Re-analyze? This will overwrite the existing analysis.')) {
                                      handleAnalyzeJobClick(exp.id);
                                    }
                                  }}
                                  className="px-3 py-1.5 bg-purple-50 text-purple-600 border border-purple-200 rounded-md text-xs font-medium hover:bg-purple-100 transition-colors flex items-center gap-1"
                                  title="Re-analyze this job (will overwrite existing analysis, costs 50 credits)"
                                >
                                  <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                                  </svg>
                                  Re-analyze (50 credits)
                                </button>
                              </>
                            ) : (
                              /* Analyze Job Button (Primary - First time) */
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  handleAnalyzeJobClick(exp.id);
                                }}
                                className="px-3 py-1.5 bg-purple-600 text-white rounded-md text-xs font-medium hover:bg-purple-700 transition-colors flex items-center gap-1 shadow-sm"
                                title="Analyze this job experience with AI (50 credits)"
                              >
                                <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                                </svg>
                                Generate detail experience analysis report (50 credits)
                              </button>
                            );
                            })()}
                          </div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Skills */}
            {analysis.skills.length > 0 && (
              <div>
                <h5 className="font-semibold text-gray-700 mb-2">Skills</h5>
                <div className="flex flex-wrap gap-2">
                  {analysis.skills.map((skill) => (
                    <span
                      key={skill.id}
                      className="inline-flex items-center bg-green-100 text-green-800 text-xs px-3 py-1.5 rounded-full font-medium"
                    >
                      {skill.skillName}
                      {skill.category && (
                        <span className="ml-1.5 px-1.5 py-0.5 bg-green-200 text-green-900 rounded text-[10px]">
                          {skill.category}
                        </span>
                      )}
                    </span>
                  ))}
                </div>
              </div>
            )}

            {/* Education */}
            {analysis.educations.length > 0 && (
              <div>
                <h5 className="font-semibold text-gray-700 mb-3">Education</h5>
                <div className="space-y-3">
                  {analysis.educations.map((edu) => (
                    <div key={edu.id} className="text-sm border-l-4 border-purple-400 pl-4 py-2 bg-purple-50 rounded-r">
                      <div className="font-bold text-gray-900 mb-1">
                        {edu.degree || 'Degree not specified'}
                      </div>
                      {edu.institution && (
                        <div className="text-gray-700 text-xs mb-1 flex items-center gap-1">
                          <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
                          </svg>
                          {edu.institution}
                        </div>
                      )}
                      {edu.graduationDate && (
                        <div className="text-gray-600 text-xs flex items-center gap-1">
                          <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                          </svg>
                          {edu.graduationDate}
                        </div>
                      )}
                      {edu.description && (
                        <div className="text-gray-700 text-xs leading-relaxed mt-2">
                          {edu.description}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Certifications */}
            {analysis.certifications.length > 0 && (
              <div>
                <h5 className="font-semibold text-gray-700 mb-3">Certifications</h5>
                <div className="space-y-3">
                  {analysis.certifications.map((cert) => (
                    <div key={cert.id} className="text-sm border-l-4 border-orange-400 pl-4 py-2 bg-orange-50 rounded-r">
                      <div className="font-bold text-gray-900 mb-1">
                        {cert.certificationName || 'Certification name not specified'}
                      </div>
                      {cert.issuingOrganization && (
                        <div className="text-gray-700 text-xs mb-1 flex items-center gap-1">
                          <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4M7.835 4.697a3.42 3.42 0 001.946-.806 3.42 3.42 0 014.438 0 3.42 3.42 0 001.946.806 3.42 3.42 0 013.138 3.138 3.42 3.42 0 00.806 1.946 3.42 3.42 0 010 4.438 3.42 3.42 0 00-.806 1.946 3.42 3.42 0 01-3.138 3.138 3.42 3.42 0 00-1.946.806 3.42 3.42 0 01-4.438 0 3.42 3.42 0 00-1.946-.806 3.42 3.42 0 01-3.138-3.138 3.42 3.42 0 00-.806-1.946 3.42 3.42 0 010-4.438 3.42 3.42 0 00.806-1.946 3.42 3.42 0 013.138-3.138z" />
                          </svg>
                          {cert.issuingOrganization}
                        </div>
                      )}
                      <div className="flex items-center gap-3 text-xs text-gray-600">
                        {cert.issueDate && (
                          <div className="flex items-center gap-1">
                            <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                            </svg>
                            Issued: {cert.issueDate}
                          </div>
                        )}
                        {cert.credentialId && (
                          <div className="flex items-center gap-1">
                            <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H5a2 2 0 00-2 2v9a2 2 0 002 2h14a2 2 0 002-2V8a2 2 0 00-2-2h-5m-4 0V5a2 2 0 114 0v1m-4 0a2 2 0 104 0m-5 8a2 2 0 100-4 2 2 0 000 4zm0 0c1.306 0 2.417.835 2.83 2M9 14a3.001 3.001 0 00-2.83 2M15 11h3m-3 4h2" />
                            </svg>
                            ID: {cert.credentialId}
                          </div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Projects */}
            {analysis.projects.length > 0 && (
              <div>
                <h5 className="font-semibold text-gray-700 mb-3">Projects</h5>
                <div className="space-y-3">
                  {analysis.projects.map((proj) => (
                    <div key={proj.id} className="text-sm border-l-4 border-indigo-400 pl-4 py-2 bg-indigo-50 rounded-r">
                      <div className="font-bold text-gray-900 mb-1 flex items-center justify-between">
                        <span>{proj.projectName || 'Project name not specified'}</span>
                        {proj.projectUrl && (
                          <a
                            href={proj.projectUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-indigo-600 hover:text-indigo-800 text-xs flex items-center gap-1"
                          >
                            <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                            </svg>
                            View
                          </a>
                        )}
                      </div>
                      {proj.description && (
                        <div className="text-gray-700 text-xs leading-relaxed mb-2">
                          {proj.description}
                        </div>
                      )}
                      {proj.technologiesUsed && (
                        <div className="text-gray-600 text-xs flex items-start gap-1">
                          <svg className="h-3 w-3 mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
                          </svg>
                          <span className="font-medium">Technologies:</span> {proj.technologiesUsed}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </details>
        </div>
      </div>
    </>
  );
}
