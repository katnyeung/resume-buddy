'use client';

import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import JobSearchProfile from '@/components/JobSearchProfile';
import JobMatchingResults from '@/components/JobMatchingResults';
import Link from 'next/link';
import { getJobSearchProfile } from '@/lib/api';

export default function JobSearchPage() {
  const params = useParams();
  const profileId = params.profileId as string;
  const [resumeId, setResumeId] = useState<string | null>(null);

  // Fetch profile to get resumeId
  useEffect(() => {
    const fetchProfile = async () => {
      try {
        const profile = await getJobSearchProfile(profileId);
        setResumeId(profile.resumeId);
      } catch (error) {
        console.error('Failed to fetch profile:', error);
      }
    };
    fetchProfile();
  }, [profileId]);

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-6xl mx-auto px-4 py-8">
        {/* Breadcrumb Navigation */}
        <div className="mb-6">
          <Link
            href={resumeId ? `/resume/${resumeId}` : '/'}
            className="text-sm text-gray-600 hover:text-gray-900 flex items-center gap-1"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
            Back to Resume
          </Link>
        </div>

        {/* Page Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 mb-2">Job Search</h1>
          <p className="text-gray-600">
            Manage your job search profile and find matching opportunities
          </p>
        </div>

        {/* Job Search Profile Section */}
        <JobSearchProfile profileId={profileId} resumeId={resumeId || undefined} />

        {/* Job Matching Results */}
        <div className="mt-8">
          <JobMatchingResults profileId={profileId} />
        </div>
      </div>
    </div>
  );
}
