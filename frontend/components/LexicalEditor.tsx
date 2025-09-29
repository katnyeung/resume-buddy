'use client';

import { useEffect, useState, useCallback } from 'react';
import { LexicalComposer } from '@lexical/react/LexicalComposer';
import { RichTextPlugin } from '@lexical/react/LexicalRichTextPlugin';
import { ContentEditable } from '@lexical/react/LexicalContentEditable';
import { HistoryPlugin } from '@lexical/react/LexicalHistoryPlugin';
import { LexicalErrorBoundary } from '@lexical/react/LexicalErrorBoundary';
import type { EditorState } from 'lexical';
import OnChangePlugin from './plugins/OnChangePlugin';
import AutoFocusPlugin from './plugins/AutoFocusPlugin';
import { getResumeLines, batchUpdateLines } from '@/lib/api';
import { resumeLinesToEditorState, editorStateToResumeLines, detectLineChanges } from '@/lib/lexicalUtils';
import { ResumeLine } from '@/lib/types';

interface LexicalEditorProps {
  resumeId: string;
}

export default function LexicalEditor({ resumeId }: LexicalEditorProps) {
  const [editorState, setEditorState] = useState<EditorState | null>(null);
  const [originalLines, setOriginalLines] = useState<ResumeLine[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saveStatus, setSaveStatus] = useState<string>('');

  // Load resume lines on mount
  useEffect(() => {
    const loadResumeLines = async () => {
      try {
        setLoading(true);
        const lines = await getResumeLines(resumeId);
        setOriginalLines(lines);
      } catch (error) {
        console.error('Failed to load resume lines:', error);
        setSaveStatus('Error loading resume');
      } finally {
        setLoading(false);
      }
    };

    loadResumeLines();
  }, [resumeId]);

  // Handle editor state changes
  const handleEditorChange = useCallback((newEditorState: EditorState) => {
    setEditorState(newEditorState);
  }, []);

  // Save handler
  const handleSave = async () => {
    if (!editorState) {
      setSaveStatus('No changes to save');
      return;
    }

    try {
      setSaving(true);
      setSaveStatus('Saving...');

      // Convert editor state to resume lines
      const currentLines = editorStateToResumeLines(editorState);

      // Detect changes
      const changes = detectLineChanges(originalLines, currentLines);

      if (changes.length === 0) {
        setSaveStatus('No changes detected');
        setSaving(false);
        return;
      }

      // Send batch update
      const response = await batchUpdateLines(resumeId, changes);

      if (response.success) {
        setSaveStatus(`Saved ${response.updatedCount} line(s)`);

        // Reload lines to get updated data
        const updatedLines = await getResumeLines(resumeId);
        setOriginalLines(updatedLines);

        setTimeout(() => setSaveStatus(''), 3000);
      } else {
        setSaveStatus('Save failed');
      }
    } catch (error) {
      console.error('Save error:', error);
      setSaveStatus('Error saving changes');
    } finally {
      setSaving(false);
    }
  };

  // Editor config
  const initialConfig = {
    namespace: 'ResumeEditor',
    editorState: originalLines.length > 0 ? resumeLinesToEditorState(originalLines) : undefined,
    theme: {
      paragraph: 'editor-paragraph',
      text: {
        bold: 'editor-text-bold',
        italic: 'editor-text-italic',
        underline: 'editor-text-underline',
      },
    },
    onError: (error: Error) => {
      console.error('Lexical error:', error);
    },
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="text-lg text-gray-600">Loading resume...</div>
      </div>
    );
  }

  return (
    <div className="w-full max-w-5xl mx-auto">
      {/* Toolbar */}
      <div className="bg-white border border-gray-300 rounded-t-lg p-4 flex items-center justify-between">
        <h2 className="text-xl font-semibold text-gray-800">Resume Editor</h2>
        <div className="flex items-center gap-4">
          {saveStatus && (
            <span className={`text-sm ${saveStatus.includes('Error') || saveStatus.includes('failed') ? 'text-red-600' : 'text-green-600'}`}>
              {saveStatus}
            </span>
          )}
          <button
            onClick={handleSave}
            disabled={saving}
            className="px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
          >
            {saving ? 'Saving...' : 'Save'}
          </button>
        </div>
      </div>

      {/* Lexical Editor */}
      <LexicalComposer initialConfig={initialConfig}>
        <div className="relative bg-white border-x border-b border-gray-300 rounded-b-lg">
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
          <OnChangePlugin onChange={handleEditorChange} />
          <AutoFocusPlugin />
        </div>
      </LexicalComposer>
    </div>
  );
}