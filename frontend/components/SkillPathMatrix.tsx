'use client';

import { useEffect, useState } from 'react';
import { getSkillPathMatrix } from '@/lib/api';
import { SkillPathMatrixResponse } from '@/lib/types';

interface SkillPathMatrixProps {
  userSkills: string[];
  onCellClick?: (userSkill: string, marketSkill: string, count: number) => void;
}

export default function SkillPathMatrix({ userSkills, onCellClick }: SkillPathMatrixProps) {
  const [matrixData, setMatrixData] = useState<SkillPathMatrixResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [hoveredCell, setHoveredCell] = useState<{ userSkill: string; marketSkill: string; count: number } | null>(null);

  useEffect(() => {
    if (userSkills.length === 0) return;

    const fetchMatrix = async () => {
      try {
        setLoading(true);
        setError(null);
        const data = await getSkillPathMatrix(userSkills, 15);
        setMatrixData(data);
      } catch (err) {
        console.error('Failed to fetch skill path matrix:', err);
        setError('Failed to load skill path matrix');
      } finally {
        setLoading(false);
      }
    };

    fetchMatrix();
  }, [userSkills]);

  const getColorIntensity = (count: number, maxCount: number): string => {
    if (count === 0 || maxCount === 0) {
      return 'rgba(229, 231, 235, 0.3)'; // gray-200 for empty
    }
    const intensity = count / maxCount;
    // Use green gradient for positive skill combinations
    return `rgba(34, 197, 94, ${intensity})`; // green-500
  };

  if (loading) {
    return (
      <div className="bg-white rounded-lg p-4 border border-indigo-200">
        <div className="flex items-center gap-2 text-gray-600">
          <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-indigo-600"></div>
          <span className="text-sm">Loading skill path matrix...</span>
        </div>
      </div>
    );
  }

  if (error || !matrixData || matrixData.marketSkills.length === 0) {
    return null; // Gracefully hide if no data
  }

  const { userSkills: matrixUserSkills, marketSkills, cooccurrenceMatrix, maxCooccurrence } = matrixData;

  // Sort market skills (rows) by total co-occurrence with user skills (descending)
  const sortedMarketSkills = [...marketSkills].sort((a, b) => {
    const totalA = matrixUserSkills.reduce((sum, userSkill) => sum + (cooccurrenceMatrix[userSkill]?.[a] || 0), 0);
    const totalB = matrixUserSkills.reduce((sum, userSkill) => sum + (cooccurrenceMatrix[userSkill]?.[b] || 0), 0);
    return totalB - totalA; // Descending order
  });

  // Sort user skills (columns) by total co-occurrence with market skills (descending)
  const sortedUserSkills = [...matrixUserSkills].sort((a, b) => {
    const totalA = sortedMarketSkills.reduce((sum, marketSkill) => sum + (cooccurrenceMatrix[a]?.[marketSkill] || 0), 0);
    const totalB = sortedMarketSkills.reduce((sum, marketSkill) => sum + (cooccurrenceMatrix[b]?.[marketSkill] || 0), 0);
    return totalB - totalA; // Descending order (highest on left)
  });

  return (
    <div className="bg-white rounded-lg p-4 border border-indigo-200 mt-4">
      {/* Header */}
      <div className="mb-3">
        <h5 className="text-sm font-semibold text-gray-800 flex items-center gap-2">
          <svg className="w-4 h-4 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 5a1 1 0 011-1h14a1 1 0 011 1v2a1 1 0 01-1 1H5a1 1 0 01-1-1V5zM4 13a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H5a1 1 0 01-1-1v-6zM16 13a1 1 0 011-1h2a1 1 0 011 1v6a1 1 0 01-1 1h-2a1 1 0 01-1-1v-6z" />
          </svg>
          Career Path Matrix
        </h5>
        <p className="text-xs text-gray-600 mt-1">
          Your skills (rows, sorted) × Skills to learn (columns, sorted by demand). Higher numbers = more jobs requiring both.
        </p>
      </div>

      {/* Matrix Grid - Your skills (rows) × Market skills to learn (columns, sorted by demand) */}
      <div className="overflow-auto max-h-[400px]">
        <table className="border-collapse text-xs">
          <thead>
            <tr>
              <th className="sticky top-0 left-0 z-20 bg-white border border-gray-200 p-1"></th>
              {sortedMarketSkills.map((skill) => (
                <th
                  key={skill}
                  className="sticky top-0 z-10 bg-indigo-50 border border-gray-200 p-1 min-w-[50px] max-w-[60px]"
                  style={{ writingMode: 'vertical-rl', textOrientation: 'mixed' }}
                  title={skill}
                >
                  <div className="truncate font-medium text-gray-700">{skill}</div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {sortedUserSkills.map((userSkill) => (
              <tr key={userSkill}>
                <td
                  className="sticky left-0 z-10 bg-green-50 border border-gray-200 p-2 text-xs font-medium text-gray-700"
                  style={{
                    minWidth: '100px',
                    maxWidth: '120px',
                    whiteSpace: 'normal',
                    wordWrap: 'break-word',
                    lineHeight: '1.2'
                  }}
                  title={userSkill}
                >
                  <div>✓ {userSkill}</div>
                </td>
                {sortedMarketSkills.map((marketSkill) => {
                  const count = cooccurrenceMatrix[userSkill]?.[marketSkill] || 0;
                  const bgColor = getColorIntensity(count, maxCooccurrence);

                  return (
                    <td
                      key={`${userSkill}-${marketSkill}`}
                      className={`border border-gray-200 text-center font-medium transition-all ${
                        count > 0 ? 'cursor-pointer hover:ring-2 hover:ring-indigo-400' : ''
                      }`}
                      style={{
                        backgroundColor: bgColor,
                        minWidth: '50px',
                        minHeight: '40px',
                        padding: '2px',
                        color: count > maxCooccurrence * 0.6 ? 'white' : 'black'
                      }}
                      onClick={() => count > 0 && onCellClick && onCellClick(userSkill, marketSkill, count)}
                      onMouseEnter={() => setHoveredCell({ userSkill, marketSkill, count })}
                      onMouseLeave={() => setHoveredCell(null)}
                      title={`${userSkill} + ${marketSkill}: ${count} jobs`}
                    >
                      {count > 0 ? count : ''}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Hover tooltip */}
      {hoveredCell && (
        <div className={`mt-2 rounded-lg p-2 border ${
          hoveredCell.count === 0
            ? 'bg-gray-50 border-gray-200'
            : 'bg-green-50 border-green-200'
        }`}>
          <div className="text-xs">
            <span className="font-semibold text-green-900">✓ {hoveredCell.userSkill}</span>
            <span className={`mx-1 ${hoveredCell.count === 0 ? 'text-gray-400' : 'text-green-600'}`}>+</span>
            <span className="font-semibold text-gray-900">{hoveredCell.marketSkill}</span>
            <span className="text-gray-600 ml-2">
              → <span className={`font-bold ${hoveredCell.count === 0 ? 'text-gray-500' : 'text-green-700'}`}>
                {hoveredCell.count}
              </span> {hoveredCell.count === 1 ? 'job' : 'jobs'}
              {hoveredCell.count === 0 && <span className="text-gray-500 ml-1">(No matches found)</span>}
            </span>
          </div>
        </div>
      )}

      {/* Legend */}
      <div className="mt-3 pt-2 border-t border-indigo-200">
        <div className="flex items-center gap-3 text-[10px] text-gray-600 flex-wrap">
          <div className="flex items-center gap-1">
            <div className="w-4 h-4 rounded border border-gray-300" style={{ backgroundColor: 'rgba(229, 231, 235, 0.3)' }}></div>
            <span>No overlap</span>
          </div>
          <div className="flex items-center gap-1">
            <div className="w-4 h-4 rounded border border-gray-300" style={{ backgroundColor: 'rgba(34, 197, 94, 0.4)' }}></div>
            <span>Low demand</span>
          </div>
          <div className="flex items-center gap-1">
            <div className="w-4 h-4 rounded border border-gray-300" style={{ backgroundColor: 'rgba(34, 197, 94, 1)' }}></div>
            <span>High ({maxCooccurrence} jobs)</span>
          </div>
          <span className="ml-auto">💡 Both rows & columns sorted by demand (highest first)</span>
        </div>
      </div>
    </div>
  );
}
