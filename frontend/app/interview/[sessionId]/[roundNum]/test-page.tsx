"use client";

import { useParams } from 'next/navigation';

export default function TestInterviewPage() {
  const params = useParams();
  const sessionId = params.sessionId as string;
  const roundNum = params.roundNum as string;

  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold mb-4">Interview Practice Test Page</h1>
      <div className="bg-white p-4 rounded shadow">
        <p><strong>Session ID:</strong> {sessionId}</p>
        <p><strong>Round Number:</strong> {roundNum}</p>
        <p className="mt-4 text-green-600">✓ Route parameters working!</p>
      </div>
    </div>
  );
}
