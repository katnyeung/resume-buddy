'use client';

import { ResumeAnalysisDto } from '@/lib/types';

interface AnalysisSummaryProps {
  analysis: ResumeAnalysisDto;
  onAnalyzeJob?: (experienceId: string) => void;
  onFindJobs?: (experienceId: string) => void;
}

export default function AnalysisSummary({ analysis, onAnalyzeJob, onFindJobs }: AnalysisSummaryProps) {
  return (
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
        <details className="bg-white rounded-lg shadow-sm">
          <summary className="cursor-pointer p-4 font-semibold text-gray-700 hover:bg-gray-50 rounded-lg">
            View Detailed Analysis
          </summary>
          <div className="p-4 space-y-4 border-t">
            {/* Experiences */}
            {analysis.experiences.length > 0 && (
              <div>
                <h5 className="font-semibold text-gray-700 mb-3">Work Experience</h5>
                <div className="space-y-3">
                  {analysis.experiences.map((exp, index) => (
                    <div key={exp.id} className="text-sm border-l-4 border-blue-400 pl-4 py-2 bg-blue-50 rounded-r">
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
                        <div className="text-gray-700 text-xs leading-relaxed mt-2 mb-3 whitespace-pre-line">
                          {exp.description}
                        </div>
                      )}

                      {/* Action Buttons */}
                      <div className="flex gap-2 mt-3 pt-2 border-t border-blue-200">
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            onAnalyzeJob?.(exp.id);
                          }}
                          className="px-3 py-1.5 bg-purple-100 text-purple-700 rounded-md text-xs font-medium hover:bg-purple-200 transition-colors flex items-center gap-1"
                          title="Analyze this job experience with AI (Coming soon)"
                        >
                          <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                          </svg>
                          Analyze Job
                        </button>

                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            onFindJobs?.(exp.id);
                          }}
                          className="px-3 py-1.5 bg-green-100 text-green-700 rounded-md text-xs font-medium hover:bg-green-200 transition-colors flex items-center gap-1"
                          title="Find similar job opportunities (Coming soon)"
                        >
                          <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                          </svg>
                          Find Similar Jobs
                        </button>
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
  );
}
