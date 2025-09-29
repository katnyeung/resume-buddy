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
import { getResumeLines, saveEditorState, getEditorState } from '@/lib/api';
import { resumeLinesToEditorState } from '@/lib/lexicalUtils';
import { ResumeLine } from '@/lib/types';

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

  // Load editor state or resume lines on mount
  useEffect(() => {
    const loadResumeContent = async () => {
      try {
        setLoading(true);
        setIsLoaded(false);

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

      {/* Lexical Editor - key prop forces remount when editorState changes */}
      <LexicalComposer key={resumeId} initialConfig={initialConfig}>
        <div className="relative bg-white border-x border-b border-gray-300 rounded-b-lg">
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
    </div>
  );
}