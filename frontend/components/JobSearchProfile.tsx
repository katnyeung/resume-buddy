'use client';

import { useState, useEffect } from 'react';
import {
  getJobSearchProfile,
  getJobSearchProfileLines,
  getProfileSkills,
  addProfileSkill,
  removeProfileSkill,
  updateProfileMetadata,
  updateJobPost,
  updateSkillProficiency
} from '@/lib/api';

interface JobSearchProfileProps {
  profileId: string;
  resumeId?: string;
}

export default function JobSearchProfile({ profileId, resumeId }: JobSearchProfileProps) {
  const [currentProfile, setCurrentProfile] = useState<any>(null);
  const [editedJobPost, setEditedJobPost] = useState<string>('');
  const [isSaving, setIsSaving] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);

  // Collapse state with localStorage persistence
  const [isExpanded, setIsExpanded] = useState(() => {
    if (typeof window !== 'undefined') {
      const saved = localStorage.getItem('jobSearchProfile_expanded');
      return saved !== null ? JSON.parse(saved) : true; // Default to expanded
    }
    return true;
  });

  // Form fields
  const [desiredJobTitle, setDesiredJobTitle] = useState<string>('');
  const [location, setLocation] = useState<string>('');
  const [experienceLevel, setExperienceLevel] = useState<string>('');
  const [skills, setSkills] = useState<any[]>([]);
  const [newSkillName, setNewSkillName] = useState<string>('');
  const [isAddingSkill, setIsAddingSkill] = useState(false);

  // Excluded keywords (deal-breakers)
  const [excludedKeywords, setExcludedKeywords] = useState<string[]>([]);
  const [newExcludedKeyword, setNewExcludedKeyword] = useState<string>('');

  // Save collapse state to localStorage
  const toggleExpanded = () => {
    const newState = !isExpanded;
    setIsExpanded(newState);
    if (typeof window !== 'undefined') {
      localStorage.setItem('jobSearchProfile_expanded', JSON.stringify(newState));
    }
  };

  // Load profile on mount
  useEffect(() => {
    const loadProfile = async () => {
      try {
        const profile = await getJobSearchProfile(profileId);
        setCurrentProfile(profile);

        // Set form fields from profile
        setDesiredJobTitle(profile.desiredJobTitle || '');
        setLocation(profile.location || '');
        setExperienceLevel(profile.experienceLevel || '');

        // Fetch lines from job_search_profile_line table (single source of truth)
        const lines = await getJobSearchProfileLines(profileId);
        // Reconstruct job post from lines
        const reconstructedJobPost = lines
          .sort((a: any, b: any) => a.lineNumber - b.lineNumber)
          .map((line: any) => line.lineContent)
          .join('\n');
        setEditedJobPost(reconstructedJobPost || '');

        // Fetch skills
        const profileSkills = await getProfileSkills(profileId);
        setSkills(profileSkills);

        // Load excluded keywords
        if (profile.excludedKeywords) {
          const keywords = profile.excludedKeywords.split(',').map((k: string) => k.trim()).filter((k: string) => k);
          setExcludedKeywords(keywords);
        }
      } catch (error) {
        console.error('Failed to load job search profile:', error);
      }
    };
    loadProfile();
  }, [profileId]);


  const handleAddSkill = async () => {
    if (!currentProfile || !newSkillName.trim()) {
      alert('Please enter a skill name');
      return;
    }

    setIsAddingSkill(true);
    try {
      const newSkill = await addProfileSkill(currentProfile.id, newSkillName.trim(), 'Technical');
      setSkills([...skills, newSkill]);
      setNewSkillName('');
    } catch (error) {
      console.error('Failed to add skill:', error);
      alert('Failed to add skill. It may already exist.');
    } finally {
      setIsAddingSkill(false);
    }
  };

  const handleRemoveSkill = async (skillId: string) => {
    if (!currentProfile) return;

    try {
      await removeProfileSkill(currentProfile.id, skillId);
      setSkills(skills.filter(s => s.id !== skillId));
    } catch (error) {
      console.error('Failed to remove skill:', error);
      alert('Failed to remove skill.');
    }
  };

  const handleAddExcludedKeyword = () => {
    const keyword = newExcludedKeyword.trim();
    if (keyword && !excludedKeywords.includes(keyword)) {
      setExcludedKeywords([...excludedKeywords, keyword]);
      setNewExcludedKeyword('');
    }
  };

  const handleRemoveExcludedKeyword = (index: number) => {
    setExcludedKeywords(excludedKeywords.filter((_, i) => i !== index));
  };

  const handleSaveJobPost = async () => {
    if (!currentProfile || !editedJobPost.trim()) {
      return;
    }

    setIsSaving(true);
    setSaveSuccess(false);
    try {
      // 1. Update metadata (location, experience level, and desired job title)
      await updateProfileMetadata(currentProfile.id, location, experienceLevel, desiredJobTitle);

      // 2. Update job post and regenerate vectors (includes excluded keywords)
      const updatedProfile = await updateJobPost(currentProfile.id, editedJobPost, excludedKeywords);
      setCurrentProfile(updatedProfile);

      // Show success indicator
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 3000); // Hide after 3 seconds
    } catch (error) {
      console.error('Failed to update profile:', error);
    } finally {
      setIsSaving(false);
    }
  };

  if (!currentProfile) {
    return (
      <div className="flex items-center justify-center p-8">
        <svg className="animate-spin h-8 w-8 text-gray-500" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
        </svg>
      </div>
    );
  }

  return (
    <div className="bg-gradient-to-r from-green-50 to-teal-50 border border-green-300 rounded-lg shadow-lg">
      {/* Collapsible Header */}
      <button
        onClick={toggleExpanded}
        className="w-full p-6 flex items-center justify-between hover:bg-green-100/50 transition-colors rounded-t-lg"
      >
        <div className="flex items-center gap-2">
          <svg className="h-6 w-6 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 13.255A23.931 23.931 0 0112 15c-3.183 0-6.22-.62-9-1.745M16 6V4a2 2 0 00-2-2h-4a2 2 0 00-2 2v2m4 6h.01M5 20h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
          </svg>
          <h3 className="text-xl font-bold text-gray-800">Job Search Profile</h3>
          {!isExpanded && desiredJobTitle && (
            <span className="text-sm text-gray-600 ml-2">
              - {desiredJobTitle} {location && `in ${location}`}
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs bg-green-200 text-green-800 px-3 py-1 rounded-full">
            ID: {currentProfile.id}
          </span>
          <svg
            className={`h-5 w-5 text-gray-600 transition-transform ${isExpanded ? 'rotate-180' : ''}`}
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </div>
      </button>

      {/* Collapsible Content */}
      {isExpanded && (
        <div className="px-6 pb-6">
          {/* Row 1: Desired Job Title (Full Width) */}
      <div className="mb-6">
        <label className="block text-sm font-semibold text-gray-700 mb-2">
          <svg className="inline h-4 w-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 13.255A23.931 23.931 0 0112 15c-3.183 0-6.22-.62-9-1.745M16 6V4a2 2 0 00-2-2h-4a2 2 0 00-2 2v2m4 6h.01M5 20h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
          </svg>
          Desired Job Title
        </label>
        <input
          type="text"
          value={desiredJobTitle}
          onChange={(e) => setDesiredJobTitle(e.target.value)}
          placeholder="e.g., Senior Java Developer, Data Scientist, Full Stack Engineer"
          disabled={isSaving}
          className="w-full px-4 py-2.5 text-base border-2 border-gray-300 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-green-500 disabled:bg-gray-100 font-medium"
        />
        <p className="mt-1.5 text-xs text-gray-500">
          This will be used as the primary keyword for job searching in Adzuna and other job boards
        </p>
      </div>

      {/* Row 2: Metadata (Location + Experience Level) */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
        <div>
          <label className="block text-sm font-semibold text-gray-700 mb-2">
            <svg className="inline h-4 w-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
            Target Location
          </label>
          <input
            type="text"
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            placeholder="e.g., San Francisco, CA or Remote"
            disabled={isSaving}
            className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-transparent disabled:bg-gray-100"
          />
        </div>

        <div>
          <label className="block text-sm font-semibold text-gray-700 mb-2">
            <svg className="inline h-4 w-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4M7.835 4.697a3.42 3.42 0 001.946-.806 3.42 3.42 0 014.438 0 3.42 3.42 0 001.946.806 3.42 3.42 0 013.138 3.138 3.42 3.42 0 00.806 1.946 3.42 3.42 0 010 4.438 3.42 3.42 0 00-.806 1.946 3.42 3.42 0 01-3.138 3.138 3.42 3.42 0 00-1.946.806 3.42 3.42 0 01-4.438 0 3.42 3.42 0 00-1.946-.806 3.42 3.42 0 01-3.138-3.138 3.42 3.42 0 00-.806-1.946 3.42 3.42 0 010-4.438 3.42 3.42 0 00.806-1.946 3.42 3.42 0 013.138-3.138z" />
            </svg>
            Target Experience Level
          </label>
          <select
            value={experienceLevel}
            onChange={(e) => setExperienceLevel(e.target.value)}
            disabled={isSaving}
            className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-transparent disabled:bg-gray-100"
          >
            <option value="">Select level...</option>
            <option value="Entry Level">Entry Level (0-2 years)</option>
            <option value="Mid Level">Mid Level (3-5 years)</option>
            <option value="Senior">Senior (6-10 years)</option>
            <option value="Lead">Lead/Principal (10+ years)</option>
            <option value="Executive">Executive/Director</option>
          </select>
        </div>
      </div>

      {/* Row 3: Skills */}
      <div className="mb-6">
        <label className="block text-sm font-semibold text-gray-700 mb-2">
          <svg className="inline h-4 w-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
          Skills ({skills.length})
        </label>
        <div className="bg-white border border-gray-300 rounded-lg p-4">
          <div className="flex flex-wrap gap-2 mb-3">
            {skills
              .sort((a, b) => (b.proficiencyScore || 50) - (a.proficiencyScore || 50)) // Sort by proficiency descending
              .map((skill) => {
              const proficiency = skill.proficiencyScore || 50;

              // Map score to 5 levels with focus labels
              const getProficiencyLevel = (score: number) => {
                if (score >= 90) return { level: 5, label: 'Highly Focus', icon: '⭐⭐⭐', color: 'text-yellow-600', bgColor: 'bg-yellow-50' };
                if (score >= 70) return { level: 4, label: 'High Focus', icon: '⭐⭐', color: 'text-blue-600', bgColor: 'bg-blue-50' };
                if (score >= 50) return { level: 3, label: 'Moderate Focus', icon: '⭐', color: 'text-indigo-600', bgColor: 'bg-indigo-50' };
                if (score >= 30) return { level: 2, label: 'Low Focus', icon: '○', color: 'text-gray-600', bgColor: 'bg-gray-50' };
                if (score >= 10) return { level: 1, label: 'Less Focus', icon: '○', color: 'text-gray-500', bgColor: 'bg-gray-50' };
                return { level: 0, label: 'Minimal', icon: '○', color: 'text-gray-400', bgColor: 'bg-gray-50' };
              };

              const currentLevel = getProficiencyLevel(proficiency);
              const canIncrease = proficiency < 100;
              const canDecrease = proficiency > 0;

              const handleIncrease = async () => {
                const newScore = Math.min(proficiency + 20, 100);
                try {
                  const updated = await updateSkillProficiency(currentProfile.id, skill.id, newScore);
                  setSkills(skills.map(s => s.id === skill.id ? updated : s));
                } catch (error) {
                  console.error('Failed to increase proficiency:', error);
                  alert('Failed to update proficiency');
                }
              };

              const handleDecrease = async () => {
                const newScore = Math.max(proficiency - 20, 0);
                try {
                  const updated = await updateSkillProficiency(currentProfile.id, skill.id, newScore);
                  setSkills(skills.map(s => s.id === skill.id ? updated : s));
                } catch (error) {
                  console.error('Failed to decrease proficiency:', error);
                  alert('Failed to update proficiency');
                }
              };

              return (
                <div key={skill.id} className={`inline-flex items-center gap-1.5 ${currentLevel.bgColor} border ${currentLevel.color.replace('text-', 'border-')} rounded-lg px-2.5 py-1.5`}>
                  {/* Rank icon */}
                  <span className={`text-xs ${currentLevel.color}`} title={`${currentLevel.label} (${proficiency}/100)`}>
                    {currentLevel.icon}
                  </span>

                  {/* Skill name */}
                  <span className="text-gray-800 text-sm font-medium">
                    {skill.skillName}
                  </span>

                  {/* Focus label */}
                  <span className={`text-[10px] ${currentLevel.color} font-semibold`}>
                    {currentLevel.label}
                  </span>

                  {/* Up button - smaller */}
                  <button
                    onClick={handleIncrease}
                    disabled={!canIncrease || isSaving}
                    className="p-0.5 bg-green-500 text-white rounded hover:bg-green-600 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors ml-1"
                    title="Increase importance (+20)"
                  >
                    <svg className="w-2.5 h-2.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 15l7-7 7 7" />
                    </svg>
                  </button>

                  {/* Down button - smaller */}
                  <button
                    onClick={handleDecrease}
                    disabled={!canDecrease || isSaving}
                    className="p-0.5 bg-red-500 text-white rounded hover:bg-red-600 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors"
                    title="Decrease importance (-20)"
                  >
                    <svg className="w-2.5 h-2.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M19 9l-7 7-7-7" />
                    </svg>
                  </button>

                  {/* Remove button */}
                  <button
                    onClick={() => handleRemoveSkill(skill.id)}
                    disabled={isSaving}
                    className="text-gray-600 hover:text-red-600 disabled:opacity-50 text-base leading-none ml-0.5"
                    title="Remove skill"
                  >
                    ×
                  </button>
                </div>
              );
            })}
            {skills.length === 0 && (
              <p className="text-sm text-gray-500">No skills added yet. Add skills below.</p>
            )}
          </div>
          <div className="flex gap-2">
            <input
              type="text"
              value={newSkillName}
              onChange={(e) => setNewSkillName(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && handleAddSkill()}
              placeholder="Type skill name (e.g., React, Python)"
              disabled={isSaving || isAddingSkill}
              className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-green-500 focus:border-transparent"
            />
            <button
              onClick={handleAddSkill}
              disabled={isSaving || isAddingSkill || !newSkillName.trim()}
              className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed flex items-center gap-1"
            >
              {isAddingSkill ? (
                <>
                  <svg className="animate-spin h-4 w-4" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  Adding...
                </>
              ) : (
                <>+ Add</>
              )}
            </button>
          </div>
        </div>
      </div>

      {/* Row 3.5: Deal-Breaker Keywords */}
      <div className="mb-6">
        <label className="block text-sm font-semibold text-gray-700 mb-2">
          <svg className="inline h-4 w-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          Deal-Breaker Keywords ({excludedKeywords.length})
        </label>
        <p className="text-xs text-gray-500 mb-3">
          Jobs containing these keywords will be highlighted in red in the results table (e.g., "SC Clearance", "PhD required", "blockchain")
        </p>
        <div className="bg-white border border-gray-300 rounded-lg p-4">
          <div className="flex flex-wrap gap-2 mb-3">
            {excludedKeywords.map((keyword, index) => (
              <span key={index} className="inline-flex items-center gap-2 px-3 py-1.5 bg-red-100 text-red-800 border border-red-300 rounded-full text-sm font-medium">
                {keyword}
                <button
                  onClick={() => handleRemoveExcludedKeyword(index)}
                  disabled={isSaving}
                  className="text-red-600 hover:text-red-800 disabled:opacity-50 text-base leading-none"
                  title="Remove keyword"
                >
                  ×
                </button>
              </span>
            ))}
            {excludedKeywords.length === 0 && (
              <p className="text-sm text-gray-500">No deal-breaker keywords added yet. Add keywords below.</p>
            )}
          </div>
          <div className="flex gap-2">
            <input
              type="text"
              value={newExcludedKeyword}
              onChange={(e) => setNewExcludedKeyword(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && handleAddExcludedKeyword()}
              placeholder="Type keyword and press Enter (e.g., SC Clearance, PhD required)"
              disabled={isSaving}
              className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-red-500 focus:border-transparent"
            />
            <button
              onClick={handleAddExcludedKeyword}
              disabled={isSaving || !newExcludedKeyword.trim()}
              className="px-4 py-2 bg-red-600 text-white rounded-lg text-sm font-medium hover:bg-red-700 disabled:bg-gray-400 disabled:cursor-not-allowed flex items-center gap-1"
            >
              + Add
            </button>
          </div>
        </div>
      </div>

      {/* Row 4: Job Post Editor */}
      <div className="mb-4">
        <label className="block text-sm font-semibold text-gray-700 mb-2">
          <svg className="inline h-4 w-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
          </svg>
          Mock Job Requirements (will split by line and vectorize on save)
        </label>
        <p className="text-xs text-gray-500 mb-3">
          Edit text freely. Each line (separated by newline) will be vectorized when you click "Save All Changes".
        </p>
        <textarea
          value={editedJobPost}
          onChange={(e) => setEditedJobPost(e.target.value)}
          disabled={isSaving}
          placeholder="Enter job requirements (one per line)...&#10;Example:&#10;5+ years of experience in Java development&#10;Strong knowledge of Spring Boot and microservices&#10;Experience with cloud platforms (AWS/Azure)"
          rows={12}
          className={`w-full px-4 py-3 border-2 border-gray-300 rounded-lg text-sm font-mono focus:ring-2 focus:ring-green-500 focus:border-green-500 resize-y ${
            isSaving ? 'bg-gray-100 cursor-not-allowed' : ''
          }`}
        />

        {/* Vector Matching Tips */}
        <div className="mt-3 bg-blue-50 border border-blue-200 rounded-lg p-4">
          <h4 className="text-sm font-semibold text-blue-900 mb-2 flex items-center gap-2">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            How Vector Matching Works
          </h4>
          <div className="space-y-2 text-xs text-blue-800">
            <div className="flex items-start gap-2">
              <span className="text-green-600 font-bold mt-0.5">✓</span>
              <div>
                <strong>Use detailed phrases</strong> for better matches:
                <div className="mt-1 bg-white rounded px-2 py-1 font-mono text-[11px]">
                  Good: "5+ years Java development experience"<br/>
                  Avoid: "Java"
                </div>
              </div>
            </div>
            <div className="flex items-start gap-2">
              <span className="text-green-600 font-bold mt-0.5">✓</span>
              <div>
                <strong>Add context</strong> to single-word skills:
                <div className="mt-1 bg-white rounded px-2 py-1 font-mono text-[11px]">
                  Good: "DevOps and CI/CD pipeline expertise"<br/>
                  Avoid: "DevOps"
                </div>
              </div>
            </div>
            <div className="flex items-start gap-2">
              <span className="text-blue-600 font-bold mt-0.5">ℹ</span>
              <div>
                <strong>How it works:</strong> Each line is converted to a semantic vector (AI embedding). Jobs with similar requirements get higher match scores. Single words match broadly but with lower confidence (~65-75%). Detailed phrases match more precisely (~80-95%).
              </div>
            </div>
            <div className="flex items-start gap-2">
              <span className="text-purple-600 font-bold mt-0.5">★</span>
              <div>
                <strong>Match scoring:</strong> Jobs matching MORE of your requirements rank higher. Coverage (% lines matched) + precision (best match quality) + breadth (multiple strong matches) = final score.
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Footer: Metadata + Save Button */}
      <div className="flex items-center justify-between pt-4 border-t border-green-200">
        <p className="text-xs text-gray-600">
          <strong>Created:</strong> {new Date(currentProfile.createdAt).toLocaleString()}
          {currentProfile.updatedAt && currentProfile.updatedAt !== currentProfile.createdAt && (
            <> | <strong>Updated:</strong> {new Date(currentProfile.updatedAt).toLocaleString()}</>
          )}
        </p>
        <div className="flex items-center gap-3">
          {saveSuccess && (
            <span className="text-green-600 text-sm font-medium flex items-center gap-1">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              Saved
            </span>
          )}
          <button
            onClick={handleSaveJobPost}
            disabled={isSaving || !editedJobPost.trim()}
            className="px-6 py-2.5 bg-green-600 text-white rounded-lg text-sm font-semibold hover:bg-green-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors flex items-center gap-2 shadow-md"
          >
            {isSaving ? (
              <>
                <svg className="animate-spin h-5 w-5" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                Saving All Changes...
              </>
            ) : (
              <>
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7H5a2 2 0 00-2 2v9a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-3m-1 4l-3 3m0 0l-3-3m3 3V4" />
                </svg>
                Save All Changes
              </>
            )}
          </button>
        </div>
      </div>
        </div>
      )}
    </div>
  );
}
