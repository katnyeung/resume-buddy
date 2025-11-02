'use client';

import { useEffect, useState, useRef } from 'react';
import { getSkillTrain, exploreSkillPath } from '@/lib/api';
import { SkillTrainDto, SkillPathResponse } from '@/lib/types';
import SkillPathMatrix from './SkillPathMatrix';

interface SkillTrainProps {
  resumeId: string;
  userSkills: string[];
  userSkillsWithCategory?: Array<{
    id: string;
    skillName: string;
    category?: string;
  }>;
}

interface TrainStation {
  skill: string;
  jobCount: number;
  isOwned: boolean;
  level: number; // 0 = starting station, 1+ = next stops
}

export default function SkillTrain({ resumeId, userSkills, userSkillsWithCategory }: SkillTrainProps) {
  const [trainData, setTrainData] = useState<SkillTrainDto | null>(null);
  const [selectedPath, setSelectedPath] = useState<string[]>([]);
  const [currentStations, setCurrentStations] = useState<TrainStation[]>([]);
  const [pathData, setPathData] = useState<SkillPathResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isInitialLoading, setIsInitialLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // Load initial skill train data
  useEffect(() => {
    const loadSkillTrain = async () => {
      try {
        setIsInitialLoading(true);
        setError(null);
        const data = await getSkillTrain(resumeId);
        setTrainData(data);

        // Initialize starting stations (user's skills)
        const startingStations: TrainStation[] = data.userSkills.map((skill: string) => ({
          skill,
          jobCount: data.jobCountBySkill[skill] || 0,
          isOwned: true,
          level: 0
        }));

        setCurrentStations(startingStations);
      } catch (err: any) {
        console.error('Failed to load skill train:', err);
        // If 404, user hasn't created job search profile yet - don't show error
        if (err?.response?.status === 404) {
          setError(null);
        } else {
          setError('Failed to load skill train data');
        }
      } finally {
        setIsInitialLoading(false);
      }
    };

    loadSkillTrain();
  }, [resumeId]);

  // Handle selecting a skill (adding to path)
  const handleSelectSkill = async (skill: string, isOwned: boolean) => {
    try {
      setIsLoading(true);
      setError(null);

      const newPath = [...selectedPath, skill];
      setSelectedPath(newPath);

      // Fetch next stations (related skills)
      const pathResponse = await exploreSkillPath(newPath);
      setPathData(pathResponse);

      // Build next level stations from related skills
      // Handle case where relatedSkills might be empty or null
      const relatedSkillsMap = pathResponse.relatedSkills || {};
      const nextStations: TrainStation[] = Object.entries(relatedSkillsMap).map(
        ([skillName, jobCount]) => ({
          skill: skillName,
          jobCount: jobCount as number,
          isOwned: trainData?.userSkills.includes(skillName) || false,
          level: newPath.length
        })
      );

      setCurrentStations(nextStations);
    } catch (err: any) {
      console.error('Failed to explore skill path:', err);
      setError('Failed to explore skill path');
      // Don't reset the path on error - keep showing the current state
    } finally {
      setIsLoading(false);
    }
  };

  // Reset to start
  const handleReset = () => {
    setSelectedPath([]);
    setPathData(null);
    if (trainData) {
      const startingStations: TrainStation[] = trainData.userSkills.map((skill: string) => ({
        skill,
        jobCount: trainData.jobCountBySkill[skill] || 0,
        isOwned: true,
        level: 0
      }));
      setCurrentStations(startingStations);
    }
  };

  // Go back one step
  const handleBacktrack = async () => {
    if (selectedPath.length === 0) return;

    const newPath = selectedPath.slice(0, -1);
    setSelectedPath(newPath);

    if (newPath.length === 0) {
      // Back to start
      handleReset();
    } else {
      // Reload path data for previous level
      try {
        setIsLoading(true);
        const pathResponse = await exploreSkillPath(newPath);
        setPathData(pathResponse);

        const relatedSkillsMap = pathResponse.relatedSkills || {};
        const nextStations: TrainStation[] = Object.entries(relatedSkillsMap).map(
          ([skillName, jobCount]) => ({
            skill: skillName,
            jobCount: jobCount as number,
            isOwned: trainData?.userSkills.includes(skillName) || false,
            level: newPath.length
          })
        );
        setCurrentStations(nextStations);
      } catch (err: any) {
        console.error('Failed to backtrack:', err);
      } finally {
        setIsLoading(false);
      }
    }
  };

  if (isInitialLoading) {
    return (
      <div className="bg-gradient-to-r from-indigo-50 to-blue-50 border border-indigo-200 rounded-lg p-6 mt-4">
        <div className="flex items-center gap-2 mb-3">
          <svg className="h-5 w-5 text-indigo-600 animate-spin" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
          <h4 className="font-semibold text-gray-700">Loading Skill Train...</h4>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-lg p-4 mt-4">
        <p className="text-red-700 text-sm">{error}</p>
      </div>
    );
  }

  if (!trainData) {
    return null;
  }

  return (
    <div ref={containerRef} className="bg-gradient-to-r from-indigo-50 to-blue-50 border border-indigo-200 rounded-lg p-6 mt-4">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <svg className="h-6 w-6 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
          </svg>
          <h4 className="font-semibold text-gray-800">Skills & Career Path Explorer</h4>
          <span className="px-2 py-0.5 bg-indigo-100 text-indigo-700 text-xs font-semibold rounded-full">
            BETA
          </span>
        </div>

        {selectedPath.length > 0 && (
          <div className="flex gap-2">
            <button
              onClick={handleBacktrack}
              className="px-3 py-1 bg-gray-200 text-gray-700 rounded-md text-sm hover:bg-gray-300 transition-colors"
            >
              ← Back
            </button>
            <button
              onClick={handleReset}
              className="px-3 py-1 bg-indigo-100 text-indigo-700 rounded-md text-sm hover:bg-indigo-200 transition-colors"
            >
              🔄 Reset
            </button>
          </div>
        )}
      </div>

      {/* User's Skills Display */}
      {selectedPath.length === 0 && userSkillsWithCategory && userSkillsWithCategory.length > 0 && (
        <>
          <div className="mb-4">
            <h5 className="font-semibold text-gray-700 mb-2 text-sm">Your Skills (sorted by job demand)</h5>
            <div className="flex flex-wrap gap-2">
              {[...userSkillsWithCategory]
                .sort((a, b) => {
                  const countA = trainData?.jobCountBySkill[a.skillName] || 0;
                  const countB = trainData?.jobCountBySkill[b.skillName] || 0;
                  return countB - countA; // Descending order (highest first)
                })
                .map((skill) => (
                  <button
                    key={skill.id}
                    onClick={() => handleSelectSkill(skill.skillName, true)}
                    className="inline-flex items-center bg-green-100 border-2 border-green-500 text-green-800 text-xs px-3 py-1.5 rounded-lg font-medium hover:bg-green-200 transition-all transform hover:scale-105 cursor-pointer"
                    title={`Click to explore jobs with ${skill.skillName}`}
                  >
                    <span className="text-green-600 font-bold mr-1">✓</span>
                    {skill.skillName}
                    {skill.category && (
                      <span className="ml-1.5 px-1.5 py-0.5 bg-green-200 text-green-900 rounded text-[10px]">
                        {skill.category}
                      </span>
                    )}
                    {trainData?.jobCountBySkill[skill.skillName] !== undefined && (
                      <span className="ml-1.5 px-1.5 py-0.5 bg-indigo-100 text-indigo-700 rounded text-[10px] font-bold">
                        {trainData.jobCountBySkill[skill.skillName]}
                      </span>
                    )}
                  </button>
                ))}
            </div>
          </div>

          {/* Skill Path Matrix - Shows valuable skill combinations */}
          <SkillPathMatrix
            userSkills={trainData.userSkills}
            onCellClick={async (userSkill, marketSkill, count) => {
              // Save current scroll position
              const scrollY = window.scrollY;

              // Start path with user skill first
              try {
                setIsLoading(true);
                setError(null);

                // Start with the user skill (the one they already have)
                const firstPath = [userSkill];
                setSelectedPath(firstPath);

                // Fetch related skills for this user skill
                const firstPathResponse = await exploreSkillPath(firstPath);
                setPathData(firstPathResponse);

                // Check if market skill is in the related skills
                const relatedSkillsMap = firstPathResponse.relatedSkills || {};
                if (relatedSkillsMap[marketSkill] !== undefined) {
                  // Market skill is available, add it to the path
                  const secondPath = [userSkill, marketSkill];
                  setSelectedPath(secondPath);

                  // Fetch next level
                  const secondPathResponse = await exploreSkillPath(secondPath);
                  setPathData(secondPathResponse);

                  const nextStations = Object.entries(secondPathResponse.relatedSkills || {}).map(
                    ([skillName, jobCount]) => ({
                      skill: skillName,
                      jobCount: jobCount as number,
                      isOwned: trainData?.userSkills.includes(skillName) || false,
                      level: secondPath.length
                    })
                  );
                  setCurrentStations(nextStations);
                } else {
                  // Market skill not directly related, just show first level
                  const nextStations = Object.entries(relatedSkillsMap).map(
                    ([skillName, jobCount]) => ({
                      skill: skillName,
                      jobCount: jobCount as number,
                      isOwned: trainData?.userSkills.includes(skillName) || false,
                      level: firstPath.length
                    })
                  );
                  setCurrentStations(nextStations);
                }

                // Restore scroll position after state updates
                requestAnimationFrame(() => {
                  window.scrollTo(0, scrollY);
                });
              } catch (err: any) {
                console.error('Failed to start skill path from matrix:', err);
                setError('Failed to explore skill path');
              } finally {
                setIsLoading(false);
              }
            }}
          />
        </>
      )}

      {/* Description */}
      <p className="text-sm text-gray-600 mb-4">
        {selectedPath.length === 0
          ? '💡 Click skill badges above or matrix cells below to start exploring career paths. The matrix shows which skill combinations are most valuable in the job market.'
          : 'Build your skill path by selecting related skills below. Each combination shows matching job counts.'}
      </p>

      {/* Train Track - Horizontal Path */}
      {selectedPath.length > 0 && (
        <div className="mb-6 pb-4 border-b border-indigo-200">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-xs font-medium text-gray-600">Current Path:</span>
            {selectedPath.map((skill, index) => (
              <div key={index} className="flex items-center gap-2">
                <div className="px-3 py-1.5 bg-indigo-600 text-white rounded-md text-sm font-medium shadow-sm">
                  {skill}
                </div>
                {index < selectedPath.length - 1 && (
                  <span className="text-indigo-400 font-bold">→</span>
                )}
              </div>
            ))}
            {pathData && (
              <div className={`ml-2 px-2 py-1 rounded-full text-xs font-bold ${
                pathData.jobCount === 0
                  ? 'bg-red-100 text-red-800'
                  : 'bg-green-100 text-green-800'
              }`}>
                {pathData.jobCount} {pathData.jobCount === 1 ? 'job' : 'jobs'}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Station Selection - Only show when path is active */}
      {selectedPath.length > 0 && (
        <div>
          <h5 className="text-sm font-semibold text-gray-700 mb-3">
            Next Stations (Related Skills)
          </h5>

          {isLoading ? (
          <div className="flex items-center gap-2 text-gray-600">
            <svg className="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            <span className="text-sm">Finding related skills...</span>
          </div>
        ) : currentStations.length === 0 ? (
          <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
            <p className="text-sm text-yellow-800">
              {pathData && pathData.jobCount === 0 ? (
                <>
                  ⚠️ No jobs found requiring this skill combination. Try a different path or use the Back button to explore other options.
                </>
              ) : (
                <>
                  🎯 End of the line! No more related skills found for this path. This is the most specific skill set you can build from here.
                </>
              )}
            </p>
          </div>
        ) : (
          <div className="flex flex-wrap gap-2">
            {currentStations.slice(0, 15).map((station, index) => (
              <button
                key={index}
                onClick={() => handleSelectSkill(station.skill, station.isOwned)}
                className={`
                  px-3 py-2 rounded-lg text-sm font-medium transition-all transform hover:scale-105 shadow-sm
                  ${
                    station.isOwned
                      ? 'bg-green-100 border-2 border-green-500 text-green-800 hover:bg-green-200'
                      : 'bg-white border-2 border-dashed border-gray-300 text-gray-700 hover:bg-gray-50'
                  }
                `}
                title={station.isOwned ? 'You have this skill' : 'Learn this skill'}
              >
                <div className="flex items-center gap-2">
                  {station.isOwned && (
                    <span className="text-green-600 font-bold">✓</span>
                  )}
                  <span>{station.skill}</span>
                  <span className="px-1.5 py-0.5 bg-indigo-100 text-indigo-700 rounded text-xs font-bold">
                    {station.jobCount}
                  </span>
                </div>
              </button>
            ))}
          </div>
        )}
        </div>
      )}

      {/* Legend */}
      <div className="mt-4 pt-4 border-t border-indigo-200">
        <div className="flex flex-wrap gap-4 text-xs text-gray-600">
          <div className="flex items-center gap-1.5">
            <div className="w-4 h-4 bg-green-100 border-2 border-green-500 rounded"></div>
            <span>Skills you have</span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="w-4 h-4 bg-white border-2 border-dashed border-gray-300 rounded"></div>
            <span>Skills to learn</span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="px-1.5 py-0.5 bg-indigo-100 text-indigo-700 rounded font-bold">123</div>
            <span>Number of jobs requiring this skill</span>
          </div>
        </div>
      </div>
    </div>
  );
}
