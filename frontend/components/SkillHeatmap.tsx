'use client';

import { useEffect, useState } from 'react';
import { getSkillHeatmap } from '@/lib/api';
import { SkillHeatmapResponse } from '@/lib/types';

interface SkillHeatmapProps {
  onCellClick?: (skill1: string, skill2: string, count: number) => void;
}

export default function SkillHeatmap({ onCellClick }: SkillHeatmapProps) {
  const [heatmapData, setHeatmapData] = useState<SkillHeatmapResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [hoveredCell, setHoveredCell] = useState<{ skill1: string; skill2: string; count: number } | null>(null);
  const [matrixSize, setMatrixSize] = useState(20);

  useEffect(() => {
    fetchHeatmap(matrixSize);
  }, [matrixSize]);

  const fetchHeatmap = async (topN: number) => {
    try {
      setLoading(true);
      setError(null);
      const data = await getSkillHeatmap(topN);
      setHeatmapData(data);
    } catch (err) {
      console.error('Failed to fetch skill heatmap:', err);
      setError('Failed to load skill heatmap');
    } finally {
      setLoading(false);
    }
  };

  const getColorIntensity = (count: number, maxCount: number): string => {
    if (count === 0 || maxCount === 0) {
      return 'rgba(229, 231, 235, 0.5)'; // gray-200 with opacity for empty cells
    }
    const intensity = count / maxCount;
    // Use indigo color gradient
    return `rgba(99, 102, 241, ${intensity})`;
  };

  const handleCellClick = (skill1: string, skill2: string) => {
    if (!heatmapData) return;
    const count = heatmapData.cooccurrenceMatrix[skill1]?.[skill2] || 0;
    if (onCellClick && count > 0) {
      onCellClick(skill1, skill2, count);
    }
  };

  if (loading) {
    return (
      <div className="bg-white border border-gray-200 rounded-lg p-6 mb-6">
        <div className="flex items-center justify-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
          <span className="ml-3 text-gray-600">Loading skill co-occurrence heatmap...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-white border border-gray-200 rounded-lg p-6 mb-6">
        <div className="text-red-600 text-center">{error}</div>
      </div>
    );
  }

  if (!heatmapData || heatmapData.skillNames.length === 0) {
    return null;
  }

  const { skillNames, cooccurrenceMatrix, maxCooccurrence, totalJobs } = heatmapData;

  return (
    <div className="bg-gradient-to-r from-purple-50 to-pink-50 border border-purple-200 rounded-lg p-6 mb-6">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div>
          <h3 className="text-lg font-semibold text-gray-900 flex items-center gap-2">
            <svg className="w-5 h-5 text-purple-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 5a1 1 0 011-1h14a1 1 0 011 1v2a1 1 0 01-1 1H5a1 1 0 01-1-1V5zM4 13a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H5a1 1 0 01-1-1v-6zM16 13a1 1 0 011-1h2a1 1 0 011 1v6a1 1 0 01-1 1h-2a1 1 0 01-1-1v-6z" />
            </svg>
            Skill Co-occurrence Matrix
          </h3>
          <p className="text-sm text-gray-600 mt-1">
            Shows how often skill pairs appear together in {totalJobs.toLocaleString()} job listings
          </p>
        </div>

        {/* Size selector */}
        <div className="flex items-center gap-2">
          <label className="text-xs text-gray-600 font-medium">Matrix size:</label>
          <select
            value={matrixSize}
            onChange={(e) => setMatrixSize(Number(e.target.value))}
            className="px-2 py-1 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
          >
            <option value={10}>10×10</option>
            <option value={15}>15×15</option>
            <option value={20}>20×20</option>
            <option value={25}>25×25</option>
          </select>
        </div>
      </div>

      {/* Heatmap Grid */}
      <div className="bg-white rounded-lg p-4 shadow-inner overflow-auto max-h-[600px]">
        <div className="inline-block min-w-full">
          <table className="border-collapse">
            <thead>
              <tr>
                <th className="sticky top-0 left-0 z-20 bg-white border border-gray-200 p-1"></th>
                {skillNames.map((skill) => (
                  <th
                    key={skill}
                    className="sticky top-0 z-10 bg-gray-50 border border-gray-200 p-2 text-xs font-medium text-gray-700 min-w-[60px] max-w-[80px]"
                    style={{ writingMode: 'vertical-rl', textOrientation: 'mixed' }}
                    title={skill}
                  >
                    <div className="truncate">{skill}</div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {skillNames.map((skill1) => (
                <tr key={skill1}>
                  <td
                    className="sticky left-0 z-10 bg-gray-50 border border-gray-200 p-2 text-xs font-medium text-gray-700 max-w-[100px]"
                    title={skill1}
                  >
                    <div className="truncate">{skill1}</div>
                  </td>
                  {skillNames.map((skill2) => {
                    const count = cooccurrenceMatrix[skill1]?.[skill2] || 0;
                    const bgColor = getColorIntensity(count, maxCooccurrence);
                    const isDiagonal = skill1 === skill2;

                    return (
                      <td
                        key={`${skill1}-${skill2}`}
                        className={`border border-gray-200 text-center text-xs font-medium transition-all ${
                          count > 0 && !isDiagonal ? 'cursor-pointer hover:ring-2 hover:ring-purple-400' : ''
                        }`}
                        style={{
                          backgroundColor: bgColor,
                          minWidth: '60px',
                          minHeight: '60px',
                          color: count > maxCooccurrence * 0.5 ? 'white' : 'black'
                        }}
                        onClick={() => !isDiagonal && handleCellClick(skill1, skill2)}
                        onMouseEnter={() => setHoveredCell({ skill1, skill2, count })}
                        onMouseLeave={() => setHoveredCell(null)}
                        title={isDiagonal ? `${skill1} (diagonal)` : `${skill1} + ${skill2}: ${count} jobs`}
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
      </div>

      {/* Hover tooltip */}
      {hoveredCell && hoveredCell.count > 0 && hoveredCell.skill1 !== hoveredCell.skill2 && (
        <div className="mt-3 bg-indigo-50 border border-indigo-200 rounded-lg p-3">
          <div className="text-sm">
            <span className="font-semibold text-indigo-900">{hoveredCell.skill1}</span>
            <span className="text-indigo-600 mx-2">+</span>
            <span className="font-semibold text-indigo-900">{hoveredCell.skill2}</span>
            <span className="text-gray-600 ml-2">
              → <span className="font-bold text-indigo-700">{hoveredCell.count}</span> jobs
            </span>
            <span className="text-gray-500 ml-2">
              ({((hoveredCell.count / totalJobs) * 100).toFixed(1)}% of all jobs)
            </span>
          </div>
        </div>
      )}

      {/* Legend */}
      <div className="mt-4 pt-4 border-t border-purple-200">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4 text-xs text-gray-600">
            <div className="flex items-center gap-2">
              <div className="w-6 h-6 rounded border border-gray-300" style={{ backgroundColor: 'rgba(229, 231, 235, 0.5)' }}></div>
              <span>0 jobs</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-6 h-6 rounded border border-gray-300" style={{ backgroundColor: 'rgba(99, 102, 241, 0.3)' }}></div>
              <span>Low co-occurrence</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-6 h-6 rounded border border-gray-300" style={{ backgroundColor: 'rgba(99, 102, 241, 0.7)' }}></div>
              <span>Medium</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-6 h-6 rounded border border-gray-300" style={{ backgroundColor: 'rgba(99, 102, 241, 1)' }}></div>
              <span>High ({maxCooccurrence} jobs)</span>
            </div>
          </div>
          <div className="text-xs text-gray-600">
            💡 Click any cell to explore jobs requiring both skills
          </div>
        </div>
      </div>
    </div>
  );
}
