'use client';

import { useEffect, useState } from 'react';
import { getTopSkills } from '@/lib/api';
import { SkillCooccurrence } from '@/lib/types';

interface SkillFilterCloudProps {
  onSkillClick: (skillName: string) => void;
}

export default function SkillFilterCloud({ onSkillClick }: SkillFilterCloudProps) {
  const [skills, setSkills] = useState<SkillCooccurrence[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchTopSkills();
  }, []);

  const fetchTopSkills = async () => {
    try {
      setLoading(true);
      const data = await getTopSkills(30);
      setSkills(data);
    } catch (err) {
      console.error('Failed to fetch top skills:', err);
      setError('Failed to load skills');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="bg-white border border-gray-200 rounded-lg p-6 mb-6">
        <div className="flex items-center justify-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
          <span className="ml-3 text-gray-600">Loading skills...</span>
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

  if (skills.length === 0) {
    return null;
  }

  return (
    <div className="bg-gradient-to-r from-indigo-50 to-purple-50 border border-indigo-200 rounded-lg p-6 mb-6">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h3 className="text-lg font-semibold text-gray-900 flex items-center gap-2">
            <svg className="w-5 h-5 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z" />
            </svg>
            Discover Jobs by Skills
          </h3>
          <p className="text-sm text-gray-600 mt-1">
            Click any skill to see related jobs from Neo4j graph
          </p>
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        {skills.map((skill) => (
          <button
            key={skill.skillName}
            onClick={() => onSkillClick(skill.skillName)}
            className="px-4 py-2 bg-white border border-indigo-300 rounded-full text-sm font-medium text-gray-700 hover:bg-indigo-100 hover:border-indigo-400 hover:text-indigo-900 transition-all duration-200 shadow-sm hover:shadow-md"
          >
            {skill.skillName}
            <span className="ml-2 px-2 py-0.5 bg-indigo-100 text-indigo-700 rounded-full text-xs font-semibold">
              {skill.jobCount}
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}
