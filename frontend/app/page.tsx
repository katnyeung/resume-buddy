'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import ResumeList from '@/components/ResumeList';
import CreditBalance from '@/components/CreditBalance';
import { listResumes, deleteResume } from '@/lib/api';
import { getCurrentUserId } from '@/lib/userUtils';
import { Resume } from '@/lib/types';
import Link from 'next/link';

export default function HomePage() {
  const router = useRouter();
  const { user, loading: authLoading, logout } = useAuth();
  const [resumes, setResumes] = useState<Resume[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!authLoading && user) {
      loadResumes();
    } else if (!authLoading && !user) {
      setLoading(false);
    }
  }, [authLoading, user]);

  const loadResumes = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await listResumes();
      // Sort by most recent first (using updatedAt or createdAt)
      const sortedData = data.sort((a, b) => {
        const dateA = new Date(a.updatedAt || a.createdAt).getTime();
        const dateB = new Date(b.updatedAt || b.createdAt).getTime();
        return dateB - dateA; // Descending order (newest first)
      });
      setResumes(sortedData);
    } catch (err) {
      console.error('Failed to load resumes:', err);
      setError('Failed to load resumes');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Are you sure you want to delete this resume?')) {
      return;
    }

    try {
      await deleteResume(id);
      setResumes(resumes.filter(r => r.id !== id));
    } catch (err) {
      console.error('Failed to delete resume:', err);
      alert('Failed to delete resume');
    }
  };

  // Show landing page if not authenticated
  if (!user && !authLoading) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-blue-50 via-white to-gray-100">
        {/* Navigation */}
        <nav className="px-6 py-4 border-b border-gray-200 bg-white/80 backdrop-blur-sm">
          <div className="max-w-7xl mx-auto flex justify-between items-center">
            <h1 className="text-2xl font-bold text-blue-600">Resume Buddy</h1>
            <div className="flex gap-4">
              <Link
                href="/login"
                className="px-4 py-2 text-gray-700 font-medium hover:text-blue-600 transition-colors"
              >
                Sign In
              </Link>
              <Link
                href="/register"
                className="px-6 py-2 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 transition-colors"
              >
                Get Started
              </Link>
            </div>
          </div>
        </nav>

        {/* Hero Section */}
        <div className="max-w-7xl mx-auto px-6 py-20">
          <div className="text-center max-w-3xl mx-auto">
            <h2 className="text-5xl font-bold text-gray-900 mb-6">
              AI-Powered Resume Enhancement
            </h2>
            <p className="text-xl text-gray-600 mb-8">
              Transform your resume with intelligent analysis, job matching, and personalized recommendations
            </p>
            <div className="flex gap-4 justify-center">
              <Link
                href="/register"
                className="px-8 py-4 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 transition-colors text-lg shadow-lg hover:shadow-xl"
              >
                Start Free
              </Link>
              <Link
                href="/login"
                className="px-8 py-4 bg-white text-gray-700 rounded-lg font-medium hover:bg-gray-50 transition-colors text-lg border border-gray-300"
              >
                Sign In
              </Link>
            </div>
          </div>

          {/* Features */}
          <div className="mt-20 grid md:grid-cols-3 gap-8">
            <div className="bg-white p-8 rounded-xl shadow-md">
              <div className="text-blue-600 text-3xl mb-4">✨</div>
              <h3 className="text-xl font-bold text-gray-900 mb-3">AI Analysis</h3>
              <p className="text-gray-600">
                Get instant feedback on your resume with advanced AI-powered analysis
              </p>
            </div>
            <div className="bg-white p-8 rounded-xl shadow-md">
              <div className="text-blue-600 text-3xl mb-4">🎯</div>
              <h3 className="text-xl font-bold text-gray-900 mb-3">Job Matching</h3>
              <p className="text-gray-600">
                Find the perfect jobs with intelligent vector-based matching
              </p>
            </div>
            <div className="bg-white p-8 rounded-xl shadow-md">
              <div className="text-blue-600 text-3xl mb-4">📝</div>
              <h3 className="text-xl font-bold text-gray-900 mb-3">Smart Editor</h3>
              <p className="text-gray-600">
                Edit your resume with real-time suggestions and improvements
              </p>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // Show authenticated dashboard
  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 py-12 px-4">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="mb-8 flex justify-between items-center">
          <div>
            <h1 className="text-4xl font-bold text-gray-900 mb-2">Resume Buddy</h1>
            <p className="text-gray-600 text-lg">
              Welcome back, {user?.fullName}
            </p>
          </div>
          <div className="flex items-center gap-4">
            <CreditBalance userId={getCurrentUserId()} compact={true} />
            <button
              onClick={logout}
              className="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg font-medium hover:bg-gray-300 transition-colors"
            >
              Sign Out
            </button>
          </div>
        </div>

        {/* Actions */}
        <div className="mb-8 flex justify-between items-center">
          <button
            onClick={() => loadResumes()}
            className="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg font-medium hover:bg-gray-300 transition-colors"
          >
            Refresh
          </button>
          <button
            onClick={() => router.push('/upload')}
            className="px-6 py-3 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 transition-colors shadow-md hover:shadow-lg"
          >
            Upload New Resume
          </button>
        </div>

        {/* Content */}
        {loading ? (
          <div className="flex items-center justify-center py-20">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
          </div>
        ) : error ? (
          <div className="bg-red-50 border border-red-200 rounded-lg p-6">
            <p className="text-red-800 font-medium">{error}</p>
          </div>
        ) : (
          <ResumeList resumes={resumes} onDelete={handleDelete} />
        )}
      </div>
    </div>
  );
}