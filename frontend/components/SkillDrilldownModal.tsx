'use client';

import { useEffect, useState } from 'react';
import { skillDrilldown, getJobsByIds } from '@/lib/api';
import { SkillDrilldownResponse, JobListing } from '@/lib/types';
import DateDistributionChart from './DateDistributionChart';

interface SkillDrilldownModalProps {
  isOpen: boolean;
  initialSkill?: string;
  onClose: () => void;
  onViewJobs: (jobs: JobListing[], selectedSkills: string[]) => void;
}

export default function SkillDrilldownModal({
  isOpen,
  initialSkill,
  onClose,
  onViewJobs
}: SkillDrilldownModalProps) {
  const [selectedSkills, setSelectedSkills] = useState<string[]>([]);
  const [drilldownData, setDrilldownData] = useState<SkillDrilldownResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen && initialSkill) {
      setSelectedSkills([initialSkill]);
      fetchDrilldownData([initialSkill]);
    }
  }, [isOpen, initialSkill]);

  const fetchDrilldownData = async (skills: string[]) => {
    if (skills.length === 0) return;

    try {
      setLoading(true);
      setError(null);
      const data = await skillDrilldown(skills, 30);
      setDrilldownData(data);
    } catch (err: any) {
      console.error('Skill drilldown failed:', err);
      setError('Failed to fetch job data. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleAddSkill = (skillName: string) => {
    const newSkills = [...selectedSkills, skillName];
    setSelectedSkills(newSkills);
    fetchDrilldownData(newSkills);
  };

  const handleRemoveSkill = (skillName: string) => {
    const newSkills = selectedSkills.filter(s => s !== skillName);
    if (newSkills.length === 0) {
      onClose();
      return;
    }
    setSelectedSkills(newSkills);
    fetchDrilldownData(newSkills);
  };

  const handleViewJobs = async () => {
    if (!drilldownData || drilldownData.jobIds.length === 0) return;

    try {
      setLoading(true);
      const jobs = await getJobsByIds(drilldownData.jobIds, 30);
      onViewJobs(jobs, selectedSkills);
      onClose();
    } catch (err) {
      console.error('Failed to fetch jobs:', err);
      setError('Failed to load job details');
    } finally {
      setLoading(false);
    }
  };

  const handleClearAll = () => {
    setSelectedSkills([]);
    setDrilldownData(null);
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 z-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-lg shadow-xl max-w-4xl w-full max-h-[90vh] overflow-hidden flex flex-col">
        {/* Header */}
        <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
          <h2 className="text-xl font-bold text-gray-900">
            Jobs requiring {selectedSkills.join(' + ')}
          </h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto px-6 py-4">
          {/* Breadcrumb */}
          <div className="flex items-center gap-2 mb-6 flex-wrap">
            <span className="text-sm text-gray-600">Selected skills:</span>
            {selectedSkills.map((skill, index) => (
              <span key={skill} className="flex items-center gap-1">
                {index > 0 && <span className="text-gray-400 mx-1">→</span>}
                <button
                  onClick={() => handleRemoveSkill(skill)}
                  className="px-3 py-1 bg-indigo-100 text-indigo-800 rounded-full text-sm font-medium hover:bg-indigo-200 transition-colors flex items-center gap-1"
                >
                  {skill}
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </span>
            ))}
          </div>

          {loading && (
            <div className="flex items-center justify-center py-12">
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600"></div>
              <span className="ml-3 text-gray-600">Loading job data...</span>
            </div>
          )}

          {error && (
            <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 mb-4">
              {error}
            </div>
          )}

          {!loading && drilldownData && (
            <>
              {/* Statistics */}
              <div className="bg-indigo-50 border border-indigo-200 rounded-lg p-6 mb-6">
                <div className="flex items-center justify-between mb-4">
                  <div>
                    <h3 className="text-2xl font-bold text-indigo-900">{drilldownData.totalJobs}</h3>
                    <p className="text-sm text-indigo-700">Jobs found</p>
                  </div>
                  <svg className="w-12 h-12 text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 13.255A23.931 23.931 0 0112 15c-3.183 0-6.22-.62-9-1.745M16 6V4a2 2 0 00-2-2h-4a2 2 0 00-2 2v2m4 6h.01M5 20h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                  </svg>
                </div>

                {Object.keys(drilldownData.dateDistribution).length > 0 && (
                  <DateDistributionChart dateDistribution={drilldownData.dateDistribution} />
                )}
              </div>

              {/* Related Skills */}
              {drilldownData.relatedSkills.length > 0 && (
                <div>
                  <h3 className="text-lg font-semibold text-gray-900 mb-3 flex items-center gap-2">
                    <svg className="w-5 h-5 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                    </svg>
                    Related Skills (click to narrow results)
                  </h3>
                  <div className="flex flex-wrap gap-2">
                    {drilldownData.relatedSkills.slice(0, 30).map((skill) => (
                      <button
                        key={skill.skillName}
                        onClick={() => handleAddSkill(skill.skillName)}
                        className="px-4 py-2 bg-white border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50 hover:border-indigo-400 hover:text-indigo-900 transition-all duration-200 shadow-sm hover:shadow-md"
                      >
                        {skill.skillName}
                        <span className="ml-2 px-2 py-0.5 bg-gray-100 text-gray-700 rounded-full text-xs font-semibold">
                          {skill.jobCount}
                        </span>
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {drilldownData.relatedSkills.length === 0 && (
                <div className="text-center py-8 text-gray-500">
                  <p>No additional related skills found.</p>
                  <p className="text-sm mt-2">This combination covers all available skills!</p>
                </div>
              )}
            </>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-gray-200 flex items-center justify-between">
          <button
            onClick={handleClearAll}
            className="px-4 py-2 text-gray-700 hover:text-gray-900 transition-colors"
          >
            Clear All
          </button>
          <div className="flex gap-3">
            <button
              onClick={onClose}
              className="px-6 py-2 border border-gray-300 rounded-lg font-medium text-gray-700 hover:bg-gray-50 transition-colors"
            >
              Close
            </button>
            <button
              onClick={handleViewJobs}
              disabled={!drilldownData || drilldownData.totalJobs === 0 || loading}
              className="px-6 py-2 bg-indigo-600 text-white rounded-lg font-medium hover:bg-indigo-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              View {drilldownData?.totalJobs || 0} Jobs
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
