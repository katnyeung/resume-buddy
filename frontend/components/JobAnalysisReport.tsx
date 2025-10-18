'use client';

import React, { useState } from 'react';

interface JobAnalysisReportProps {
  analysis: any; // Will be typed properly from API
}

export default function JobAnalysisReport({ analysis }: JobAnalysisReportProps) {
  if (!analysis) {
    return (
      <div className="p-8 text-center text-gray-500">
        No analysis data available
      </div>
    );
  }

  const formatScore = (score: number) => {
    return (score * 10).toFixed(0);
  };

  const getScoreColor = (score: number) => {
    if (score >= 8) return 'text-green-600';
    if (score >= 6) return 'text-yellow-600';
    return 'text-red-600';
  };

  return (
    <div className="max-w-5xl mx-auto p-6 space-y-8">
      {/* Header */}
      <div className="border-b pb-6">
        <div className="flex justify-between items-start">
          <div>
            <h1 className="text-3xl font-bold text-gray-900">
              Job Analysis Results
            </h1>
            <p className="text-lg text-gray-600 mt-2">
              {analysis.jobTitle} at {analysis.companyName}
            </p>
            <p className="text-sm text-gray-500 mt-1">
              {analysis.startDate} - {analysis.endDate}
            </p>
          </div>
          <div className="text-right">
            <div className="text-sm text-gray-500">Analyzed on</div>
            <div className="text-sm font-medium">
              {new Date(analysis.createdAt).toLocaleString('en-US', {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit',
                timeZoneName: 'short'
              })}
            </div>
          </div>
        </div>

        <div className="mt-4 flex items-center gap-4 flex-wrap">
          <span className="inline-block px-3 py-1 bg-blue-100 text-blue-800 rounded-full text-sm font-medium">
            {analysis.normalizedTitle}
          </span>
          <span className="inline-block px-3 py-1 bg-gray-100 text-gray-700 rounded-full text-sm">
            {analysis.seniorityLevel} Level
          </span>
          {analysis.hasLeadership && (
            <span className="inline-block px-3 py-1 bg-purple-100 text-purple-800 rounded-full text-sm">
              👥 {analysis.leadershipScope} Leadership
            </span>
          )}
        </div>

        {/* Industry Classification */}
        {(analysis.primaryIndustry || analysis.industryVertical || (analysis.industrySectors && analysis.industrySectors.length > 0)) && (
          <div className="mt-4 bg-gradient-to-r from-emerald-50 to-teal-50 border border-emerald-200 rounded-lg p-4">
            <div className="flex items-start gap-3">
              <span className="text-2xl">🏢</span>
              <div className="flex-1">
                <h3 className="text-sm font-semibold text-emerald-900 mb-2">Industry Classification</h3>
                <div className="space-y-2">
                  {analysis.primaryIndustry && (
                    <div className="flex items-center gap-2">
                      <span className="text-xs text-emerald-700 font-medium min-w-20">Primary:</span>
                      <span className="inline-block px-3 py-1 bg-emerald-100 text-emerald-800 rounded-full text-sm font-medium">
                        {analysis.primaryIndustry}
                      </span>
                    </div>
                  )}
                  {analysis.industryVertical && (
                    <div className="flex items-center gap-2">
                      <span className="text-xs text-emerald-700 font-medium min-w-20">Vertical:</span>
                      <span className="inline-block px-3 py-1 bg-teal-100 text-teal-800 rounded-full text-sm font-medium">
                        {analysis.industryVertical}
                      </span>
                    </div>
                  )}
                  {analysis.industrySectors && analysis.industrySectors.length > 0 && (
                    <div className="flex items-start gap-2">
                      <span className="text-xs text-emerald-700 font-medium min-w-20 pt-1">Sectors:</span>
                      <div className="flex flex-wrap gap-2">
                        {analysis.industrySectors.map((sector: string, idx: number) => (
                          <span key={idx} className="inline-block px-2 py-1 bg-white border border-emerald-300 text-emerald-800 rounded text-xs">
                            {sector}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Job Families & Key Responsibilities - TOP PRIORITY */}
      <div className="grid grid-cols-2 gap-4">
        {analysis.jobFamilies && analysis.jobFamilies.length > 0 && (
          <div className="bg-white border rounded-lg p-6">
            <h3 className="font-semibold mb-3">Job Families</h3>
            <div className="flex flex-wrap gap-2">
              {analysis.jobFamilies.map((family: string, idx: number) => (
                <span key={idx} className="bg-gray-100 text-gray-800 px-3 py-1 rounded-full text-sm">
                  {family}
                </span>
              ))}
            </div>
          </div>
        )}

        {analysis.keyResponsibilities && analysis.keyResponsibilities.length > 0 && (
          <div className="bg-white border rounded-lg p-6">
            <h3 className="font-semibold mb-3">Key Responsibilities</h3>
            <ul className="text-sm space-y-2">
              {analysis.keyResponsibilities.map((resp: string, idx: number) => (
                <li key={idx} className="flex gap-2">
                  <span className="text-gray-400">•</span>
                  <span>{resp}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>

      {/* SOC Code & Occupation Mappings */}
      {analysis.socCodes && analysis.socCodes.length > 0 && (
        <div className="bg-white border rounded-lg p-6">
          <h2 className="text-xl font-semibold mb-4">O*NET Occupation Mappings</h2>
          <div className="space-y-3">
            {analysis.socCodes.map((soc: any, idx: number) => (
              <div key={soc.code} className="flex items-center justify-between border-l-4 border-blue-500 pl-4 py-2">
                <div>
                  <div className="font-medium">{soc.title}</div>
                  <div className="text-sm text-gray-500">SOC Code: {soc.code}</div>
                </div>
                <div className="text-right">
                  <div className="text-2xl font-bold text-blue-600">
                    {(soc.confidence * 100).toFixed(0)}%
                  </div>
                  <div className="text-xs text-gray-500">
                    {idx === 0 ? 'Primary Match' : 'Secondary Match'}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Score Cards */}
      <div className="grid grid-cols-4 gap-4">
        <div className="bg-white border rounded-lg p-6 text-center">
          <div className={`text-4xl font-bold ${getScoreColor(analysis.overallScore)}`}>
            {formatScore(analysis.overallScore)}/100
          </div>
          <div className="text-sm text-gray-600 mt-2">Overall Score</div>
        </div>
        <div className="bg-white border rounded-lg p-6 text-center">
          <div className={`text-4xl font-bold ${getScoreColor(analysis.impactScore)}`}>
            {formatScore(analysis.impactScore)}/100
          </div>
          <div className="text-sm text-gray-600 mt-2">Impact</div>
        </div>
        <div className="bg-white border rounded-lg p-6 text-center">
          <div className={`text-4xl font-bold ${getScoreColor(analysis.technicalDepthScore)}`}>
            {formatScore(analysis.technicalDepthScore)}/100
          </div>
          <div className="text-sm text-gray-600 mt-2">Technical</div>
        </div>
        <div className="bg-white border rounded-lg p-6 text-center">
          <div className={`text-4xl font-bold ${getScoreColor(analysis.leadershipScore)}`}>
            {formatScore(analysis.leadershipScore)}/100
          </div>
          <div className="text-sm text-gray-600 mt-2">Leadership</div>
        </div>
      </div>

      {/* Recruiter Evaluation */}
      <div className="bg-gradient-to-r from-blue-50 to-indigo-50 border border-blue-200 rounded-lg p-6">
        <h2 className="text-xl font-semibold mb-3 flex items-center gap-2">
          <span>💼</span> Recruiter Evaluation
        </h2>
        <p className="text-gray-800 leading-relaxed whitespace-pre-wrap">
          {analysis.recruiterSummary}
        </p>
      </div>

      {/* Key Strengths */}
      {analysis.keyStrengths && analysis.keyStrengths.length > 0 && (
        <div className="bg-white border rounded-lg p-6">
          <h2 className="text-xl font-semibold mb-4 flex items-center gap-2">
            <span className="text-green-600">✓</span> Key Strengths
          </h2>
          <ul className="space-y-3">
            {analysis.keyStrengths.map((strength: string, idx: number) => (
              <li key={idx} className="flex gap-3">
                <span className="text-green-600 font-bold flex-shrink-0">{idx + 1}.</span>
                <span className="text-gray-800">{strength}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Areas for Improvement */}
      {analysis.improvementAreas && analysis.improvementAreas.length > 0 && (
        <div className="bg-white border rounded-lg p-6">
          <h2 className="text-xl font-semibold mb-4 flex items-center gap-2">
            <span className="text-orange-600">⚠</span> Areas for Improvement
          </h2>
          <ul className="space-y-3">
            {analysis.improvementAreas.map((improvement: string, idx: number) => (
              <li key={idx} className="flex gap-3">
                <span className="text-orange-600 font-bold flex-shrink-0">{idx + 1}.</span>
                <span className="text-gray-800">{improvement}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Achievement Analysis (MERGED with Line Value Analysis) */}
      {analysis.descriptionLineMappings && analysis.descriptionLineMappings.length > 0 && (
        <div className="bg-white border rounded-lg p-6">
          <h2 className="text-xl font-semibold mb-4 flex items-center gap-2">
            <span>💎</span> Achievement Analysis
          </h2>
          <p className="text-sm text-gray-600 mb-4">
            Your description lines with O*NET task mappings and skill showcase value
          </p>
          <div className="space-y-4">
            {analysis.descriptionLineMappings.map((line: any, idx: number) => {
              // Find matching value analysis data if available
              const valueData = analysis.descriptionLineValues?.find(
                (v: any) => v.sequence === line.sequence
              );

              return (
                <div key={idx} className="border rounded-lg p-4 hover:shadow-md transition-shadow">
                  <div className="flex items-start justify-between mb-2">
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-sm text-gray-600">Line {line.sequence}</span>
                        {valueData && <ValueRatingBadge rating={valueData.valueRating} />}
                        {line.impactLevel && (
                          <span className={`px-3 py-1 rounded-full text-xs font-semibold ${
                            line.impactLevel === 'Critical' ? 'bg-red-100 text-red-800' :
                            line.impactLevel === 'High' ? 'bg-orange-100 text-orange-800' :
                            line.impactLevel === 'Medium' ? 'bg-yellow-100 text-yellow-800' :
                            'bg-gray-100 text-gray-800'
                          }`}>
                            {line.impactLevel} Impact
                          </span>
                        )}
                      </div>
                      <p className="text-gray-800">{line.text}</p>
                    </div>
                    {valueData && (
                      <div className="ml-4 text-center flex-shrink-0">
                        <div className="text-2xl font-bold text-blue-600">{valueData.skillCount}</div>
                        <div className="text-xs text-gray-500">skills</div>
                      </div>
                    )}
                  </div>

                  {line.impactMetrics && (
                    <div className="text-sm text-green-700 bg-green-50 px-3 py-1 rounded mt-2 inline-block">
                      📊 {line.impactMetrics}
                    </div>
                  )}

                  {/* Show skills showcased if available from value analysis */}
                  {valueData && valueData.skillsShowcased && valueData.skillsShowcased.length > 0 && (
                    <div className="mt-2 flex flex-wrap gap-2 items-center">
                      <span className="text-xs text-gray-500">Showcases:</span>
                      {valueData.skillsShowcased.map((skillName: string, sIdx: number) => (
                        <span key={sIdx} className="text-xs bg-indigo-50 text-indigo-700 px-2 py-1 rounded">
                          {skillName}
                        </span>
                      ))}
                    </div>
                  )}

                  {/* Recruiter Perspective Section */}
                  {line.recruiterInsights && (
                    <div className="mt-4 bg-gradient-to-r from-purple-50 to-blue-50 border border-purple-200 rounded-lg p-4">
                      <h4 className="font-semibold text-purple-900 mb-3 flex items-center gap-2">
                        <span>🔍</span> Recruiter Perspective
                      </h4>

                      {/* Strong Signals */}
                      {line.recruiterInsights.strongSignals && line.recruiterInsights.strongSignals.length > 0 && (
                        <div className="mb-3">
                          <p className="text-sm font-medium text-gray-700 mb-2">✅ Strong Signals:</p>
                          <div className="space-y-1">
                            {line.recruiterInsights.strongSignals.map((signal: any, i: number) => (
                              <div key={i} className="text-sm ml-4">
                                • <span className="font-medium">{signal.category}:</span> {signal.insight}
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* Potential Questions */}
                      {line.recruiterInsights.potentialQuestions && line.recruiterInsights.potentialQuestions.length > 0 && (
                        <div className="mb-3">
                          <p className="text-sm font-medium text-gray-700 mb-2">⚠️ Potential Questions:</p>
                          <div className="space-y-1">
                            {line.recruiterInsights.potentialQuestions.map((q: string, i: number) => (
                              <div key={i} className="text-sm ml-4 text-gray-600">• {q}</div>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* STARS Analysis */}
                      {line.recruiterInsights.starsAnalysis && line.recruiterInsights.starsAnalysis.length > 0 && (
                        <div className="mb-3">
                          <p className="text-sm font-medium text-gray-700 mb-2">📋 STARS Suggestions:</p>
                          <div className="space-y-1">
                            {line.recruiterInsights.starsAnalysis.map((suggestion: string, i: number) => (
                              <div key={i} className="text-sm ml-4 text-indigo-700">• {suggestion}</div>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* Appeal Score */}
                      {line.recruiterInsights.recruiterAppealScore !== undefined && (
                        <div className="flex items-center gap-3 mt-3 pt-3 border-t border-purple-200">
                          <span className="text-2xl font-bold text-purple-700">
                            {line.recruiterInsights.recruiterAppealScore}/10
                          </span>
                          <div className="flex-1">
                            <p className="text-xs text-gray-600">Recruiter Appeal</p>
                            {line.recruiterInsights.appealReasoning && (
                              <p className="text-sm text-gray-700">{line.recruiterInsights.appealReasoning}</p>
                            )}
                          </div>
                        </div>
                      )}

                      {/* Best Fit Roles */}
                      {line.recruiterInsights.bestFitRoles && line.recruiterInsights.bestFitRoles.length > 0 && (
                        <div className="mt-3 pt-3 border-t border-purple-200">
                          <p className="text-xs text-gray-600 mb-2">🎯 Best Fit For:</p>
                          <div className="flex flex-wrap gap-2">
                            {line.recruiterInsights.bestFitRoles.map((role: string, i: number) => (
                              <span key={i} className="text-xs bg-purple-100 text-purple-700 px-2 py-1 rounded">
                                {role}
                              </span>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* Red Flags */}
                      {line.recruiterInsights.redFlags && line.recruiterInsights.redFlags.length > 0 && (
                        <div className="mt-3 pt-3 border-t border-red-200">
                          <p className="text-sm font-medium text-red-700 mb-2">🚩 Red Flags:</p>
                          <div className="space-y-1">
                            {line.recruiterInsights.redFlags.map((flag: string, i: number) => (
                              <div key={i} className="text-sm ml-4 text-red-600">• {flag}</div>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
                  )}

                  {valueData && valueData.valueRating === 'LOW' && (
                    <div className="mt-2 text-xs text-gray-500 bg-gray-50 px-3 py-2 rounded italic">
                      💡 Consider strengthening this line by adding quantifiable achievements or technical details
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Extracted Skills (MERGED with Evidence Crossing + Missing Skills) */}
      {analysis.extractedSkills && analysis.extractedSkills.length > 0 && (
        <SkillsTable
          skills={analysis.extractedSkills}
          evidenceData={analysis.skillDemonstrationAnalysis}
          missingSkills={analysis.missingSkillsByCategory}
          resumeId={analysis.resumeId}
          experienceId={analysis.experienceId}
        />
      )}

      {/* PHASE 6: DEEP GRAPH ANALYSIS - Priority Order */}

      {/* Section 1: Untapped Opportunities (HIGHEST PRIORITY) */}
      {analysis.missingOpportunities && analysis.missingOpportunities.length > 0 && (
        <div className="bg-gradient-to-r from-amber-50 to-orange-50 border border-amber-200 rounded-lg p-6">
          <h2 className="text-xl font-semibold mb-4 flex items-center gap-2">
            <span>💡</span> Untapped Opportunities
          </h2>
          <p className="text-sm text-gray-700 mb-4">
            You have skills that could demonstrate these important O*NET tasks, but you don't currently showcase them
          </p>
          <div className="space-y-3">
            {analysis.missingOpportunities.map((opp: any, idx: number) => (
              <div key={idx} className="bg-white border-l-4 border-amber-400 rounded p-4">
                <div className="flex justify-between items-start">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-sm font-semibold text-amber-900">
                        {opp.taskName}
                      </span>
                      <span className="text-xs bg-amber-100 text-amber-800 px-2 py-1 rounded">
                        {opp.importance.toFixed(0)}% importance
                      </span>
                    </div>
                    <p className="text-sm text-gray-700 mt-2">
                      Your <strong>{opp.skillName}</strong> skills are relevant to this task.
                      Add a specific achievement that demonstrates this capability.
                    </p>
                    <div className="text-xs text-gray-500 mt-1">
                      {opp.taskCategory} • {opp.occupationTitle}
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Section 2: Missing High-Priority Tasks & Activities */}
      {((analysis.missingTasks && analysis.missingTasks.length > 0) ||
        (analysis.missingActivities && analysis.missingActivities.length > 0)) && (
        <div className="bg-gradient-to-r from-red-50 to-pink-50 border border-red-200 rounded-lg p-6">
          <h2 className="text-xl font-semibold mb-4 flex items-center gap-2">
            <span>🎯</span> Missing High-Priority Tasks & Activities
          </h2>
          <p className="text-sm text-gray-700 mb-4">
            Important O*NET competencies for your role that you don't currently demonstrate
          </p>

          {/* Missing Tasks */}
          {analysis.missingTasks && analysis.missingTasks.length > 0 && (
            <div className="mb-4">
              <h3 className="text-md font-semibold text-gray-800 mb-2">Missing Tasks</h3>
              <div className="space-y-2">
                {analysis.missingTasks.map((task: any, idx: number) => (
                  <div key={idx} className="bg-white rounded p-3 border-l-4 border-red-400">
                    <div className="flex justify-between items-center gap-4">
                      <div className="flex-1">
                        <div className="text-sm font-medium text-gray-800">{task.taskName}</div>
                        <div className="text-xs text-gray-500 mt-1">
                          {task.category}
                        </div>
                      </div>
                      <ImportanceIndicator score={task.importance} />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Missing Activities */}
          {analysis.missingActivities && analysis.missingActivities.length > 0 && (
            <div>
              <h3 className="text-md font-semibold text-gray-800 mb-2">Missing Activities</h3>
              <div className="space-y-2">
                {analysis.missingActivities.map((activity: any, idx: number) => (
                  <div key={idx} className="bg-white rounded p-3 border-l-4 border-red-400">
                    <div className="flex justify-between items-center gap-4">
                      <div className="flex-1">
                        <div className="text-sm font-medium text-gray-800">{activity.activityName}</div>
                      </div>
                      <ImportanceIndicator score={activity.importance} />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ==================== HELPER COMPONENTS (PHASE 6) ====================

// Skill Evidence Cell component - simple display
// Trust Badge Component - Shows evidence strength in recruiter-friendly way
function TrustBadge({ evidenceStrength, linesCount, onClick }: {
  evidenceStrength: string,
  linesCount: number,
  onClick: () => void
}) {
  // Dynamic icon for STRONG based on number of connections
  const getStrongIcon = (count: number): string => {
    if (count >= 3) return '●●●';
    if (count === 2) return '●●';
    if (count === 1) return '●';
    return '●'; // Fallback
  };

  const badges = {
    STRONG: {
      icon: getStrongIcon(linesCount),
      text: 'Demonstrated in Resume',
      color: 'text-green-700 bg-green-50 border-green-300',
      hoverColor: 'hover:bg-green-100'
    },
    MODERATE: {
      icon: '⚠️',
      text: 'Limited Evidence',
      color: 'text-yellow-700 bg-yellow-50 border-yellow-300',
      hoverColor: 'hover:bg-yellow-100'
    },
    WEAK: {
      icon: '⚠️',
      text: 'Indirect Connection',
      color: 'text-orange-700 bg-orange-50 border-orange-300',
      hoverColor: 'hover:bg-orange-100'
    },
    NONE: {
      icon: '❌',
      text: 'No Evidence Found',
      color: 'text-red-700 bg-red-50 border-red-300',
      hoverColor: 'hover:bg-red-100'
    }
  };

  const badge = badges[evidenceStrength as keyof typeof badges] || badges.NONE;

  return (
    <div
      className={`flex items-center gap-2 px-3 py-1.5 rounded-md border cursor-pointer ${badge.color} ${badge.hoverColor} transition-colors`}
      onClick={onClick}
    >
      <span className="text-base">{badge.icon}</span>
      <div className="flex flex-col">
        <span className="text-xs font-semibold">{badge.text}</span>
        {linesCount > 0 && (
          <span className="text-[10px] opacity-75">{linesCount} {linesCount === 1 ? 'connection' : 'connections'}</span>
        )}
      </div>
    </div>
  );
}

function SkillsTable({ skills, evidenceData, missingSkills, resumeId, experienceId }: {
  skills: any[],
  evidenceData?: any[],
  missingSkills?: any[],
  resumeId: string,
  experienceId: string
}) {
  const [showAll, setShowAll] = useState(false);
  const [showLowImportance, setShowLowImportance] = useState(false);
  const [expandedSkill, setExpandedSkill] = useState<string | null>(null);

  // PHASE 6.5: Track which skills have loaded popularity data
  const [skillPopularityCache, setSkillPopularityCache] = useState<Record<string, any>>({});
  const [loadingSkills, setLoadingSkills] = useState<Set<string>>(new Set());

  // PHASE 6.5: Fetch task popularity data on-demand
  const fetchSkillPopularity = async (skillName: string, resumeId: string, experienceId: string) => {
    // Check if already loading or cached
    if (loadingSkills.has(skillName) || skillPopularityCache[skillName]) {
      return;
    }

    setLoadingSkills(prev => new Set(prev).add(skillName));

    try {
      const encodedSkillName = encodeURIComponent(skillName);
      const token = localStorage.getItem('authToken');
      const headers: HeadersInit = {
        'Content-Type': 'application/json'
      };
      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      const response = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/resumes/${resumeId}/experiences/${experienceId}/skills/${encodedSkillName}/task-popularity`,
        { headers }
      );

      if (response.ok) {
        const data = await response.json();
        setSkillPopularityCache(prev => ({
          ...prev,
          [skillName]: data
        }));
      } else {
        console.error(`Failed to fetch popularity for ${skillName}:`, response.statusText);
      }
    } catch (error) {
      console.error(`Error fetching popularity for ${skillName}:`, error);
    } finally {
      setLoadingSkills(prev => {
        const newSet = new Set(prev);
        newSet.delete(skillName);
        return newSet;
      });
    }
  };

  // Merge skills with evidence data and calculate evidence strength
  const enrichedSkills = skills.map(skill => {
    const evidence = evidenceData?.find((ev: any) => ev.skillName === skill.name);
    const linesShowcasing = evidence?.linesShowcasing || 0;
    const tasksLinked = evidence?.tasksLinked || 0;
    const evidenceStrength = evidence?.evidenceStrength || 'NONE';

    // PHASE 6.5: Use cached popularity data if available
    const cachedPopularity = skillPopularityCache[skill.name];

    return {
      ...skill,
      linesShowcasing,
      tasksLinked,
      exampleLines: evidence?.exampleLines || [],
      taskNames: evidence?.taskNames || [],
      avgTaskImportance: evidence?.avgTaskImportance || 0,
      evidenceStrength,
      isMissing: false,
      // PHASE 6.5: Use cached task-level popularity data
      taskPopularityData: cachedPopularity?.taskPopularityData || [],
      cooccurringSkills: cachedPopularity?.cooccurringSkills || [],
      isLoadingPopularity: loadingSkills.has(skill.name)
    };
  });

  // Add missing skills (filter out Domain Expertise/Knowledge categories)
  const missingEnrichedSkills = (missingSkills || [])
    .filter(skill => {
      const category = skill.category || '';
      return !category.includes('Domain Expertise') && !category.includes('Domain Knowledge');
    })
    .map(skill => ({
      name: skill.skillName,
      category: skill.category,
      linesShowcasing: 0,
      tasksLinked: skill.requiredByTasks,
      proficiencyLevel: 0,
      isPrimary: false,
      isMissing: true,
      avgImportance: skill.avgImportance,
      taskNames: skill.taskNames || [],
      evidenceStrength: 'NONE'
    }));

  // Group skills by evidence strength (recruiter priority: WEAK/NONE first)
  const strengthOrder = { 'NONE': 0, 'WEAK': 1, 'MODERATE': 2, 'STRONG': 3 };
  const unsupportedSkills = enrichedSkills.filter(s => s.evidenceStrength === 'NONE');
  const weakSkills = enrichedSkills.filter(s => s.evidenceStrength === 'WEAK');
  const moderateSkills = enrichedSkills.filter(s => s.evidenceStrength === 'MODERATE');
  const strongSkills = enrichedSkills.filter(s => s.evidenceStrength === 'STRONG');

  // Sort each group by isPrimary first, then proficiency
  const sortGroup = (group: any[]) => {
    return group.sort((a, b) => {
      if (a.isPrimary !== b.isPrimary) return b.isPrimary ? 1 : -1;
      return (b.proficiencyLevel || 0) - (a.proficiencyLevel || 0);
    });
  };

  const sortedUnsupported = sortGroup([...unsupportedSkills]);
  const sortedWeak = sortGroup([...weakSkills]);
  const sortedModerate = sortGroup([...moderateSkills]);
  const sortedStrong = sortGroup([...strongSkills]);

  // Sort ALL missing skills by importance (descending), then by task count
  missingEnrichedSkills.sort((a, b) => {
    // First by importance (descending - most important first)
    const importanceDiff = (b.avgImportance || 0) - (a.avgImportance || 0);
    if (importanceDiff !== 0) {
      return importanceDiff;
    }
    // Then by number of tasks matched (descending - most tasks first)
    return b.tasksLinked - a.tasksLinked;
  });

  // Determine initial visible count - show top 10 skills by default
  const defaultVisibleCount = Math.min(10, missingEnrichedSkills.length);
  const remainingCount = missingEnrichedSkills.length - defaultVisibleCount;

  // Count totals
  const totalHave = enrichedSkills.length;
  const totalMissing = missingEnrichedSkills.length;

  // States for collapsible sections
  const [showUnsupported, setShowUnsupported] = useState(true);
  const [showWeak, setShowWeak] = useState(true);
  const [showModerate, setShowModerate] = useState(false);
  const [showStrong, setShowStrong] = useState(false);

  // Get actionable suggestion based on evidence strength and graph metrics
  const getSuggestion = (evidenceStrength: string, isPrimary: boolean, linesShowcasing: number, tasksLinked: number): string => {
    if (evidenceStrength === 'NONE') {
      return isPrimary
        ? '🚨 CRITICAL: This is a primary skill but has no evidence. Add specific achievement OR remove from resume to avoid appearing dishonest.'
        : '🚨 Remove from resume OR add specific achievement demonstrating this skill.';
    }
    if (evidenceStrength === 'WEAK') {
      const taskWord = tasksLinked === 1 ? 'task' : 'tasks';
      return `💡 This skill links to ${tasksLinked} required ${taskWord}, but no resume lines demonstrate them. Add specific examples showing how you used this skill.`;
    }
    if (evidenceStrength === 'MODERATE') {
      const lineWord = linesShowcasing === 1 ? 'line' : 'lines';
      return `💡 Recruiters only see ${linesShowcasing} resume ${lineWord} for this skill. This may limit their understanding of your proficiency. Consider adding more examples.`;
    }
    return '';
  };

  // Helper function to render skill rows
  const renderSkillRow = (skill: any, idx: number) => (
    <React.Fragment key={idx}>
      <tr
        className={`border-b border-gray-100 hover:bg-gray-50 ${
          expandedSkill === skill.name ? 'bg-blue-50' : ''
        }`}
      >
        {/* Skill Name */}
        <td className="py-2 px-3">
          <div className="flex items-center gap-2">
            <span className="font-medium">{skill.name}</span>
            {skill.isPrimary && (
              <span className="text-xs bg-green-100 text-green-800 px-2 py-0.5 rounded">
                ⭐ Primary
              </span>
            )}
          </div>
        </td>

        {/* Category */}
        <td className="py-2 px-3">
          <span className="text-sm text-gray-700">{skill.category || 'Uncategorized'}</span>
        </td>

        {/* Trust Badge */}
        <td className="py-2 px-3">
          <TrustBadge
            evidenceStrength={skill.evidenceStrength}
            linesCount={skill.linesShowcasing}
            onClick={() => {
              const isExpanding = expandedSkill !== skill.name;
              setExpandedSkill(isExpanding ? skill.name : null);

              // PHASE 6.5: Fetch task popularity when expanding (only if has tasks)
              if (isExpanding && skill.tasksLinked > 0) {
                fetchSkillPopularity(skill.name, resumeId, experienceId);
              }
            }}
          />
        </td>
      </tr>

      {/* Expanded Detail Row */}
      {expandedSkill === skill.name && (
        <tr>
          <td colSpan={3} className="bg-gray-50 px-6 py-4 border-b border-gray-200">
            <div className="space-y-3">
              {/* Resume Examples */}
              {skill.exampleLines && skill.exampleLines.length > 0 ? (
                <div>
                  <div className="text-sm font-semibold text-blue-900 mb-2">
                    📄 Resume Evidence ({skill.linesShowcasing} {skill.linesShowcasing === 1 ? 'line' : 'lines'})
                  </div>
                  <div className="space-y-2">
                    {skill.exampleLines.map((line: string, i: number) => (
                      <div key={i} className="text-sm text-gray-700 bg-blue-50 border-l-4 border-blue-400 pl-3 py-2 rounded">
                        {line}
                      </div>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="text-sm text-gray-600 bg-gray-100 p-3 rounded border-l-4 border-gray-400">
                  No resume lines demonstrate this skill
                </div>
              )}

              {/* Graph Connection Details with Task-Level Popularity (PHASE 6.5) */}
              {skill.tasksLinked > 0 && (
                <div className="bg-indigo-50 border border-indigo-200 rounded p-3">
                  <div className="text-sm font-semibold text-indigo-900 mb-2 flex items-center justify-between">
                    <span>🔗 Graph Connection</span>
                    {skill.avgTaskImportance && skill.avgTaskImportance > 0 && (
                      <span className="text-xs bg-indigo-100 text-indigo-800 px-2 py-1 rounded font-semibold">
                        {skill.avgTaskImportance.toFixed(0)}% importance
                      </span>
                    )}
                  </div>
                  <div className="text-xs text-indigo-800">
                    Links to <strong>{skill.tasksLinked}</strong> O*NET {skill.tasksLinked === 1 ? 'task' : 'tasks'}

                    {/* PHASE 6.5: Loading indicator */}
                    {skill.isLoadingPopularity && (
                      <div className="mt-3 text-center py-4">
                        <div className="inline-block animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600"></div>
                        <div className="text-xs text-indigo-600 mt-2">Loading popularity data...</div>
                      </div>
                    )}

                    {/* Task-Level Popularity Display */}
                    {skill.taskPopularityData && skill.taskPopularityData.length > 0 && (
                      <div className="mt-3 space-y-3">
                        {skill.taskPopularityData.map((taskPop: any, tIdx: number) => (
                          <div key={tIdx} className="bg-white border border-indigo-300 rounded p-2">
                            <div className="font-semibold text-indigo-900 text-xs mb-2">
                              📋 {taskPop.taskName}
                            </div>

                            {/* Skill Popularity for this task */}
                            {taskPop.skillPopularity && taskPop.skillPopularity.length > 0 && (
                              <div className="mb-2">
                                <div className="text-[11px] text-gray-600 mb-1 font-semibold">
                                  Skills used for this task:
                                </div>
                                <div className="space-y-1">
                                  {taskPop.skillPopularity.slice(0, 5).map((skillPop: any, sIdx: number) => (
                                    <div
                                      key={sIdx}
                                      className={`flex items-center justify-between text-[11px] px-2 py-1 rounded ${
                                        skillPop.userHasSkill
                                          ? 'bg-green-100 text-green-900 border border-green-300'
                                          : 'bg-gray-50 text-gray-700'
                                      }`}
                                    >
                                      <span className="flex-1">
                                        {skillPop.userHasSkill && '✓ '}
                                        {skillPop.skillName}
                                      </span>
                                      <span className="font-semibold ml-2">
                                        {skillPop.peopleWithSkill} {skillPop.peopleWithSkill === 1 ? 'person' : 'people'}
                                      </span>
                                    </div>
                                  ))}
                                  {taskPop.skillPopularity.length > 5 && (
                                    <div className="text-[10px] text-gray-500 italic px-2">
                                      ...and {taskPop.skillPopularity.length - 5} more skills
                                    </div>
                                  )}
                                </div>
                              </div>
                            )}

                            {/* Missing skills for this task */}
                            {taskPop.missingSkills && taskPop.missingSkills.length > 0 && (
                              <div className="mt-2 pt-2 border-t border-amber-200">
                                <div className="text-[11px] text-amber-700 mb-1 font-semibold">
                                  🔍 Competitive gaps for this task:
                                </div>
                                <div className="space-y-1">
                                  {taskPop.missingSkills.slice(0, 3).map((missing: any, mIdx: number) => (
                                    <div
                                      key={mIdx}
                                      className="flex items-center justify-between text-[11px] px-2 py-1 bg-amber-50 text-amber-900 rounded border border-amber-200"
                                    >
                                      <span className="flex-1">{missing.skillName}</span>
                                      <span className="font-semibold ml-2">
                                        {missing.peopleWithSkill} {missing.peopleWithSkill === 1 ? 'person' : 'people'}
                                      </span>
                                    </div>
                                  ))}
                                  {taskPop.missingSkills.length > 3 && (
                                    <div className="text-[10px] text-amber-600 italic px-2">
                                      ...and {taskPop.missingSkills.length - 3} more
                                    </div>
                                  )}
                                </div>
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    )}

                    {/* Skill Co-occurrence Display */}
                    {skill.cooccurringSkills && skill.cooccurringSkills.length > 0 && (
                      <div className="mt-3 bg-white border border-purple-300 rounded p-2">
                        <div className="text-[11px] text-purple-900 mb-2 font-semibold">
                          🔗 Skills commonly paired with {skill.name}:
                        </div>
                        <div className="space-y-1">
                          {skill.cooccurringSkills.slice(0, 5).map((coSkill: any, cIdx: number) => (
                            <div
                              key={cIdx}
                              className={`flex items-center justify-between text-[11px] px-2 py-1 rounded ${
                                coSkill.userHasSkill
                                  ? 'bg-green-100 text-green-900 border border-green-300'
                                  : 'bg-gray-50 text-gray-700'
                              }`}
                            >
                              <span className="flex-1">
                                {coSkill.userHasSkill && '✓ '}
                                {coSkill.skillName}
                              </span>
                              <span className="font-semibold ml-2">
                                {coSkill.percentage.toFixed(0)}% ({coSkill.peopleCount} {coSkill.peopleCount === 1 ? 'person' : 'people'})
                              </span>
                            </div>
                          ))}
                          {skill.cooccurringSkills.length > 5 && (
                            <div className="text-[10px] text-gray-500 italic px-2">
                              ...and {skill.cooccurringSkills.length - 5} more
                            </div>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* Actionable Suggestion */}
              {getSuggestion(skill.evidenceStrength, skill.isPrimary, skill.linesShowcasing, skill.tasksLinked) && (
                <div className={`text-sm p-3 rounded border-l-4 ${
                  skill.evidenceStrength === 'NONE' ? 'bg-red-50 border-red-400 text-red-800' :
                  skill.evidenceStrength === 'WEAK' ? 'bg-orange-50 border-orange-400 text-orange-800' :
                  skill.evidenceStrength === 'MODERATE' ? 'bg-yellow-50 border-yellow-400 text-yellow-800' :
                  'bg-green-50 border-green-400 text-green-800'
                }`}>
                  {getSuggestion(skill.evidenceStrength, skill.isPrimary, skill.linesShowcasing, skill.tasksLinked)}
                </div>
              )}
            </div>
          </td>
        </tr>
      )}
    </React.Fragment>
  );

  // Render collapsible section
  const renderSection = (
    title: string,
    icon: string,
    skills: any[],
    showState: boolean,
    setShowState: (show: boolean) => void,
    colorClass: string
  ) => {
    if (skills.length === 0) return null;

    return (
      <div className={`mb-4 border rounded-lg ${colorClass}`}>
        <button
          onClick={() => setShowState(!showState)}
          className="w-full px-4 py-3 flex items-center justify-between hover:bg-opacity-50 transition-colors"
        >
          <div className="flex items-center gap-2">
            <span className="text-lg">{icon}</span>
            <h3 className="font-semibold text-sm">{title}</h3>
            <span className="text-xs opacity-75">({skills.length} skills)</span>
          </div>
          <span className="text-sm">{showState ? '▼' : '▶'}</span>
        </button>

        {showState && (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse">
              <thead className="bg-gray-50 border-y">
                <tr>
                  <th className="text-left py-2 px-3 text-xs font-semibold text-gray-600">Skill</th>
                  <th className="text-left py-2 px-3 text-xs font-semibold text-gray-600">Category</th>
                  <th className="text-left py-2 px-3 text-xs font-semibold text-gray-600">Evidence</th>
                </tr>
              </thead>
              <tbody>
                {skills.map((skill, idx) => renderSkillRow(skill, idx))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    );
  };

  return (
    <>
      {/* Skill Credibility Report */}
      <div className="bg-white border rounded-lg p-6">
        <h2 className="text-xl font-semibold mb-2 flex items-center gap-2">
          🎯 Skill Credibility Report
        </h2>
        <p className="text-sm text-gray-600 mb-2">
          How recruiters perceive your skill claims. <strong>Fix WEAK/UNSUPPORTED skills first</strong> - they raise red flags.
        </p>
        <div className="text-xs text-gray-500 mb-4 bg-blue-50 p-2 rounded">
          💡 Click on any badge to see resume evidence and get improvement suggestions
        </div>

        {/* Priority Order: NONE → WEAK → MODERATE → STRONG */}
        {renderSection(
          'UNSUPPORTED SKILLS - Remove or Add Evidence',
          '❌',
          sortedUnsupported,
          showUnsupported,
          setShowUnsupported,
          'bg-red-50 border-red-300'
        )}

        {renderSection(
          'WEAK EVIDENCE - Needs Strengthening',
          '⚠️',
          sortedWeak,
          showWeak,
          setShowWeak,
          'bg-orange-50 border-orange-300'
        )}

        {renderSection(
          'MODERATE EVIDENCE - Could Be Stronger',
          '⚠️',
          sortedModerate,
          showModerate,
          setShowModerate,
          'bg-yellow-50 border-yellow-300'
        )}

        {renderSection(
          'PROVEN SKILLS - Strong Evidence',
          '✅',
          sortedStrong,
          showStrong,
          setShowStrong,
          'bg-green-50 border-green-300'
        )}

        {/* Summary */}
        <div className="mt-4 text-xs text-gray-600 bg-gray-50 p-3 rounded">
          <strong>Summary:</strong> {totalHave} total skills •
          {sortedUnsupported.length > 0 && ` ${sortedUnsupported.length} unsupported •`}
          {sortedWeak.length > 0 && ` ${sortedWeak.length} weak •`}
          {sortedModerate.length > 0 && ` ${sortedModerate.length} moderate •`}
          {sortedStrong.length > 0 && ` ${sortedStrong.length} strong`}
        </div>
      </div>

      {/* Missing Skills Section */}
      {totalMissing > 0 && (
        <div className="bg-gradient-to-r from-amber-50 to-orange-50 border border-amber-300 rounded-lg p-6 mt-8">
          <h2 className="text-xl font-semibold mb-2 text-amber-900">
            Consider the following skills that commonly show on the described tasks
          </h2>
          <p className="text-sm text-gray-700 mb-2">
            {showLowImportance
              ? `Showing all ${totalMissing} skills sorted by importance, then task frequency`
              : `Showing top ${defaultVisibleCount} most important skills • ${remainingCount} more available`
            }
          </p>
          <div className="bg-amber-100 border border-amber-400 rounded px-3 py-2 mb-4 text-xs text-amber-900">
            <span className="font-semibold">ℹ️ Graph-Based Suggestion:</span> These skills are common across the O*NET tasks linked to your job. Skills are sorted by importance (how critical they are for the role), then by task frequency (how often they appear).
          </div>

          <div className="overflow-x-auto bg-white rounded-lg">
            <table className="w-full border-collapse">
              <thead className="bg-amber-100 border-b-2 border-amber-300">
                <tr>
                  <th className="text-left py-2 px-3 text-sm font-semibold text-gray-700">Skill</th>
                  <th className="text-left py-2 px-3 text-sm font-semibold text-gray-700">Category</th>
                  <th className="text-left py-2 px-3 text-sm font-semibold text-gray-700">Exists in these tasks</th>
                  <th className="text-left py-2 px-3 text-sm font-semibold text-gray-700">Importance</th>
                </tr>
              </thead>
              <tbody>
                {missingEnrichedSkills.map((skill, idx) => {
                  // Show first N skills, or all if expanded
                  const shouldShow = showLowImportance || idx < defaultVisibleCount;

                  if (!shouldShow) return null;

                  return (
                    <tr
                      key={idx}
                      className="border-b border-amber-100 hover:bg-amber-50"
                    >
                      {/* Skill Name */}
                      <td className="py-2 px-3">
                        <span className="font-medium text-gray-900">{skill.name}</span>
                      </td>

                      {/* Category */}
                      <td className="py-2 px-3">
                        <span className="text-sm text-gray-700">{skill.category || 'Uncategorized'}</span>
                      </td>

                      {/* Exists in these tasks */}
                      <td className="py-2 px-3">
                        <div className="text-xs text-gray-700">
                          <div className="font-semibold text-amber-800 mb-1">
                            {skill.tasksLinked} task{skill.tasksLinked !== 1 ? 's' : ''}
                          </div>
                          {skill.taskNames && skill.taskNames.length > 0 && (
                            <ul className="list-disc list-inside text-gray-600 space-y-0.5">
                              {skill.taskNames.map((task: string, i: number) => (
                                <li key={i} className="truncate max-w-md">
                                  {task.length > 60 ? task.substring(0, 60) + '...' : task}
                                </li>
                              ))}
                            </ul>
                          )}
                        </div>
                      </td>

                      {/* Importance */}
                      <td className="py-2 px-3">
                        <div className="flex items-center gap-2">
                          <div className="w-16 h-2 bg-gray-200 rounded-full overflow-hidden">
                            <div
                              className="h-full bg-amber-600 rounded-full"
                              style={{ width: `${skill.avgImportance}%` }}
                            />
                          </div>
                          <span className="text-sm font-semibold text-amber-700">
                            {(skill.avgImportance || 0).toFixed(0)}%
                          </span>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Show More Button */}
          {!showLowImportance && remainingCount > 0 && (
            <button
              onClick={() => setShowLowImportance(true)}
              className="mt-4 w-full py-2 text-sm text-amber-700 hover:text-amber-900 hover:bg-amber-100 rounded border border-amber-300 transition-colors"
            >
              Show All Skills ({remainingCount} more) ▼
            </button>
          )}
        </div>
      )}
    </>
  );
}

function ValueRatingBadge({ rating }: { rating: string }) {
  const colors: Record<string, string> = {
    EXCELLENT: 'bg-green-100 text-green-800 border-green-300',
    GOOD: 'bg-blue-100 text-blue-800 border-blue-300',
    MODERATE: 'bg-yellow-100 text-yellow-800 border-yellow-300',
    LOW: 'bg-gray-100 text-gray-600 border-gray-300'
  };

  const icons: Record<string, string> = {
    EXCELLENT: '💎',
    GOOD: '✨',
    MODERATE: '○',
    LOW: '—'
  };

  return (
    <span className={`text-xs px-2 py-1 rounded border font-semibold ${colors[rating] || colors.LOW}`}>
      {icons[rating]} {rating} VALUE
    </span>
  );
}

function ImportanceIndicator({ score }: { score: number }) {
  const width = Math.min(100, Math.max(0, score)); // Clamp between 0-100
  const color = score >= 80 ? 'bg-red-500' : score >= 60 ? 'bg-orange-500' : 'bg-gray-400';

  return (
    <div className="w-24">
      <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
        <div
          className={`h-full ${color} transition-all duration-300`}
          style={{ width: `${width}%` }}
        ></div>
      </div>
      <div className="text-xs text-gray-500 text-center mt-1">{score.toFixed(0)}%</div>
    </div>
  );
}
