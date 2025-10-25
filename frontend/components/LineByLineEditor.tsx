'use client';

import { useState, useEffect } from 'react';

interface LineByLineEditorProps {
  initialContent: string;
  onChange: (content: string) => void;
  disabled?: boolean;
}

export default function LineByLineEditor({ initialContent, onChange, disabled = false }: LineByLineEditorProps) {
  const [lines, setLines] = useState<string[]>([]);

  // Initialize lines from initialContent
  useEffect(() => {
    if (initialContent) {
      const splitLines = initialContent.split('\n').filter(line => line.trim());
      setLines(splitLines.length > 0 ? splitLines : ['']);
    } else {
      setLines(['']);
    }
  }, [initialContent]);

  // Notify parent of changes
  useEffect(() => {
    const content = lines.filter(line => line.trim()).join('\n');
    onChange(content);
  }, [lines, onChange]);

  const handleLineChange = (index: number, value: string) => {
    const newLines = [...lines];
    newLines[index] = value;
    setLines(newLines);
  };

  const handleAddLine = (index: number) => {
    const newLines = [...lines];
    newLines.splice(index + 1, 0, '');
    setLines(newLines);
  };

  const handleRemoveLine = (index: number) => {
    if (lines.length === 1) {
      setLines(['']); // Keep at least one empty line
    } else {
      const newLines = lines.filter((_, i) => i !== index);
      setLines(newLines);
    }
  };

  const handleMoveUp = (index: number) => {
    if (index === 0) return;
    const newLines = [...lines];
    [newLines[index - 1], newLines[index]] = [newLines[index], newLines[index - 1]];
    setLines(newLines);
  };

  const handleMoveDown = (index: number) => {
    if (index === lines.length - 1) return;
    const newLines = [...lines];
    [newLines[index], newLines[index + 1]] = [newLines[index + 1], newLines[index]];
    setLines(newLines);
  };

  return (
    <div className="bg-white border border-gray-300 rounded-lg p-4 max-h-96 overflow-y-auto">
      {lines.map((line, index) => (
        <div key={index} className="flex items-start gap-2 mb-3 group">
          {/* Line number */}
          <div className="flex-shrink-0 w-8 pt-2.5 text-center">
            <span className="text-xs font-mono text-gray-500">{index + 1}</span>
          </div>

          {/* Input field */}
          <textarea
            value={line}
            onChange={(e) => handleLineChange(index, e.target.value)}
            disabled={disabled}
            placeholder="Enter job requirement..."
            rows={2}
            className={`flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-green-500 focus:border-transparent resize-none ${
              disabled ? 'bg-gray-100 cursor-not-allowed' : ''
            }`}
          />

          {/* Action buttons */}
          <div className="flex flex-col gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
            {/* Move up */}
            <button
              onClick={() => handleMoveUp(index)}
              disabled={disabled || index === 0}
              title="Move up"
              className="p-1 text-gray-600 hover:text-blue-600 hover:bg-blue-50 rounded disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 15l7-7 7 7" />
              </svg>
            </button>

            {/* Move down */}
            <button
              onClick={() => handleMoveDown(index)}
              disabled={disabled || index === lines.length - 1}
              title="Move down"
              className="p-1 text-gray-600 hover:text-blue-600 hover:bg-blue-50 rounded disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            </button>

            {/* Add line below */}
            <button
              onClick={() => handleAddLine(index)}
              disabled={disabled}
              title="Add line below"
              className="p-1 text-gray-600 hover:text-green-600 hover:bg-green-50 rounded disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
            </button>

            {/* Remove line */}
            <button
              onClick={() => handleRemoveLine(index)}
              disabled={disabled || lines.length === 1}
              title="Remove line"
              className="p-1 text-gray-600 hover:text-red-600 hover:bg-red-50 rounded disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>
      ))}

      {/* Add first line button if empty */}
      {lines.length === 0 && (
        <button
          onClick={() => setLines([''])}
          disabled={disabled}
          className="w-full py-4 border-2 border-dashed border-gray-300 rounded-lg text-gray-500 hover:border-green-500 hover:text-green-600 transition-colors"
        >
          + Add first requirement
        </button>
      )}
    </div>
  );
}
