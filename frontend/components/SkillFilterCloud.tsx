'use client';

import { useEffect, useState } from 'react';
import { getTopSkillsByCategory } from '@/lib/api';
import { SkillCooccurrence } from '@/lib/types';

interface SkillFilterCloudProps {
  onSkillClick: (skillName: string) => void;
}

export default function SkillFilterCloud({ onSkillClick }: SkillFilterCloudProps) {
  const [categorizedSkills, setCategorizedSkills] = useState<Record<string, SkillCooccurrence[]>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchTopSkills();
  }, []);

  const fetchTopSkills = async () => {
    try {
      setLoading(true);
      const data = await getTopSkillsByCategory(10); // 10 skills per category
      setCategorizedSkills(data);
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

  if (Object.keys(categorizedSkills).length === 0) {
    return null;
  }

  return (
    <div className="bg-gradient-to-r from-indigo-50 to-purple-50 border border-indigo-200 rounded-lg p-3">
      {/* Render skills grouped by category */}
      <div className="space-y-3">
        {Object.entries(categorizedSkills).map(([category, skills]) => (
          <div key={category} className="border-b border-indigo-100 last:border-0 pb-2 last:pb-0">
            <h4 className="text-xs font-semibold text-indigo-900 mb-1.5 flex items-center gap-2">
              <span className="w-1.5 h-1.5 bg-indigo-600 rounded-full"></span>
              {category}
              <span className="text-xs text-gray-500 font-normal">({skills.length})</span>
            </h4>
            <div className="flex flex-wrap gap-1.5">
              {skills.map((skill) => (
                <button
                  key={skill.skillName}
                  onClick={() => onSkillClick(skill.skillName)}
                  className="px-3 py-1.5 bg-white border border-indigo-300 rounded-full text-xs font-medium text-gray-700 hover:bg-indigo-100 hover:border-indigo-400 hover:text-indigo-900 transition-all duration-200 shadow-sm hover:shadow-md"
                >
                  {skill.skillName}
                  <span className="ml-1.5 px-1.5 py-0.5 bg-indigo-100 text-indigo-700 rounded-full text-xs font-semibold">
                    {skill.jobCount}
                  </span>
                </button>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
