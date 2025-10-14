'use client';

import { ResumeAnalysisDto } from '@/lib/types';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import {
  createJobSearchProfile,
  updateJobPost,
  getJobSearchProfilesByResume,
  getJobSearchProfileLines,
  getProfileSkills,
  addProfileSkill,
  removeProfileSkill,
  updateProfileMetadata,
  updateSkillProficiency
} from '@/lib/api';
import type { EditorState } from 'lexical';
import dynamic from 'next/dynamic';

// Dynamically import JobProfileEditor to avoid SSR issues with Lexical
const JobProfileEditor = dynamic(() => import('./JobProfileEditor'), {
  ssr: false,
  loading: () => <div className="h-64 bg-gray-100 animate-pulse rounded-lg"></div>
});

interface AnalysisSummaryProps {
  analysis: ResumeAnalysisDto;
  resumeId: string;
  onAnalyzeJob?: (experienceId: string) => void;
  onFindJobs?: (experienceId: string) => void;
}

export default function AnalysisSummary({ analysis, resumeId, onAnalyzeJob, onFindJobs }: AnalysisSummaryProps) {
  const router = useRouter();
  const [analyzingJobId, setAnalyzingJobId] = useState<string | null>(null);
  const [generatingProfile, setGeneratingProfile] = useState(false);
  const [selectedExperiences, setSelectedExperiences] = useState<Set<string>>(new Set());
  const [currentProfile, setCurrentProfile] = useState<any>(null);
  const [editedJobPost, setEditedJobPost] = useState<string>('');
  const [currentEditorState, setCurrentEditorState] = useState<EditorState | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  // Form fields
  const [location, setLocation] = useState<string>('');
  const [experienceLevel, setExperienceLevel] = useState<string>('');
  const [skills, setSkills] = useState<any[]>([]);
  const [newSkillName, setNewSkillName] = useState<string>('');
  const [isAddingSkill, setIsAddingSkill] = useState(false);

  // Build analyzed experiences map from the isAnalyzed flag in each experience
  const analyzedExperiences: Record<string, boolean> = {};
  analysis.experiences.forEach(exp => {
    analyzedExperiences[exp.id] = exp.isAnalyzed || false;
  });

  // Load existing profile on mount
  useEffect(() => {
    const loadProfile = async () => {
      try {
        const profiles = await getJobSearchProfilesByResume(resumeId);
        if (profiles && profiles.length > 0) {
          // Get most recent profile
          const latestProfile = profiles[0];
          setCurrentProfile(latestProfile);

          // Set form fields from profile
          setLocation(latestProfile.location || '');
          setExperienceLevel(latestProfile.experienceLevel || '');

          // Fetch lines from job_search_profile_line table (single source of truth)
          const lines = await getJobSearchProfileLines(latestProfile.id);
          // Reconstruct job post from lines
          const reconstructedJobPost = lines
            .sort((a: any, b: any) => a.lineNumber - b.lineNumber)
            .map((line: any) => line.lineContent)
            .join('\n');
          setEditedJobPost(reconstructedJobPost || '');

          // Fetch skills
          const profileSkills = await getProfileSkills(latestProfile.id);
          setSkills(profileSkills);
        }
      } catch (error) {
        console.error('Failed to load job search profile:', error);
      }
    };
    loadProfile();
  }, [resumeId]);

  const handleViewAnalysis = (analysisId: string) => {
    router.push(`/analysis/${analysisId}`);
  };

  const handleAnalyzeJobClick = async (experienceId: string) => {
    setAnalyzingJobId(experienceId);
    try {
      await onAnalyzeJob?.(experienceId);
    } finally {
      setAnalyzingJobId(null);
    }
  };

  const toggleExperienceSelection = (experienceId: string) => {
    const newSelection = new Set(selectedExperiences);
    if (newSelection.has(experienceId)) {
      newSelection.delete(experienceId);
    } else {
      newSelection.add(experienceId);
    }
    setSelectedExperiences(newSelection);
  };

  const handleGenerateJobPost = async () => {
    if (selectedExperiences.size === 0) {
      alert('Please select at least one experience to generate a job post');
      return;
    }

    setGeneratingProfile(true);
    try {
      const profile = await createJobSearchProfile(resumeId, Array.from(selectedExperiences));
      setCurrentProfile(profile);

      // Auto-fill location and experience level from LLM-generated metadata
      setLocation(profile.location || '');
      setExperienceLevel(profile.experienceLevel || '');

      // Fetch lines from job_search_profile_line table (single source of truth)
      const lines = await getJobSearchProfileLines(profile.id);
      // Reconstruct job post from lines
      const reconstructedJobPost = lines
        .sort((a: any, b: any) => a.lineNumber - b.lineNumber)
        .map((line: any) => line.lineContent)
        .join('\n');
      setEditedJobPost(reconstructedJobPost || '');

      // Fetch skills
      const profileSkills = await getProfileSkills(profile.id);
      setSkills(profileSkills);

      alert('Job search profile generated successfully! Location, experience level, and skills have been auto-filled. You can edit them above.');
    } catch (error) {
      console.error('Failed to generate job post:', error);
      alert('Failed to generate job post. Please try again.');
    } finally {
      setGeneratingProfile(false);
    }
  };

  // Extract plain text from Lexical editor state
  const extractTextFromEditorState = (editorState: EditorState): string => {
    const textContent: string[] = [];
    editorState.read(() => {
      const root = editorState._nodeMap.get('root');
      if (root && 'getTextContent' in root) {
        const text = (root as any).getTextContent();
        return text;
      }
    });

    // Alternative: serialize and extract text from JSON
    const json = editorState.toJSON();
    const extractText = (node: any): void => {
      if (node.type === 'text') {
        textContent.push(node.text);
      }
      if (node.children) {
        node.children.forEach(extractText);
      }
      if (node.type === 'paragraph' || node.type === 'listitem') {
        textContent.push('\n');
      }
    };

    if (json.root && json.root.children) {
      json.root.children.forEach(extractText);
    }

    return textContent.join('').trim();
  };

  const handleEditorChange = (editorState: EditorState) => {
    setCurrentEditorState(editorState);
    // Extract plain text for backend
    const plainText = extractTextFromEditorState(editorState);
    setEditedJobPost(plainText);
  };

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

  const handleSaveJobPost = async () => {
    if (!currentProfile || !editedJobPost.trim()) {
      alert('Please enter job post content before saving');
      return;
    }

    setIsSaving(true);
    try {
      // 1. Update metadata (location and experience level)
      await updateProfileMetadata(currentProfile.id, location, experienceLevel);

      // 2. Update job post and regenerate vectors
      const updatedProfile = await updateJobPost(currentProfile.id, editedJobPost);
      setCurrentProfile(updatedProfile);

      alert('Profile saved successfully! Vectors regenerated.');
    } catch (error) {
      console.error('Failed to update profile:', error);
      alert('Failed to save profile. Please try again.');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <>
      {/* Job Search Profile Form - Comprehensive */}
      {currentProfile && (
        <div className="bg-gradient-to-r from-green-50 to-teal-50 border border-green-300 rounded-lg p-6 mb-6 shadow-lg">
          {/* Header */}
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center gap-2">
              <svg className="h-6 w-6 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 13.255A23.931 23.931 0 0112 15c-3.183 0-6.22-.62-9-1.745M16 6V4a2 2 0 00-2-2h-4a2 2 0 00-2 2v2m4 6h.01M5 20h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
              </svg>
              <h3 className="text-xl font-bold text-gray-800">Job Search Profile</h3>
            </div>
            <span className="text-xs bg-green-200 text-green-800 px-3 py-1 rounded-full">
              ID: {currentProfile.id}
            </span>
          </div>

          {/* Row 1: Metadata (Location + Experience Level) */}
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

          {/* Row 2: Skills */}
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

          {/* Row 3: Job Post Editor */}
          <div className="mb-4">
            <label className="block text-sm font-semibold text-gray-700 mb-2">
              <svg className="inline h-4 w-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
              </svg>
              Mock Job Requirements (vectors will regenerate on save)
            </label>
            <JobProfileEditor
              initialContent={editedJobPost}
              onChange={handleEditorChange}
              disabled={isSaving}
            />
          </div>

          {/* Footer: Metadata + Save Button */}
          <div className="flex items-center justify-between pt-4 border-t border-green-200">
            <p className="text-xs text-gray-600">
              <strong>Created:</strong> {new Date(currentProfile.createdAt).toLocaleString()}
              {currentProfile.updatedAt && currentProfile.updatedAt !== currentProfile.createdAt && (
                <> | <strong>Updated:</strong> {new Date(currentProfile.updatedAt).toLocaleString()}</>
              )}
            </p>
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
      )}

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
                <div className="flex items-center justify-between mb-3">
                  <h5 className="font-semibold text-gray-700">Work Experience</h5>
                  <button
                    onClick={handleGenerateJobPost}
                    disabled={generatingProfile || selectedExperiences.size === 0}
                    className="px-4 py-2 bg-indigo-600 text-white rounded-md text-sm font-medium hover:bg-indigo-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors flex items-center gap-2"
                  >
                    {generatingProfile ? (
                      <>
                        <svg className="animate-spin h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                        </svg>
                        Generating...
                      </>
                    ) : (
                      <>
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                        </svg>
                        Generate Mock Job Post ({selectedExperiences.size} selected)
                      </>
                    )}
                  </button>
                </div>
                <div className="space-y-3">
                  {analysis.experiences.map((exp, index) => (
                    <div key={exp.id} className="text-sm border-l-4 border-blue-400 pl-4 py-2 bg-blue-50 rounded-r relative">
                      <input
                        type="checkbox"
                        checked={selectedExperiences.has(exp.id)}
                        onChange={() => toggleExperienceSelection(exp.id)}
                        className="absolute top-3 right-3 h-5 w-5 text-indigo-600 rounded cursor-pointer"
                        title="Select for job post generation"
                      />
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
                        <div className="text-gray-700 text-xs leading-relaxed mt-2 mb-3 whitespace-pre-wrap">
                          {exp.description.split('\\n').join('\n')}
                        </div>
                      )}

                      {/* Action Buttons */}
                      <div className="flex gap-2 mt-3 pt-2 border-t border-blue-200 flex-wrap">
                        {analyzedExperiences[exp.id] ? (
                          <>
                            {/* View Latest Report Button (Primary) */}
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                if (exp.analysisId) {
                                  handleViewAnalysis(exp.analysisId);
                                }
                              }}
                              className="px-3 py-1.5 bg-indigo-600 text-white rounded-md text-xs font-medium hover:bg-indigo-700 transition-colors flex items-center gap-1.5 shadow-sm disabled:bg-gray-400 disabled:cursor-not-allowed"
                              title="View latest analysis report"
                              disabled={!exp.analysisId}
                            >
                              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                              </svg>
                              View Latest Report
                            </button>

                            {/* Re-analyze Button (Secondary) */}
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                if (confirm('This job has already been analyzed. Re-analyze? This will overwrite the existing analysis.')) {
                                  handleAnalyzeJobClick(exp.id);
                                }
                              }}
                              disabled={analyzingJobId === exp.id}
                              className="px-3 py-1.5 bg-purple-50 text-purple-600 border border-purple-200 rounded-md text-xs font-medium hover:bg-purple-100 disabled:bg-gray-200 disabled:text-gray-400 disabled:cursor-not-allowed transition-colors flex items-center gap-1"
                              title="Re-analyze this job (will overwrite existing analysis)"
                            >
                              {analyzingJobId === exp.id ? (
                                <>
                                  <svg className="animate-spin h-3 w-3" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                  </svg>
                                  Analyzing...
                                </>
                              ) : (
                                <>
                                  <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                                  </svg>
                                  Re-analyze
                                </>
                              )}
                            </button>
                          </>
                        ) : (
                          /* Analyze Job Button (Primary - First time) */
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              handleAnalyzeJobClick(exp.id);
                            }}
                            disabled={analyzingJobId === exp.id}
                            className="px-3 py-1.5 bg-purple-600 text-white rounded-md text-xs font-medium hover:bg-purple-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors flex items-center gap-1 shadow-sm"
                            title="Analyze this job experience with AI"
                          >
                            {analyzingJobId === exp.id ? (
                              <>
                                <svg className="animate-spin h-3 w-3" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                </svg>
                                Analyzing...
                              </>
                            ) : (
                              <>
                                <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                                </svg>
                                Analyze Job
                              </>
                            )}
                          </button>
                        )}

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
    </>
  );
}
