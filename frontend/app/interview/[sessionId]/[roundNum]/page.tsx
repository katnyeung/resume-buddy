import InterviewPracticeClient from './InterviewPracticeClient';

export default async function InterviewPage({
  params,
}: {
  params: Promise<{ sessionId: string; roundNum: string }>;
}) {
  // In Next.js 15, params is a Promise that must be awaited
  const { sessionId, roundNum } = await params;

  return (
    <InterviewPracticeClient
      sessionId={sessionId}
      roundNum={parseInt(roundNum)}
    />
  );
}
