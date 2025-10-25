'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import FileUploader from '@/components/FileUploader';
import AppHeader from '@/components/AppHeader';
import CreditBalance from '@/components/CreditBalance';
import { uploadResume, parseResume, getUserCredits } from '@/lib/api';
import { getCurrentUserId } from '@/lib/userUtils';

const UPLOAD_COST = 50;

export default function UploadPage() {
  const router = useRouter();
  const { user, loading: authLoading } = useAuth();
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [parsing, setParsing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [uploadProgress, setUploadProgress] = useState<string>('');
  const [credits, setCredits] = useState<any>(null);
  const [loadingCredits, setLoadingCredits] = useState(true);

  // Redirect if not authenticated
  useEffect(() => {
    if (!authLoading && !user) {
      router.push('/login');
    }
  }, [authLoading, user, router]);

  // Load user credits
  useEffect(() => {
    if (user) {
      loadCredits();
    }
  }, [user]);

  const loadCredits = async () => {
    try {
      const userCredits = await getUserCredits(getCurrentUserId());
      setCredits(userCredits);
    } catch (err) {
      console.error('Failed to load credits:', err);
    } finally {
      setLoadingCredits(false);
    }
  };

  const handleFileSelect = (selectedFile: File) => {
    setFile(selectedFile);
    setError(null);
  };

  const handleUpload = async () => {
    if (!file) {
      setError('Please select a file first');
      return;
    }

    // Check credits before upload
    if (credits && parseFloat(credits.availableCredits) < UPLOAD_COST) {
      setError(`Insufficient credits. You need ${UPLOAD_COST} credits to upload a resume. Available: ${parseFloat(credits.availableCredits).toFixed(0)}`);
      return;
    }

    try {
      setUploading(true);
      setError(null);
      setUploadProgress('Uploading resume...');

      // Upload file
      const uploadedResume = await uploadResume(file);
      setUploadProgress('Resume uploaded successfully');

      // Reload credits after upload
      await loadCredits();

      // Parse resume
      setParsing(true);
      setUploadProgress('Parsing resume content...');

      const parsedResume = await parseResume(uploadedResume.id);
      setUploadProgress('Resume parsed successfully');

      // Redirect to resume detail page
      setTimeout(() => {
        router.push(`/resume/${parsedResume.id}`);
      }, 500);

    } catch (err: any) {
      console.error('Upload/parse error:', err);

      // Handle specific error responses
      if (err.response?.status === 402) {
        const errorData = err.response.data;
        setError(errorData.message || 'Insufficient credits to upload resume');
      } else if (err.response?.data?.error) {
        setError(err.response.data.error);
      } else {
        setError('Failed to upload or parse resume. Please try again.');
      }
      setUploadProgress('');
    } finally {
      setUploading(false);
      setParsing(false);
    }
  };

  const isProcessing = uploading || parsing;

  // Show loading if auth is still loading
  if (authLoading || !user) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  const hasEnoughCredits = credits && parseFloat(credits.availableCredits) >= UPLOAD_COST;

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100">
      <AppHeader title="Upload Resume" showBackButton={true} />

      <div className="max-w-3xl mx-auto px-4 py-8">
        <div className="bg-white rounded-xl shadow-lg p-8">
          {/* Header with Credit Info */}
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-gray-900 mb-2">Upload Your Resume</h1>
            <p className="text-gray-600 mb-4">
              Upload your resume to start editing with AI-powered suggestions
            </p>

            {/* Credit Cost Notice */}
            <div className={`p-4 rounded-lg border ${hasEnoughCredits ? 'bg-blue-50 border-blue-200' : 'bg-yellow-50 border-yellow-300'}`}>
              <div className="flex items-center justify-between">
                <div>
                  <p className={`font-medium ${hasEnoughCredits ? 'text-blue-900' : 'text-yellow-900'}`}>
                    Upload Cost: {UPLOAD_COST} credits
                  </p>
                  {!loadingCredits && credits && (
                    <div className={`text-sm mt-1 ${hasEnoughCredits ? 'text-blue-700' : 'text-yellow-700'}`}>
                      <p>Available: {parseFloat(credits.availableCredits || 0).toFixed(0)} credits</p>
                      <p className="text-xs opacity-75">
                        (Total: {parseFloat(credits.totalCredits || 0).toFixed(0)} - Used: {parseFloat(credits.usedCredits || 0).toFixed(0)})
                      </p>
                    </div>
                  )}
                </div>
                {!hasEnoughCredits && !loadingCredits && (
                  <div className="text-yellow-700 font-bold">
                    Insufficient Credits
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* File Uploader */}
          <FileUploader onFileSelect={handleFileSelect} disabled={isProcessing} />

          {/* Progress Message */}
          {uploadProgress && (
            <div className="mt-6 p-4 bg-blue-50 border border-blue-200 rounded-lg">
              <p className="text-blue-800 font-medium">{uploadProgress}</p>
            </div>
          )}

          {/* Error Message */}
          {error && (
            <div className="mt-6 p-4 bg-red-50 border border-red-200 rounded-lg">
              <p className="text-red-800 font-medium">{error}</p>
            </div>
          )}

          {/* Action Buttons */}
          <div className="mt-8 flex gap-4">
            <button
              onClick={handleUpload}
              disabled={!file || isProcessing}
              className="flex-1 px-6 py-3 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
            >
              {isProcessing ? 'Processing...' : 'Upload & Parse Resume'}
            </button>

            <button
              onClick={() => router.push('/')}
              disabled={isProcessing}
              className="px-6 py-3 bg-gray-200 text-gray-700 rounded-lg font-medium hover:bg-gray-300 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              Cancel
            </button>
          </div>

          {/* Processing Indicator */}
          {isProcessing && (
            <div className="mt-6 flex items-center justify-center">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}