import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Interview Practice - Resume Buddy",
  description: "AI-powered interview practice with real-time feedback",
};

export default function InterviewLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <>
      {/* No footer for interview page - need full screen space */}
      {children}
    </>
  );
}
