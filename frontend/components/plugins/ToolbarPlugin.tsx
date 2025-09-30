'use client';

import { useLexicalComposerContext } from '@lexical/react/LexicalComposerContext';
import { useCallback, useEffect, useState } from 'react';
import {
  $getSelection,
  $isRangeSelection,
  FORMAT_TEXT_COMMAND,
  SELECTION_CHANGE_COMMAND,
  $createParagraphNode,
} from 'lexical';
import { $isHeadingNode, $createHeadingNode, HeadingTagType } from '@lexical/rich-text';
import { $getNearestNodeOfType, mergeRegister } from '@lexical/utils';
import { $setBlocksType } from '@lexical/selection';
import { INSERT_UNORDERED_LIST_COMMAND, INSERT_ORDERED_LIST_COMMAND, $isListNode } from '@lexical/list';

type BlockType = 'paragraph' | 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6';

export default function ToolbarPlugin() {
  const [editor] = useLexicalComposerContext();
  const [isBold, setIsBold] = useState(false);
  const [isItalic, setIsItalic] = useState(false);
  const [isUnderline, setIsUnderline] = useState(false);
  const [blockType, setBlockType] = useState<BlockType>('paragraph');

  const updateToolbar = useCallback(() => {
    const selection = $getSelection();
    if ($isRangeSelection(selection)) {
      setIsBold(selection.hasFormat('bold'));
      setIsItalic(selection.hasFormat('italic'));
      setIsUnderline(selection.hasFormat('underline'));

      // Get the current block type
      const anchorNode = selection.anchor.getNode();
      const element =
        anchorNode.getKey() === 'root'
          ? anchorNode
          : anchorNode.getTopLevelElementOrThrow();

      const elementKey = element.getKey();
      const elementDOM = editor.getElementByKey(elementKey);

      if (elementDOM !== null) {
        if ($isListNode(element)) {
          setBlockType('paragraph');
        } else {
          const type = element.getType();
          if ($isHeadingNode(element)) {
            const tag = element.getTag();
            setBlockType(tag);
          } else {
            setBlockType('paragraph');
          }
        }
      }
    }
  }, [editor]);

  useEffect(() => {
    return mergeRegister(
      editor.registerUpdateListener(({ editorState }) => {
        editorState.read(() => {
          updateToolbar();
        });
      }),
      editor.registerCommand(
        SELECTION_CHANGE_COMMAND,
        () => {
          updateToolbar();
          return false;
        },
        1
      )
    );
  }, [editor, updateToolbar]);

  const formatBold = () => {
    editor.dispatchCommand(FORMAT_TEXT_COMMAND, 'bold');
  };

  const formatItalic = () => {
    editor.dispatchCommand(FORMAT_TEXT_COMMAND, 'italic');
  };

  const formatUnderline = () => {
    editor.dispatchCommand(FORMAT_TEXT_COMMAND, 'underline');
  };

  const insertBulletList = () => {
    editor.dispatchCommand(INSERT_UNORDERED_LIST_COMMAND, undefined);
  };

  const insertNumberedList = () => {
    editor.dispatchCommand(INSERT_ORDERED_LIST_COMMAND, undefined);
  };

  const formatBlockType = (newBlockType: BlockType) => {
    if (blockType === newBlockType) return;

    editor.update(() => {
      const selection = $getSelection();
      if ($isRangeSelection(selection)) {
        if (newBlockType === 'paragraph') {
          $setBlocksType(selection, () => $createParagraphNode());
        } else {
          $setBlocksType(selection, () => $createHeadingNode(newBlockType as HeadingTagType));
        }
      }
    });
  };

  const buttonClass = (active: boolean) =>
    `px-3 py-1.5 rounded text-sm font-medium transition-colors ${
      active
        ? 'bg-blue-600 text-white'
        : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
    }`;

  const blockTypeLabels: Record<BlockType, string> = {
    paragraph: 'Normal',
    h1: 'Heading 1',
    h2: 'Heading 2',
    h3: 'Heading 3',
    h4: 'Heading 4',
    h5: 'Heading 5',
    h6: 'Heading 6',
  };

  return (
    <div className="flex items-center gap-2 p-2 border-b border-gray-200">
      {/* Block Type Selector */}
      <select
        value={blockType}
        onChange={(e) => formatBlockType(e.target.value as BlockType)}
        className="px-3 py-1.5 rounded text-sm font-medium bg-white border border-gray-300 text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
      >
        {Object.entries(blockTypeLabels).map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </select>
      <div className="w-px h-6 bg-gray-300 mx-1" />
      <button
        onClick={formatBold}
        className={buttonClass(isBold)}
        title="Bold (Ctrl+B)"
        type="button"
      >
        <strong>B</strong>
      </button>
      <button
        onClick={formatItalic}
        className={buttonClass(isItalic)}
        title="Italic (Ctrl+I)"
        type="button"
      >
        <em>I</em>
      </button>
      <button
        onClick={formatUnderline}
        className={buttonClass(isUnderline)}
        title="Underline (Ctrl+U)"
        type="button"
      >
        <u>U</u>
      </button>
      <div className="w-px h-6 bg-gray-300 mx-1" />
      <button
        onClick={insertBulletList}
        className={buttonClass(false)}
        title="Bullet List"
        type="button"
      >
        • List
      </button>
      <button
        onClick={insertNumberedList}
        className={buttonClass(false)}
        title="Numbered List"
        type="button"
      >
        1. List
      </button>
      <div className="w-px h-6 bg-gray-300 mx-1" />
      <div className="text-xs text-gray-500 ml-2">
        Markdown shortcuts: **bold** *italic* # heading
      </div>
    </div>
  );
}
