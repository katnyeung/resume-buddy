'use client';

import { useAuth } from '@/contexts/AuthContext';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import CreditBalance from './CreditBalance';
import InfoModal from './InfoModal';
import { getCurrentUserId } from '@/lib/userUtils';
import { getUserSessions } from '@/lib/api/interview';
import Image from 'next/image';

interface AppHeaderProps {
  title?: string;
  showBackButton?: boolean;
}

export default function AppHeader({ title, showBackButton = false }: AppHeaderProps) {
  const { user, logout } = useAuth();
  const router = useRouter();
  const [modalState, setModalState] = useState<{ isOpen: boolean; title: string; message: string }>({
    isOpen: false,
    title: '',
    message: ''
  });

  if (!user) {
    return null;
  }

  const userId = getCurrentUserId();

  // Handle Job Search button click
  const handleJobSearchClick = () => {
    // Navigate to job search index - it will handle profile selection/creation
    router.push('/job-search');
  };

  // Handle Interview button click
  const handleInterviewClick = async () => {
    try {
      const sessions = await getUserSessions(userId);

      if (sessions && sessions.length > 0) {
        // Navigate to interviews history page
        router.push('/interviews');
      } else {
        // No sessions - show modal
        setModalState({
          isOpen: true,
          title: 'No Interview Sessions',
          message: 'Create an Interview session in Job Search first to start practicing.'
        });
      }
    } catch (error) {
      console.error('Failed to check interview sessions:', error);
      // Show modal on error (likely means no sessions)
      setModalState({
        isOpen: true,
        title: 'No Interview Sessions',
        message: 'Create an Interview session in Job Search first to start practicing.'
      });
    }
  };

  const closeModal = () => {
    setModalState({ isOpen: false, title: '', message: '' });
  };

  return (
    <div className="bg-white border-b border-gray-200 shadow-sm">
      <div className="max-w-7xl mx-auto px-4 py-3">
        <div className="flex items-center justify-between">
          {/* Left side - Logo + Title or Back button */}
          <div className="flex items-center gap-4">
            {showBackButton && (
              <button
                onClick={() => router.back()}
                className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
                title="Go back"
              >
                <svg className="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
                </svg>
              </button>
            )}
            <div className="flex items-center gap-3">
              <Image
                src="/logo.png"
                alt="Resume Buddy"
                width={40}
                height={46}
                className="object-contain"
              />
              <div>
                <h1 className="text-xl font-bold text-gray-900">
                  {title || 'Resume Buddy'}
                </h1>
                <p className="text-sm text-gray-600">
                  {user.fullName}
                </p>
              </div>
            </div>
          </div>

          {/* Right side - Credits, Job Search, Interview, Profile, Home, and Sign out */}
          <div className="flex items-center gap-4">
            <CreditBalance userId={getCurrentUserId()} compact={true} />

            <button
              onClick={handleJobSearchClick}
              className="px-3 py-2 text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded-lg transition-colors text-sm font-medium"
              title="Job Search"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 13.255A23.931 23.931 0 0112 15c-3.183 0-6.22-.62-9-1.745M16 6V4a2 2 0 00-2-2h-4a2 2 0 00-2 2v2m4 6h.01M5 20h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
              </svg>
            </button>

            <button
              onClick={handleInterviewClick}
              className="px-3 py-2 text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded-lg transition-colors text-sm font-medium"
              title="Interview Practice"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" />
              </svg>
            </button>

            <button
              onClick={() => router.push('/profile')}
              className="px-3 py-2 text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded-lg transition-colors text-sm font-medium"
              title="View profile"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
              </svg>
            </button>

            <button
              onClick={() => router.push('/')}
              className="px-3 py-2 text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded-lg transition-colors text-sm font-medium"
              title="Go to dashboard"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
              </svg>
            </button>

            <button
              onClick={logout}
              className="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg font-medium hover:bg-gray-300 transition-colors text-sm"
            >
              Sign Out
            </button>
          </div>
        </div>
      </div>

      {/* Info Modal */}
      <InfoModal
        isOpen={modalState.isOpen}
        title={modalState.title}
        message={modalState.message}
        onClose={closeModal}
      />
    </div>
  );
}
