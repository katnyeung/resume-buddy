'use client';

import { useParams } from 'next/navigation';
import LexicalEditor from '@/components/LexicalEditor';
import AppHeader from '@/components/AppHeader';

export default function ResumePage() {
  const params = useParams();
  const resumeId = params.id as string;

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