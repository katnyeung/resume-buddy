'use client';

import { ResumeLine, AnalysisGroup } from '@/lib/types';
import { useMemo, useState } from 'react';

interface AnalysisOverlayProps {
  lines: ResumeLine[];
  onAnalyzeGroup?: (groupId: number, groupType: string) => void;
  onFindJobs?: (groupId: number) => void;
}

// Color scheme for different section types
const SECTION_COLORS: Record<string, { bg: string; text: string; border: string }> = {
  CONTACT: { bg: 'bg-blue-50', text: 'text-blue-700', border: 'border-blue-200' },
  SUMMARY: { bg: 'bg-purple-50', text: 'text-purple-700', border: 'border-purple-200' },
  EXPERIENCE: { bg: 'bg-green-50', text: 'text-green-700', border: 'border-green-200' },
  EDUCATION: { bg: 'bg-yellow-50', text: 'text-yellow-700', border: 'border-yellow-200' },
  SKILLS: { bg: 'bg-pink-50', text: 'text-pink-700', border: 'border-pink-200' },
  PROJECTS: { bg: 'bg-indigo-50', text: 'text-indigo-700', border: 'border-indigo-200' },
  CERTIFICATIONS: { bg: 'bg-teal-50', text: 'text-teal-700', border: 'border-teal-200' },
  AWARDS: { bg: 'bg-orange-50', text: 'text-orange-700', border: 'border-orange-200' },
  PUBLICATIONS: { bg: 'bg-cyan-50', text: 'text-cyan-700', border: 'border-cyan-200' },
  LANGUAGES: { bg: 'bg-lime-50', text: 'text-lime-700', border: 'border-lime-200' },
  VOLUNTEER: { bg: 'bg-emerald-50', text: 'text-emerald-700', border: 'border-emerald-200' },
  INTERESTS: { bg: 'bg-rose-50', text: 'text-rose-700', border: 'border-rose-200' },
  OTHER: { bg: 'bg-gray-50', text: 'text-gray-700', border: 'border-gray-200' },
};

export default function AnalysisOverlay({ lines, onAnalyzeGroup, onFindJobs }: AnalysisOverlayProps) {
  const [expandedGroups, setExpandedGroups] = useState<Set<number>>(new Set());

  // Helper function to extract group title from content
  const extractGroupTitle = (lines: ResumeLine[], groupType: string): string | null => {
    if (groupType === 'JOB' || groupType === 'EXPERIENCE_JOB') {
      // Try to find company name or position from first few non-empty lines
      const contentLines = lines
        .filter(l => l.content && l.content.trim().length > 0)
        .slice(0, 3); // Check first 3 lines

      for (const line of contentLines) {
        const content = line.content.trim();
        // Skip lines that look like headers or sections
        if (content.toLowerCase().includes('experience') ||
            content.toLowerCase().includes('work history')) {
          continue;
        }
        // Return first meaningful line (likely company or position)
        if (content.length > 0 && content.length < 100) {
          return content;
        }
      }
    }
    return null;
  };

  // Group lines by groupId and organize by sections
  const analysisGroups = useMemo(() => {
    const groups: AnalysisGroup[] = [];
    const groupMap = new Map<number, ResumeLine[]>();

    // Filter analyzed lines - only include lines with valid groupId (not null/undefined)
    // Note: groupType can be null for sections like SUMMARY
    const analyzedLines = lines.filter(line =>
      line.sectionType &&
      line.groupId !== undefined &&
      line.groupId !== null
    );

    analyzedLines.forEach(line => {
      if (line.groupId !== undefined && line.groupId !== null) {
        if (!groupMap.has(line.groupId)) {
          groupMap.set(line.groupId, []);
        }
        groupMap.get(line.groupId)!.push(line);
      }
    });

    // Convert map to array of AnalysisGroup objects
    groupMap.forEach((groupLines, groupId) => {
      groupLines.sort((a, b) => a.lineNumber - b.lineNumber);
      const firstLine = groupLines[0];

      groups.push({
        groupId,
        groupType: firstLine.groupType || 'SECTION', // Use 'SECTION' for non-grouped sections like SUMMARY
        sectionType: firstLine.sectionType || 'OTHER',
        lines: groupLines,
        startLine: groupLines[0].lineNumber,
        endLine: groupLines[groupLines.length - 1].lineNumber,
      });
    });

    // Sort groups by starting line number
    groups.sort((a, b) => a.startLine - b.startLine);

    return groups;
  }, [lines]);

  const toggleGroup = (groupId: number) => {
    const newExpanded = new Set(expandedGroups);
    if (newExpanded.has(groupId)) {
      newExpanded.delete(groupId);
    } else {
      newExpanded.add(groupId);
    }
    setExpandedGroups(newExpanded);
  };

  const getColorScheme = (sectionType: string) => {
    return SECTION_COLORS[sectionType] || SECTION_COLORS.OTHER;
  };

  if (analysisGroups.length === 0) {
    return (
      <div className="text-sm text-gray-500 italic p-4 bg-gray-50 rounded-lg border border-gray-200">
        No analysis data available. Click "Analyze" to get AI-powered insights.
      </div>
    );
  }

  return (
    <div className="space-y-3 mb-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-gray-800">AI Analysis Results</h3>
        <span className="text-sm text-gray-600">
          {analysisGroups.length} group{analysisGroups.length !== 1 ? 's' : ''} detected
        </span>
      </div>

      {/* Track job numbers by section type */}
      {(() => {
        const sectionCounters = new Map<string, number>();

        return analysisGroups.map((group) => {
          const colors = getColorScheme(group.sectionType);
          const isExpanded = expandedGroups.has(group.groupId);
          const hasAnalysisNotes = group.lines.some(line => line.analysisNotes);
          const groupTitle = extractGroupTitle(group.lines, group.groupType);

          // Track numbering for groups of same section type
          let groupNumber: number | null = null;
          if (group.groupType === 'JOB' || group.groupType === 'EXPERIENCE_JOB') {
            const key = `${group.sectionType}_JOB`;
            const currentCount = (sectionCounters.get(key) || 0) + 1;
            sectionCounters.set(key, currentCount);
            groupNumber = currentCount;
          }

          return (
            <div
              key={group.groupId}
              className={`border-2 ${colors.border} ${colors.bg} rounded-lg overflow-hidden transition-all`}
            >
              {/* Group Header */}
              <div
                className="p-3 cursor-pointer hover:opacity-80 transition-opacity"
                onClick={() => toggleGroup(group.groupId)}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3 flex-wrap">
                    {/* Section Badge */}
                    <span className={`px-3 py-1 rounded-full text-xs font-semibold ${colors.text} bg-white border ${colors.border}`}>
                      {group.sectionType}
                    </span>

                    {/* Group Type Badge with Number */}
                    <span className={`px-2 py-1 rounded text-xs font-medium ${colors.text}`}>
                      {group.groupType}
                      {groupNumber !== null && ` ${groupNumber}`}
                    </span>

                    {/* Group Title (extracted from content) */}
                    {groupTitle && (
                      <span className="text-sm font-semibold text-gray-800 max-w-md truncate">
                        {groupTitle}
                      </span>
                    )}

                    {/* Line Range */}
                    <span className="text-xs text-gray-500">
                      Lines {group.startLine}–{group.endLine}
                    </span>
                  </div>

                {/* Expand/Collapse Icon */}
                <svg
                  className={`w-5 h-5 ${colors.text} transform transition-transform ${isExpanded ? 'rotate-180' : ''}`}
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </div>
            </div>

            {/* Expanded Content */}
            {isExpanded && (
              <div className="border-t-2 border-gray-200 bg-white p-4 space-y-3">
                {/* Content Preview */}
                <div className="space-y-2">
                  <h4 className="text-sm font-semibold text-gray-700">Content:</h4>
                  <div className="text-sm text-gray-600 space-y-1">
                    {group.lines.map((line) => (
                      <div key={line.id} className="pl-2 border-l-2 border-gray-200">
                        <span className="text-xs text-gray-400 mr-2">L{line.lineNumber}:</span>
                        {line.content || <span className="italic text-gray-400">(empty line)</span>}
                      </div>
                    ))}
                  </div>
                </div>

                {/* Analysis Notes */}
                {hasAnalysisNotes && (
                  <div className="space-y-2">
                    <h4 className="text-sm font-semibold text-gray-700">AI Insights:</h4>
                    <div className="text-sm text-gray-600 space-y-1">
                      {group.lines.map((line) =>
                        line.analysisNotes ? (
                          <div key={line.id} className="pl-2 border-l-2 border-blue-200 bg-blue-50 p-2 rounded">
                            <span className="text-xs text-gray-400 mr-2">L{line.lineNumber}:</span>
                            {line.analysisNotes}
                          </div>
                        ) : null
                      )}
                    </div>
                  </div>
                )}

                {/* Action Links */}
                <div className="flex gap-2 pt-2 border-t border-gray-200">
                  {/* Analyze Group Button (for future job analysis) */}
                  {(group.groupType === 'JOB' || group.groupType === 'PROJECT') && (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onAnalyzeGroup?.(group.groupId, group.groupType);
                      }}
                      className="px-3 py-1 bg-purple-100 text-purple-700 rounded-md text-xs font-medium hover:bg-purple-200 transition-colors flex items-center gap-1"
                      title="Analyze this experience with AI (Coming soon)"
                    >
                      <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                      </svg>
                      Analyze {group.groupType === 'JOB' ? 'Job' : 'Project'}
                    </button>
                  )}

                  {/* Find Similar Jobs Button (for experience groups) */}
                  {group.sectionType === 'EXPERIENCE' && (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onFindJobs?.(group.groupId);
                      }}
                      className="px-3 py-1 bg-green-100 text-green-700 rounded-md text-xs font-medium hover:bg-green-200 transition-colors flex items-center gap-1"
                      title="Find similar job opportunities (Coming soon)"
                    >
                      <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                      </svg>
                      Find Similar Jobs
                    </button>
                  )}
                </div>
              </div>
            )}
            </div>
          );
        });
      })()}
    </div>
  );
}
