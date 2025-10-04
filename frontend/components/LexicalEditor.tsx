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
import AutoFocusPlugin from './plugins/AutoFocusPlugin';
import ToolbarPlugin from './plugins/ToolbarPlugin';
import { getResumeLines, saveEditorState, getEditorState, analyzeResume, getResume, getStructuredAnalysis, analyzeJob } from '@/lib/api';
import { resumeLinesToEditorState } from '@/lib/lexicalUtils';
import { ResumeLine, ResumeAnalysisDto, JobAnalysisResult } from '@/lib/types';
import AnalysisOverlay from './AnalysisOverlay';
import AnalysisSummary from './AnalysisSummary';
import JobAnalysisResults from './JobAnalysisResults';

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
  const [jobAnalysisResult, setJobAnalysisResult] = useState<JobAnalysisResult | null>(null);
  const [showJobAnalysis, setShowJobAnalysis] = useState(false);

  // Load editor state or resume lines on mount
  useEffect(() => {
    const loadResumeContent = async () => {
      try {
        setLoading(true);
        setIsLoaded(false);

        // Check resume status first
        const resume = await getResume(resumeId);

        // If resume is already analyzed, load the analyzed lines and structured analysis for display
        if (resume.status === 'ANALYZED') {
          const lines = await getResumeLines(resumeId);
          setAnalyzedLines(lines);

          // Load structured analysis summary
          const analysis = await getStructuredAnalysis(resumeId);
          setStructuredAnalysis(analysis);
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

  // Ensure editor scrolls to top after loading
  useEffect(() => {
    if (!loading && initialEditorState) {
      // Small delay to ensure editor is rendered
      setTimeout(() => {
        const editorInput = document.querySelector('.editor-input');
        if (editorInput) {
          editorInput.scrollTop = 0;
        }
      }, 100);
    }
  }, [loading, initialEditorState]);

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
    try {
      setAnalyzing(true);
      setAnalysisStatus('Analyzing with AI...');

      // Call analysis endpoint
      const result = await analyzeResume(resumeId);

      setAnalysisStatus(`Analysis complete! Analyzed ${result.analyzedLines} lines`);

      // Reload lines to get updated analysis data
      const updatedLines = await getResumeLines(resumeId);
      console.log('Analysis result:', result);
      console.log('Updated lines with analysis:', updatedLines);

      // Store analyzed lines for overlay display
      setAnalyzedLines(updatedLines);

      // Load structured analysis summary
      const analysis = await getStructuredAnalysis(resumeId);
      setStructuredAnalysis(analysis);

      setTimeout(() => setAnalysisStatus(''), 5000);
    } catch (error) {
      console.error('Analysis error:', error);
      setAnalysisStatus('Error during analysis');
      setTimeout(() => setAnalysisStatus(''), 3000);
    } finally {
      setAnalyzing(false);
    }
  };

  // Handler for analyzing specific job from ATS summary
  const handleAnalyzeJob = async (experienceId: string) => {
    if (analyzingJob) return;

    try {
      setAnalyzingJob(true);
      setSaveStatus('Analyzing job experience...');

      const result = await analyzeJob(resumeId, experienceId);
      setJobAnalysisResult(result);
      setShowJobAnalysis(true);
      setSaveStatus('Job analysis completed!');
      setTimeout(() => setSaveStatus(''), 3000);

    } catch (error) {
      console.error('Job analysis error:', error);
      setSaveStatus('Error analyzing job');
      setTimeout(() => setSaveStatus(''), 3000);
    } finally {
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
      {/* ATS Analysis Summary - displayed at the very top when available */}
      {structuredAnalysis && (
        <div className="mb-4">
          <AnalysisSummary
            analysis={structuredAnalysis}
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

          {/* Action buttons */}
          <button
            onClick={handleAnalyze}
            disabled={analyzing}
            className="px-5 py-2 bg-purple-600 text-white rounded-md hover:bg-purple-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors flex items-center gap-2"
            title="Analyze resume with AI"
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
              </>
            )}
          </button>

          <button
            onClick={handleSave}
            disabled={saving}
            className="px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
          >
            {saving ? 'Saving...' : 'Save'}
          </button>
        </div>
      </div>

      {/* Analysis Overlay - displayed above editor when analysis is available */}
      {analyzedLines.length > 0 && (
        <div className="bg-white border-x border-gray-300 p-4">
          <AnalysisOverlay lines={analyzedLines} />
        </div>
      )}

      {/* Lexical Editor - key prop forces remount when editorState changes */}
      <LexicalComposer key={resumeId} initialConfig={initialConfig}>
        <div className={`relative bg-white border-x ${analyzedLines.length > 0 ? '' : 'border-t'} border-b border-gray-300 ${analyzedLines.length > 0 ? '' : 'rounded-b-lg'}`}>
          <ToolbarPlugin />
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
          <AutoFocusPlugin />
        </div>
      </LexicalComposer>

      {/* Job Analysis Results Modal */}
      {showJobAnalysis && jobAnalysisResult && (
        <JobAnalysisResults
          analysis={jobAnalysisResult}
          onClose={() => setShowJobAnalysis(false)}
        />
      )}
    </div>
  );
}