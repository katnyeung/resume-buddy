'use client';

import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import JobSearchProfile from '@/components/JobSearchProfile';
import JobMatchingResults from '@/components/JobMatchingResults';
import CrawlActivityHistory from '@/components/CrawlActivityHistory';
import AppHeader from '@/components/AppHeader';
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
        if (profile && profile.resumeId) {
          setResumeId(profile.resumeId);
        }
      } catch (error) {
        console.error('Failed to fetch profile:', error);
        // Retry once after 500ms on failure
        setTimeout(() => {
          getJobSearchProfile(profileId)
            .then(profile => {
              if (profile && profile.resumeId) {
                setResumeId(profile.resumeId);
              }
            })
            .catch(err => console.error('Retry failed:', err));
        }, 500);
      }
    };
    fetchProfile();
  }, [profileId]);

  return (
    <div className="min-h-screen bg-gray-50">
      <AppHeader title="Job Search & Matching" showBackButton={true} />

      <div className="max-w-6xl mx-auto px-4 py-8">
        {/* Page Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 mb-2">Job Search</h1>
          <p className="text-gray-600">
            Manage your job search profile and find matching opportunities
          </p>
        </div>

        {/* Crawl Activity History */}
        <CrawlActivityHistory />

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
