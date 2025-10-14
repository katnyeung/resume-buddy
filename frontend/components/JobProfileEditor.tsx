'use client';

import { useCallback, useMemo } from 'react';
import { LexicalComposer } from '@lexical/react/LexicalComposer';
import { RichTextPlugin } from '@lexical/react/LexicalRichTextPlugin';
import { ContentEditable } from '@lexical/react/LexicalContentEditable';
import { HistoryPlugin } from '@lexical/react/LexicalHistoryPlugin';
import { LexicalErrorBoundary } from '@lexical/react/LexicalErrorBoundary';
import { ListPlugin } from '@lexical/react/LexicalListPlugin';
import { ListNode, ListItemNode } from '@lexical/list';
import type { EditorState } from 'lexical';
import OnChangePlugin from './plugins/OnChangePlugin';

interface JobProfileEditorProps {
  initialContent: string;
  onChange: (editorState: EditorState) => void;
  disabled?: boolean;
}

export default function JobProfileEditor({ initialContent, onChange, disabled = false }: JobProfileEditorProps) {
  // Convert plain text to Lexical editor state
  const convertTextToEditorState = (text: string): string => {
    const lines = text.split('\n').filter(line => line.trim());

    // Create a simple paragraph-based editor state
    const children = lines.map(line => ({
      type: 'paragraph',
      version: 1,
      children: [{
        type: 'text',
        version: 1,
        text: line,
        format: 0,
        style: '',
        mode: 'normal',
        detail: 0
      }]
    }));

    return JSON.stringify({
      root: {
        type: 'root',
        version: 1,
        children: children,
        format: '',
        indent: 0,
        direction: null
      }
    });
  };

  const handleEditorChange = useCallback((newEditorState: EditorState) => {
    onChange(newEditorState);
  }, [onChange]);

  // Editor config
  const initialConfig = useMemo(() => ({
    namespace: 'JobProfileEditor',
    editorState: initialContent ? convertTextToEditorState(initialContent) : undefined,
    nodes: [
      ListNode,
      ListItemNode,
    ],
    theme: {
      paragraph: 'mb-2 text-sm',
      list: {
        ul: 'list-disc ml-6 mb-2',
        ol: 'list-decimal ml-6 mb-2',
        listitem: 'mb-1',
      },
      text: {
        bold: 'font-bold',
        italic: 'italic',
        underline: 'underline',
      },
    },
    editable: !disabled,
    onError: (error: Error) => {
      console.error('Lexical error:', error);
    },
  }), [initialContent, disabled]);

  return (
    <LexicalComposer key={initialContent} initialConfig={initialConfig}>
      <div className="relative bg-white border border-gray-300 rounded-lg">
        <RichTextPlugin
          contentEditable={
            <ContentEditable
              className={`editor-input h-64 overflow-y-auto p-4 focus:outline-none focus:ring-2 focus:ring-green-500 focus:border-transparent rounded-lg ${disabled ? 'bg-gray-100 cursor-not-allowed' : ''}`}
            />
          }
          placeholder={
            <div className="editor-placeholder absolute top-4 left-4 text-gray-400 pointer-events-none text-sm">
              Enter job post content here...
            </div>
          }
          ErrorBoundary={LexicalErrorBoundary}
        />
        <HistoryPlugin />
        <ListPlugin />
        <OnChangePlugin onChange={handleEditorChange} />
      </div>
    </LexicalComposer>
  );
}
