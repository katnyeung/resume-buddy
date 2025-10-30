"use client";

import React, { useRef, useEffect } from 'react';

// Message types matching backend
export type ChatMessage =
  | { type: 'question'; text: string; canRegenerate?: boolean; isRegenerated?: boolean }
  | { type: 'transcript'; text: string; isPartial?: boolean }
  | { type: 'followup'; feedback: string; question: string; satisfactionLevel: number }
  | { type: 'evaluation'; score: number; feedback: string }
  | { type: 'interrupt'; interruptType: string; reason: string; text: string }
  | { type: 'completion'; message: string };

interface InterviewChatProps {
  messages: ChatMessage[];
  onRegenerate?: () => void;
  regenerating?: boolean;
}

export default function InterviewChat({
  messages,
  onRegenerate,
  regenerating = false
}: InterviewChatProps) {
  const chatEndRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to bottom when new messages arrive
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const renderMessage = (message: ChatMessage, index: number) => {
    switch (message.type) {
      case 'question':
        return (
          <div key={index} className="flex gap-3 items-start mb-4">
            <div className="flex-shrink-0 w-8 h-8 rounded-full bg-blue-500 flex items-center justify-center text-white font-semibold">
              AI
            </div>
            <div className="flex-1">
              <div className="bg-blue-100 border border-blue-300 rounded-lg p-4 max-w-[80%]">
                <p className="text-gray-900 leading-relaxed">{message.text}</p>
                {message.canRegenerate && onRegenerate && (
                  <button
                    onClick={onRegenerate}
                    disabled={regenerating}
                    className="mt-3 flex items-center gap-2 px-3 py-1.5 bg-white border border-blue-400 rounded-md text-sm text-blue-700 hover:bg-blue-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                    </svg>
                    {regenerating ? 'Regenerating...' : '🔄 Regenerate Question'}
                  </button>
                )}
                {message.isRegenerated && (
                  <p className="mt-2 text-xs text-blue-600 italic">✨ Question regenerated</p>
                )}
              </div>
            </div>
          </div>
        );

      case 'transcript':
        return (
          <div key={index} className="flex gap-3 items-start mb-4 justify-end">
            <div className="flex-1">
              <div className="bg-gray-200 border border-gray-300 rounded-lg p-4 max-w-[80%] ml-auto">
                <p className="text-gray-900 leading-relaxed">{message.text}</p>
                {message.isPartial && (
                  <div className="mt-2 flex items-center gap-2 text-xs text-gray-500">
                    <div className="animate-pulse w-2 h-2 bg-gray-500 rounded-full"></div>
                    <span>Listening...</span>
                  </div>
                )}
              </div>
            </div>
            <div className="flex-shrink-0 w-8 h-8 rounded-full bg-gray-500 flex items-center justify-center text-white font-semibold">
              YOU
            </div>
          </div>
        );

      case 'followup':
        return (
          <div key={index} className="flex gap-3 items-start mb-4">
            <div className="flex-shrink-0 w-8 h-8 rounded-full bg-orange-500 flex items-center justify-center text-white font-semibold">
              AI
            </div>
            <div className="flex-1">
              <div className="bg-orange-50 border border-orange-300 rounded-lg p-4 max-w-[80%]">
                <div className="flex items-center gap-2 mb-2">
                  <span className="px-2 py-0.5 bg-orange-200 text-orange-800 text-xs font-semibold rounded">
                    🔄 FOLLOW-UP
                  </span>
                  <span className="text-xs text-gray-600">
                    Score: {message.satisfactionLevel}/100
                  </span>
                </div>
                <p className="text-sm text-gray-700 mb-2">{message.feedback}</p>
                <p className="text-gray-900 font-medium leading-relaxed">{message.question}</p>
              </div>
            </div>
          </div>
        );

      case 'interrupt':
        const interruptColors = {
          gentle: { bg: 'bg-green-50', border: 'border-green-300', badge: 'bg-green-200 text-green-800' },
          moderate: { bg: 'bg-yellow-50', border: 'border-yellow-300', badge: 'bg-yellow-200 text-yellow-800' },
          firm: { bg: 'bg-red-50', border: 'border-red-300', badge: 'bg-red-200 text-red-800' }
        };
        const colors = interruptColors[message.interruptType as keyof typeof interruptColors] || interruptColors.moderate;

        return (
          <div key={index} className="flex gap-3 items-start mb-4">
            <div className="flex-shrink-0 w-8 h-8 rounded-full bg-yellow-500 flex items-center justify-center text-white text-lg">
              🔔
            </div>
            <div className="flex-1">
              <div className={`${colors.bg} border ${colors.border} rounded-lg p-4 max-w-[80%]`}>
                <div className="flex items-center gap-2 mb-2">
                  <span className={`px-2 py-0.5 ${colors.badge} text-xs font-semibold rounded uppercase`}>
                    {message.interruptType} INTERRUPT
                  </span>
                  <span className="text-xs text-gray-600">{message.reason}</span>
                </div>
                <p className="text-gray-900 leading-relaxed">{message.text}</p>
              </div>
            </div>
          </div>
        );

      case 'evaluation':
        const scoreColor =
          message.score >= 80
            ? 'text-green-600 bg-green-50 border-green-300'
            : message.score >= 60
            ? 'text-yellow-600 bg-yellow-50 border-yellow-300'
            : 'text-red-600 bg-red-50 border-red-300';

        return (
          <div key={index} className="flex gap-3 items-start mb-4">
            <div className="flex-shrink-0 w-8 h-8 rounded-full bg-purple-500 flex items-center justify-center text-white text-lg">
              ✅
            </div>
            <div className="flex-1">
              <div className={`${scoreColor} border rounded-lg p-4 max-w-[90%]`}>
                <div className="flex items-center gap-3 mb-3">
                  <span className="px-3 py-1 bg-white border border-gray-300 rounded-lg text-xs font-semibold">
                    FINAL SCORE
                  </span>
                  <span className="text-3xl font-bold">{message.score}/100</span>
                </div>
                <p className="text-gray-900 leading-relaxed whitespace-pre-wrap">{message.feedback}</p>
              </div>
            </div>
          </div>
        );

      case 'completion':
        return (
          <div key={index} className="flex justify-center my-6">
            <div className="bg-gray-100 border border-gray-300 rounded-lg px-6 py-3">
              <p className="text-sm text-gray-700 text-center">{message.message}</p>
            </div>
          </div>
        );

      default:
        return null;
    }
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-gray-50">
      {/* Messages Container */}
      <div className="flex-1 overflow-y-auto p-6">
        {messages.length === 0 ? (
          <div className="flex items-center justify-center h-full">
            <div className="text-center text-gray-500">
              <p className="text-lg font-semibold mb-2">Ready to start!</p>
              <p className="text-sm">Click "Start Interview" to begin</p>
            </div>
          </div>
        ) : (
          <>
            {messages.map((message, index) => renderMessage(message, index))}
            <div ref={chatEndRef} />
          </>
        )}
      </div>
    </div>
  );
}
