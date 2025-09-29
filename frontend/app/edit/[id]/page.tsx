'use client';

import { useParams, useRouter } from 'next/navigation';
import LexicalEditor from '@/components/LexicalEditor';

export default function EditPage() {
  const params = useParams();
  const router = useRouter();
  const resumeId = params.id as string;

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 py-8 px-4">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="mb-6 flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold text-gray-900 mb-2">Edit Resume</h1>
            <p className="text-gray-600">Make changes to your resume using the editor below</p>
          </div>
          <button
            onClick={() => router.push('/')}
            className="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg font-medium hover:bg-gray-300 transition-colors"
          >
            Back to List
          </button>
        </div>

        {/* Editor */}
        <LexicalEditor resumeId={resumeId} />
      </div>
    </div>
  );
}