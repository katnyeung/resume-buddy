'use client';

import { useParams } from 'next/navigation';
import LexicalEditor from '@/components/LexicalEditor';
import AppHeader from '@/components/AppHeader';
import { useAuth } from '@/hooks/useAuth';

export default function ResumePage() {
  const params = useParams();
  const resumeId = params.id as string;
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">Verifying authentication...</p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return null; // Will redirect via useAuth hook
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100">
      <AppHeader title="Resume Editor" showBackButton={true} />

      <div className="max-w-7xl mx-auto py-8 px-4">
        {/* Editor */}
        <LexicalEditor resumeId={resumeId} />
      </div>
    </div>
  );
}