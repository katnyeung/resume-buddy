'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import AppHeader from '@/components/AppHeader';
import { listResumes, getJobSearchProfilesByResume } from '@/lib/api';
import { getCurrentUserId } from '@/lib/userUtils';

interface ProfileWithResume {
  profileId: string;
  resumeId: string;
  resumeFilename: string;
  createdAt?: string;
}

export default function JobSearchIndexPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [profiles, setProfiles] = useState<ProfileWithResume[]>([]);

  useEffect(() => {
    findProfiles();
  }, []);

  const findProfiles = async () => {
    try {
      setLoading(true);

      // Get user's resumes
      const resumes = await listResumes();

      if (!resumes || resumes.length === 0) {
        setError('No resumes found. Please upload and analyze a resume first.');
        setLoading(false);
        return;
      }

      // Check each resume for profiles
      const allProfiles: ProfileWithResume[] = [];
      for (const resume of resumes) {
        try {
          const profiles = await getJobSearchProfilesByResume(resume.id);
          if (profiles && profiles.length > 0) {
            // Add each profile with resume info
            profiles.forEach((profile: any) => {
              allProfiles.push({
                profileId: profile.id,
                resumeId: resume.id,
                resumeFilename: resume.filename || 'Unnamed Resume',
                createdAt: profile.createdAt
              });
            });
          }
        } catch (err) {
          // No profiles for this resume, continue checking others
          console.log(`No profiles found for resume ${resume.id}`);
        }
      }

      if (allProfiles.length === 0) {
        // No profiles found in any resume
        setError('No job search profiles found. Please go to a resume analysis page and click "Generate Mock Job Post" to create your first profile.');
        setLoading(false);
      } else if (allProfiles.length === 1) {
        // Only one profile - auto-redirect
        router.push(`/job-search/${allProfiles[0].profileId}`);
      } else {
        // Multiple profiles - show selection
        setProfiles(allProfiles);
        setLoading(false);
      }
    } catch (err) {
      console.error('Error finding profiles:', err);
      setError('Failed to load job search profiles. Please try again.');
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <AppHeader title="Job Search" />

      <div className="max-w-4xl mx-auto px-4 py-8">
        {loading ? (
          <div className="flex flex-col items-center justify-center py-12">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mb-4"></div>
            <div className="text-gray-600">Finding your job search profiles...</div>
          </div>
        ) : error ? (
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-8 text-center">
            <svg className="w-16 h-16 mx-auto mb-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 13.255A23.931 23.931 0 0112 15c-3.183 0-6.22-.62-9-1.745M16 6V4a2 2 0 00-2-2h-4a2 2 0 00-2 2v2m4 6h.01M5 20h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
            </svg>
            <h2 className="text-xl font-bold text-gray-900 mb-2">No Job Search Profile Found</h2>
            <p className="text-gray-600 mb-6">{error}</p>
            <div className="flex gap-4 justify-center">
              <button
                onClick={() => router.push('/')}
                className="px-6 py-3 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 transition-colors"
              >
                Go to Dashboard
              </button>
            </div>
          </div>
        ) : profiles.length > 0 ? (
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-8">
            <div className="mb-6">
              <h2 className="text-2xl font-bold text-gray-900 mb-2">Select a Job Search Profile</h2>
              <p className="text-gray-600">You have multiple job search profiles. Choose one to view matching jobs.</p>
            </div>
            <div className="space-y-4">
              {profiles.map((profile) => (
                <button
                  key={profile.profileId}
                  onClick={() => router.push(`/job-search/${profile.profileId}`)}
                  className="w-full text-left p-6 border-2 border-gray-200 rounded-lg hover:border-blue-500 hover:bg-blue-50 transition-all group"
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-4">
                      <div className="w-12 h-12 rounded-lg bg-blue-100 flex items-center justify-center group-hover:bg-blue-200 transition-colors">
                        <svg className="w-6 h-6 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                        </svg>
                      </div>
                      <div>
                        <h3 className="text-lg font-semibold text-gray-900 group-hover:text-blue-600 transition-colors">
                          {profile.resumeFilename}
                        </h3>
                        <p className="text-sm text-gray-500">
                          {profile.createdAt ? `Created ${new Date(profile.createdAt).toLocaleDateString()}` : 'Job Search Profile'}
                        </p>
                      </div>
                    </div>
                    <svg className="w-6 h-6 text-gray-400 group-hover:text-blue-600 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                    </svg>
                  </div>
                </button>
              ))}
            </div>
          </div>
        ) : null}
      </div>
    </div>
  );
}
