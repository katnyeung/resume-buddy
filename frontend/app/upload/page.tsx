'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import FileUploader from '@/components/FileUploader';
import { uploadResume, parseResume } from '@/lib/api';

export default function UploadPage() {
  const router = useRouter();
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [parsing, setParsing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [uploadProgress, setUploadProgress] = useState<string>('');

  const handleFileSelect = (selectedFile: File) => {
    setFile(selectedFile);
    setError(null);
  };

  const handleUpload = async () => {
    if (!file) {
      setError('Please select a file first');
      return;
    }

    try {
      setUploading(true);
      setError(null);
      setUploadProgress('Uploading resume...');

      // Upload file
      const uploadedResume = await uploadResume(file);
      setUploadProgress('Resume uploaded successfully');

      // Parse resume
      setParsing(true);
      setUploadProgress('Parsing resume content...');

      const parsedResume = await parseResume(uploadedResume.id);
      setUploadProgress('Resume parsed successfully');

      // Redirect to editor
      setTimeout(() => {
        router.push(`/edit/${parsedResume.id}`);
      }, 500);

    } catch (err) {
      console.error('Upload/parse error:', err);
      setError('Failed to upload or parse resume. Please try again.');
      setUploadProgress('');
    } finally {
      setUploading(false);
      setParsing(false);
    }
  };

  const isProcessing = uploading || parsing;

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 py-12 px-4">
      <div className="max-w-3xl mx-auto">
        <div className="bg-white rounded-xl shadow-lg p-8">
          {/* Header */}
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-gray-900 mb-2">Upload Your Resume</h1>
            <p className="text-gray-600">
              Upload your resume to start editing with AI-powered suggestions
            </p>
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