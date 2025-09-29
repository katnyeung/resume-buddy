import { $getRoot, $createParagraphNode, $createTextNode, EditorState } from 'lexical';
import { ResumeLine, ResumeLineUpdateDto } from './types';

/**
 * Convert resume lines from backend to Lexical EditorState JSON
 */
export function resumeLinesToEditorState(lines: ResumeLine[]): string {
  const editorStateJSON = {
    root: {
      children: lines.map(line => ({
        children: line.content
          ? [{
              detail: 0,
              format: 0,
              mode: 'normal',
              style: '',
              text: line.content,
              type: 'text',
              version: 1,
            }]
          : [],
        direction: 'ltr',
        format: '',
        indent: 0,
        type: 'paragraph',
        version: 1,
        textFormat: 0,
      })),
      direction: 'ltr',
      format: '',
      indent: 0,
      type: 'root',
      version: 1,
    },
  };

  return JSON.stringify(editorStateJSON);
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

      if (child.getType() === 'paragraph') {
        content = child.getTextContent();
      }

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