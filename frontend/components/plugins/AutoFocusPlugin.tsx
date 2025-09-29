'use client';

import { useLexicalComposerContext } from '@lexical/react/LexicalComposerContext';
import { useEffect } from 'react';
import { $getRoot, $getSelection, $setSelection } from 'lexical';

export default function AutoFocusPlugin() {
  const [editor] = useLexicalComposerContext();

  useEffect(() => {
    editor.update(() => {
      const root = $getRoot();
      if (root) {
        // Select the beginning of the document
        root.selectStart();
      }
    });

    // Focus without scrolling
    editor.focus(() => {}, { defaultSelection: 'rootStart' });
  }, [editor]);

  return null;
}