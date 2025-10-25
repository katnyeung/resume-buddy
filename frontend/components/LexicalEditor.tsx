'use client';

import { useEffect, useState, useCallback, useMemo } from 'react';
import { LexicalComposer } from '@lexical/react/LexicalComposer';
import { RichTextPlugin } from '@lexical/react/LexicalRichTextPlugin';
import { ContentEditable } from '@lexical/react/LexicalContentEditable';
import { HistoryPlugin } from '@lexical/react/LexicalHistoryPlugin';
import { LexicalErrorBoundary } from '@lexical/react/LexicalErrorBoundary';
import { HeadingNode, QuoteNode } from '@lexical/rich-text';
import { ListPlugin } from '@lexical/react/LexicalListPlugin';
import { ListNode, ListItemNode } from '@lexical/list';
import { LinkPlugin } from '@lexical/react/LexicalLinkPlugin';
import { LinkNode, AutoLinkNode } from '@lexical/link';
import { CodeNode } from '@lexical/code';
import { MarkdownShortcutPlugin } from '@lexical/react/LexicalMarkdownShortcutPlugin';
import { TRANSFORMERS } from '@lexical/markdown';
import type { EditorState } from 'lexical';
import OnChangePlugin from './plugins/OnChangePlugin';
import ToolbarPlugin from './plugins/ToolbarPlugin';
import { getResumeLines, saveEditorState, getEditorState, analyzeResume, analyzeResumeAsync, getResume, getStructuredAnalysis, analyzeJob, analyzeJobAsync, checkActiveJobForResume } from '@/lib/api';
import { resumeLinesToEditorState } from '@/lib/lexicalUtils';
import { getCurrentUserId } from '@/lib/userUtils';
import { ResumeLine, ResumeAnalysisDto, ATSReport } from '@/lib/types';
import AnalysisOverlay from './AnalysisOverlay';
import AnalysisSummary from './AnalysisSummary';
import ATSQualityReport from './ATSQualityReport';
import JobStatusIndicator from './JobStatusIndicator';

interface LexicalEditorProps {
  resumeId: string;
}

export default function LexicalEditor({ resumeId }: LexicalEditorProps) {
  const [editorState, setEditorState] = useState<EditorState | null>(null);
  const [initialEditorState, setInitialEditorState] = useState<string | null>(null);
  const [isLoaded, setIsLoaded] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saveStatus, setSaveStatus] = useState<string>('');
  const [analyzing, setAnalyzing] = useState(false);
  const [analysisStatus, setAnalysisStatus] = useState<string>('');
  const [analyzedLines, setAnalyzedLines] = useState<ResumeLine[]>([]);
  const [structuredAnalysis, setStructuredAnalysis] = useState<ResumeAnalysisDto | null>(null);
  const [analyzingJob, setAnalyzingJob] = useState(false);
  const [atsReport, setATSReport] = useState<ATSReport | null>(null);
  const [resumeAnalysisJobId, setResumeAnalysisJobId] = useState<string | null>(null);
  const [jobAnalysisJobId, setJobAnalysisJobId] = useState<string | null>(null);

  // Load editor state or resume lines on mount
  useEffect(() => {
    const loadResumeContent = async () => {
      try {
        setLoading(true);
        setIsLoaded(false);

        // Check if there's an active resume analysis job
        const activeJobId = await checkActiveJobForResume(resumeId);
        if (activeJobId) {
          console.log('Found active resume analysis job:', activeJobId);
          setResumeAnalysisJobId(activeJobId);
          setAnalyzing(true);
          setAnalysisStatus('Resuming active analysis job...');
        }

        // Check resume status first
        const resume = await getResume(resumeId);

        // If resume is already analyzed, load the analyzed lines and structured analysis for display
        if (resume.status === 'ANALYZED') {
          const lines = await getResumeLines(resumeId);
          setAnalyzedLines(lines);

          // Load structured analysis summary
          const analysis = await getStructuredAnalysis(resumeId);
          setStructuredAnalysis(analysis);

          // Load ATS report if available
          if (resume.atsReport) {
            try {
              const parsedReport = JSON.parse(resume.atsReport);
              setATSReport(parsedReport);
            } catch (e) {
              console.error('Failed to parse ATS report', e);
            }
          }
        }

        // First try to load the saved editor state
        const savedEditorState = await getEditorState(resumeId);

        if (savedEditorState) {
          // If editor state exists, use it directly
          setInitialEditorState(savedEditorState);
        } else {
          // Fall back to loading lines if no editor state
          const lines = await getResumeLines(resumeId);
          if (lines.length > 0) {
            setInitialEditorState(resumeLinesToEditorState(lines));
          } else {
            // No content at all, start with empty editor
            setInitialEditorState(null);
          }
        }

        setIsLoaded(true);
      } catch (error) {
        console.error('Failed to load resume content:', error);
        setSaveStatus('Error loading resume');
        setIsLoaded(true);
      } finally {
        setLoading(false);
      }
    };

    loadResumeContent();
  }, [resumeId]);

  // Handle editor state changes
  const handleEditorChange = useCallback((newEditorState: EditorState) => {
    setEditorState(newEditorState);
  }, []);

  // Save handler - saves full Lexical editor state as JSON
  const handleSave = async () => {
    if (!editorState) {
      setSaveStatus('No changes to save');
      return;
    }

    try {
      setSaving(true);
      setSaveStatus('Saving...');

      // Serialize the full editor state to JSON (preserves all formatting)
      const editorStateJSON = JSON.stringify(editorState.toJSON());

      // Save to backend
      await saveEditorState(resumeId, editorStateJSON);

      setSaveStatus('Saved successfully!');
      setTimeout(() => setSaveStatus(''), 3000);
    } catch (error) {
      console.error('Save error:', error);
      setSaveStatus('Error saving changes');
    } finally {
      setSaving(false);
    }
  };

  // Analyze handler - triggers AI analysis
  const handleAnalyze = async () => {
    // If already analyzed, show warning about data loss
    if (structuredAnalysis) {
      const confirmed = window.confirm(
        '⚠️ Warning: Re-analyzing will permanently delete all job analysis data including:\n\n' +
        '• Recruiter evaluations and scores\n' +
        '• O*NET skill mappings and graph relationships\n' +
        '• STARS suggestions and improvement areas\n' +
        '• Missing skills analysis\n\n' +
        'You will need to re-analyze each job experience individually after this.\n\n' +
        'Continue with re-analysis?'
      );

      if (!confirmed) {
        return; // User cancelled
      }
    }

    try {
      setAnalyzing(true);
      setAnalysisStatus('Queueing analysis job...');

      // Call async analysis endpoint (uses job queue)
      const response = await analyzeResumeAsync(resumeId, getCurrentUserId());
      setResumeAnalysisJobId(response.jobId);
      setAnalysisStatus(`Analysis queued (Position: ${response.queuePosition})`);

      console.log('Analysis job queued:', response);

    } catch (error: any) {
      console.error('Analysis error:', error);
      if (error.response?.status === 402) {
        setAnalysisStatus('Insufficient credits! Need 50 credits for resume analysis.');
      } else {
        setAnalysisStatus('Error queueing analysis: ' + (error.message || 'Unknown error'));
      }
      setTimeout(() => setAnalysisStatus(''), 5000);
      setAnalyzing(false);
    }
  };

  // Handler for analyzing specific job from ATS summary
  const handleAnalyzeJob = async (experienceId: string) => {
    if (analyzingJob) return;

    try {
      setAnalyzingJob(true);
      setSaveStatus('Queueing job analysis...');

      // Call async job analysis endpoint (uses job queue)
      const response = await analyzeJobAsync(resumeId, experienceId, getCurrentUserId());
      setJobAnalysisJobId(response.jobId);
      setSaveStatus(`Job analysis queued (Job ID: ${response.jobId})`);

      console.log('Job analysis queued:', response);

    } catch (error: any) {
      console.error('Job analysis error:', error);
      if (error.response?.status === 402) {
        setSaveStatus('Insufficient credits! Need 50 credits for job analysis.');
      } else {
        setSaveStatus('Error queueing job analysis');
      }
      setTimeout(() => setSaveStatus(''), 5000);
      setAnalyzingJob(false);
    }
  };

  // Handler for finding similar jobs from ATS summary
  const handleFindJobsForExperience = (experienceId: string) => {
    console.log(`Future feature: Find similar jobs for experience ${experienceId}`);
    // TODO: Implement in Phase 6
  };

  // Editor config - memoized to prevent recreation on every render
  const initialConfig = useMemo(() => ({
    namespace: 'ResumeEditor',
    editorState: initialEditorState || undefined,
    nodes: [
      HeadingNode,
      QuoteNode,
      ListNode,
      ListItemNode,
      LinkNode,
      AutoLinkNode,
      CodeNode,
    ],
    theme: {
      paragraph: 'mb-2',
      heading: {
        h1: 'text-3xl font-bold mb-4',
        h2: 'text-2xl font-bold mb-3',
        h3: 'text-xl font-bold mb-2',
      },
      list: {
        ul: 'list-disc ml-6 mb-2',
        ol: 'list-decimal ml-6 mb-2',
        listitem: 'mb-1',
      },
      text: {
        bold: 'font-bold',
        italic: 'italic',
        underline: 'underline',
        strikethrough: 'line-through',
        code: 'bg-gray-100 px-1 py-0.5 rounded font-mono text-sm',
      },
      link: 'text-blue-600 underline hover:text-blue-800',
      quote: 'border-l-4 border-gray-300 pl-4 italic my-2',
    },
    onError: (error: Error) => {
      console.error('Lexical error:', error);
    },
  }), [initialEditorState]);

  // Don't render editor until we have loaded the initial state
  if (loading || !isLoaded) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="text-lg text-gray-600">Loading resume...</div>
      </div>
    );
  }

  return (
    <div className="w-full max-w-5xl mx-auto">
      {/* Resume Analysis Job Status Indicator */}
      {resumeAnalysisJobId && (
        <div className="mb-4">
          <JobStatusIndicator
            jobId={resumeAnalysisJobId}
            onComplete={async () => {
              console.log('Resume analysis complete!');
              setResumeAnalysisJobId(null);
              setAnalyzing(false);
              setAnalysisStatus('');

              // Reload analysis data
              const updatedLines = await getResumeLines(resumeId);
              setAnalyzedLines(updatedLines);

              const analysis = await getStructuredAnalysis(resumeId);
              setStructuredAnalysis(analysis);

              // Reload resume to get ATS report
              const resume = await getResume(resumeId);
              if (resume.atsReport) {
                try {
                  const parsedReport = JSON.parse(resume.atsReport);
                  setATSReport(parsedReport);
                } catch (e) {
                  console.error('Failed to parse ATS report', e);
                }
              }
            }}
            onError={(error) => {
              console.error('Resume analysis failed:', error);
              setResumeAnalysisJobId(null);
              setAnalyzing(false);
              setAnalysisStatus('');
              alert('Analysis failed: ' + error);
            }}
          />
        </div>
      )}

      {/* Job Analysis Job Status Indicator */}
      {jobAnalysisJobId && (
        <div className="mb-4">
          <JobStatusIndicator
            jobId={jobAnalysisJobId}
            onComplete={async () => {
              console.log('Job analysis complete!');
              setJobAnalysisJobId(null);
              setAnalyzingJob(false);
              setSaveStatus('Job analysis completed!');

              // Reload structured analysis to update isAnalyzed flags
              const updatedAnalysis = await getStructuredAnalysis(resumeId);
              setStructuredAnalysis(updatedAnalysis);

              setTimeout(() => setSaveStatus(''), 3000);
            }}
            onError={(error) => {
              console.error('Job analysis failed:', error);
              setJobAnalysisJobId(null);
              setAnalyzingJob(false);
              setSaveStatus('');
              alert('Job analysis failed: ' + error);
            }}
          />
        </div>
      )}

      {/* ATS Analysis Summary - displayed at the very top when available */}
      {structuredAnalysis && (
        <div className="mb-4">
          <AnalysisSummary
            analysis={structuredAnalysis}
            resumeId={resumeId}
            onAnalyzeJob={handleAnalyzeJob}
            onFindJobs={handleFindJobsForExperience}
          />
        </div>
      )}

      {/* Toolbar */}
      <div className="bg-white border border-gray-300 rounded-t-lg p-4 flex items-center justify-between">
        <h2 className="text-xl font-semibold text-gray-800">Resume Editor</h2>
        <div className="flex items-center gap-3">
          {/* Status messages */}
          {saveStatus && (
            <span className={`text-sm ${saveStatus.includes('Error') || saveStatus.includes('failed') ? 'text-red-600' : 'text-green-600'}`}>
              {saveStatus}
            </span>
          )}
          {analysisStatus && (
            <span className={`text-sm ${analysisStatus.includes('Error') ? 'text-red-600' : 'text-blue-600'}`}>
              {analysisStatus}
            </span>
          )}

          {/* Analyze/Re-Analyze button */}
          {structuredAnalysis ? (
            // Re-Analyze button (when already analyzed)
            <div className="flex flex-col items-end gap-0.5">
              <button
                onClick={handleAnalyze}
                disabled={analyzing}
                className="px-5 py-2 bg-purple-600 text-white rounded-md hover:bg-purple-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors flex items-center gap-2"
                title="Re-analyze the ATS detail and format (costs 50 credits)"
              >
                {analyzing ? (
                  <>
                    <svg className="animate-spin h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                    Analyzing...
                  </>
                ) : (
                  <>
                    <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                    </svg>
                    Re-Analyze
                    <span className="ml-1 px-1.5 py-0.5 bg-purple-500 rounded text-xs font-medium">
                      -50
                    </span>
                  </>
                )}
              </button>
              <span className="text-[10px] text-gray-500">
                Costs 50 credits • Re-analyze ATS details
              </span>
            </div>
          ) : (
            // Analyze button (first time)
            <div className="flex flex-col items-end gap-0.5">
              <button
                onClick={handleAnalyze}
                disabled={analyzing}
                className="px-5 py-2 bg-purple-600 text-white rounded-md hover:bg-purple-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors flex items-center gap-2"
                title="Analyze resume with AI (costs 50 credits)"
              >
                {analyzing ? (
                  <>
                    <svg className="animate-spin h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                    Analyzing...
                  </>
                ) : (
                  <>
                    <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                    </svg>
                    Analyze
                    <span className="ml-1 px-1.5 py-0.5 bg-purple-500 rounded text-xs font-medium">
                      -50
                    </span>
                  </>
                )}
              </button>
              <span className="text-[10px] text-gray-500">
                Costs 50 credits • AI-powered ATS analysis
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Analysis Overlay - displayed above editor when analysis is available */}
      {analyzedLines.length > 0 && (
        <div className="bg-white border-x border-gray-300 p-4">
          <AnalysisOverlay lines={analyzedLines} />
        </div>
      )}

      {/* ATS Quality Report - displayed below analysis overlay */}
      {atsReport && (
        <div className="bg-white border-x border-gray-300 p-4">
          <ATSQualityReport report={atsReport} />
        </div>
      )}

      {/* Lexical Editor - key prop forces remount when editorState changes */}
      <LexicalComposer key={resumeId} initialConfig={initialConfig}>
        <div className={`relative bg-white border-x ${analyzedLines.length > 0 ? '' : 'border-t'} border-b ${analyzedLines.length > 0 ? '' : 'rounded-b-lg'}`}>
          {/* Raw Text Notice - Only show before first analysis */}
          {!structuredAnalysis && (
            <div className="bg-blue-50 border-b border-blue-200 px-4 py-2.5">
              <div className="flex items-start gap-2">
                <svg className="w-5 h-5 text-blue-600 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <div className="text-sm text-blue-900">
                  <strong className="font-semibold">Raw Parsed Text:</strong> This is the text extracted directly from your PDF by our parser.
                  You may see formatting issues (dates/locations misaligned, line breaks in wrong places).
                  <strong className="font-semibold ml-1">Feel free to edit and fix any errors before clicking "Analyze".</strong>
                </div>
              </div>
            </div>
          )}
          <ToolbarPlugin onSave={handleSave} saving={saving} />
          <RichTextPlugin
            contentEditable={
              <ContentEditable className="editor-input min-h-[500px] max-h-[800px] overflow-y-auto p-8 focus:outline-none" />
            }
            placeholder={
              <div className="editor-placeholder absolute top-8 left-8 text-gray-400 pointer-events-none">
                Start editing your resume...
              </div>
            }
            ErrorBoundary={LexicalErrorBoundary}
          />
          <HistoryPlugin />
          <ListPlugin />
          <LinkPlugin />
          <MarkdownShortcutPlugin transformers={TRANSFORMERS} />
          <OnChangePlugin onChange={handleEditorChange} />
        </div>
      </LexicalComposer>
    </div>
  );
}