import { $getRoot, EditorState, createEditor } from 'lexical';
import { $convertFromMarkdownString, TRANSFORMERS } from '@lexical/markdown';
import { HeadingNode, QuoteNode } from '@lexical/rich-text';
import { ListNode, ListItemNode } from '@lexical/list';
import { CodeNode } from '@lexical/code';
import { LinkNode, AutoLinkNode } from '@lexical/link';
import { ResumeLine, ResumeLineUpdateDto } from './types';

/**
 * Convert resume lines from backend to Lexical EditorState JSON
 * Parses markdown syntax (##, **, etc.) into proper Lexical nodes
 */
export function resumeLinesToEditorState(lines: ResumeLine[]): string {
  // Join all lines with newlines, preserving empty lines
  // Empty lines need a space or special marker to prevent markdown from collapsing them
  const markdownText = lines.map(line => {
    const content = line.content || '';
    // If line is empty or only whitespace, use a non-breaking space to preserve it
    return content.trim() === '' ? '\u00A0' : content;
  }).join('\n');

  // Create a temporary headless editor to parse the markdown
  const tempEditor = createEditor({
    nodes: [HeadingNode, QuoteNode, ListNode, ListItemNode, CodeNode, LinkNode, AutoLinkNode],
    onError: (error) => console.error('Temp editor error:', error),
  });

  let editorStateJSON: string = '';

  // Use the editor to convert markdown to EditorState
  tempEditor.update(() => {
    $convertFromMarkdownString(markdownText, TRANSFORMERS);
  }, { discrete: true });

  // Get the resulting editor state as JSON
  editorStateJSON = JSON.stringify(tempEditor.getEditorState().toJSON());

  return editorStateJSON;
}

/**
 * Convert Lexical EditorState to resume line updates
 */
export function editorStateToResumeLines(editorState: EditorState): ResumeLineUpdateDto[] {
  let lines: ResumeLineUpdateDto[] = [];

  editorState.read(() => {
    const root = $getRoot();
    const children = root.getChildren();

    children.forEach((child, index) => {
      let content = '';

      // Extract text content from all node types (paragraph, heading, list, etc.)
      content = child.getTextContent();

      lines.push({
        lineNumber: index + 1,
        content: content,
      });
    });
  });

  return lines;
}

/**
 * Detect changes between original lines and current editor state
 */
export function detectLineChanges(
  originalLines: ResumeLine[],
  currentLines: ResumeLineUpdateDto[]
): ResumeLineUpdateDto[] {
  const changes: ResumeLineUpdateDto[] = [];

  currentLines.forEach((currentLine, index) => {
    const originalLine = originalLines[index];

    // New line or content changed
    if (!originalLine || originalLine.content !== currentLine.content) {
      changes.push(currentLine);
    }
  });

  return changes;
}