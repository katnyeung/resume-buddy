'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import JobAnalysisReport from '@/components/JobAnalysisReport';
import AppHeader from '@/components/AppHeader';
import { useAuth } from '@/hooks/useAuth';

export default function AnalysisPage() {
  const params = useParams();
  const router = useRouter();
  const [analysis, setAnalysis] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { isAuthenticated, isLoading: authLoading } = useAuth();

  useEffect(() => {
    const fetchAnalysis = async () => {
      try {
        const token = localStorage.getItem('authToken');
        const headers: HeadersInit = {
          'Content-Type': 'application/json'
        };

        if (token) {
          headers['Authorization'] = `Bearer ${token}`;
        }

        const apiUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
        const response = await fetch(
          `${apiUrl}/resumes/analysis/${params.analysisId}`,
          { headers }
        );

        if (!response.ok) {
          throw new Error('Analysis not found');
        }

        const data = await response.json();
        setAnalysis(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load analysis');
      } finally {
        setLoading(false);
      }
    };

    fetchAnalysis();
  }, [params.analysisId]);

  if (authLoading || loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">
            {authLoading ? 'Verifying authentication...' : 'Loading analysis...'}
          </p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return null; // Will redirect via useAuth hook
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <p className="text-red-600 mb-4">{error}</p>
          <button
            onClick={() => router.back()}
            className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
          >
            Go Back
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <AppHeader title="Job Analysis Report" showBackButton={true} />

      <div className="max-w-7xl mx-auto px-4 py-8">
        <JobAnalysisReport analysis={analysis} />
      </div>
    </div>
  );
}
