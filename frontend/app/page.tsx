'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import ResumeList from '@/components/ResumeList';
import { listResumes, deleteResume } from '@/lib/api';
import { Resume } from '@/lib/types';

export default function HomePage() {
  const router = useRouter();
  const [resumes, setResumes] = useState<Resume[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadResumes();
  }, []);

  const loadResumes = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await listResumes();
      setResumes(data);
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

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 py-12 px-4">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-4xl font-bold text-gray-900 mb-2">Resume Buddy</h1>
          <p className="text-gray-600 text-lg">
            AI-powered resume enhancement platform
          </p>
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