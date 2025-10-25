'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import AppHeader from '@/components/AppHeader';
import React from 'react';

export default function PrivacyNoticePage() {
  const [content, setContent] = useState<string>('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch('/legal/privacy_notice.md')
      .then(res => res.text())
      .then(text => {
        setContent(text);
        setLoading(false);
      })
      .catch(err => {
        console.error('Failed to load privacy notice:', err);
        setLoading(false);
      });
  }, []);

  // Simple markdown-to-HTML converter for headings and lists
  const renderMarkdown = (markdown: string) => {
    const lines = markdown.split('\n');
    const elements: React.ReactElement[] = [];
    let currentList: string[] = [];
    let listKey = 0;

    const flushList = () => {
      if (currentList.length > 0) {
        elements.push(
          <ul key={`list-${listKey++}`} className="list-disc list-inside space-y-1 mb-4">
            {currentList.map((item, i) => (
              <li key={i} className="text-gray-700">{item}</li>
            ))}
          </ul>
        );
        currentList = [];
      }
    };

    lines.forEach((line, index) => {
      // Headings
      if (line.startsWith('# ')) {
        flushList();
        elements.push(<h1 key={index} className="text-3xl font-bold text-gray-900 mb-4">{line.slice(2)}</h1>);
      } else if (line.startsWith('## ')) {
        flushList();
        elements.push(<h2 key={index} className="text-2xl font-semibold text-gray-800 mt-8 mb-3">{line.slice(3)}</h2>);
      } else if (line.startsWith('### ')) {
        flushList();
        elements.push(<h3 key={index} className="text-xl font-semibold text-gray-800 mt-6 mb-2">{line.slice(4)}</h3>);
      }
      // List items
      else if (line.startsWith('- ')) {
        currentList.push(line.slice(2));
      }
      // Bold text in paragraphs
      else if (line.trim().startsWith('**') && line.includes(':**')) {
        flushList();
        const match = line.match(/\*\*(.*?)\*\*: (.*)/);
        if (match) {
          elements.push(
            <p key={index} className="mb-2">
              <strong className="font-semibold text-gray-900">{match[1]}:</strong>
              <span className="text-gray-700"> {match[2]}</span>
            </p>
          );
        }
      }
      // Regular paragraphs
      else if (line.trim() !== '') {
        flushList();
        // Handle inline bold
        const formatted = line.replace(/\*\*(.*?)\*\*/g, '<strong class="font-semibold">$1</strong>');
        elements.push(
          <p key={index} className="text-gray-700 mb-4" dangerouslySetInnerHTML={{ __html: formatted }} />
        );
      }
      // Empty lines
      else {
        flushList();
      }
    });

    flushList();
    return elements;
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <AppHeader />

      <div className="max-w-4xl mx-auto px-4 py-12">
        {/* Back navigation */}
        <div className="mb-6">
          <Link
            href="/"
            className="text-blue-600 hover:text-blue-700 flex items-center gap-2"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
            Back to Home
          </Link>
        </div>

        {/* Content */}
        <div className="bg-white rounded-lg shadow-sm p-8 md:p-12">
          {loading ? (
            <div className="flex justify-center items-center py-20">
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
            </div>
          ) : (
            <div className="prose prose-lg max-w-none">
              {renderMarkdown(content)}
            </div>
          )}
        </div>

        {/* Print button */}
        <div className="mt-6 flex justify-end">
          <button
            onClick={() => window.print()}
            className="px-4 py-2 text-sm text-gray-600 hover:text-gray-900 flex items-center gap-2"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z" />
            </svg>
            Print
          </button>
        </div>
      </div>

      <style jsx global>{`
        @media print {
          header, button, nav {
            display: none !important;
          }
          .bg-white {
            box-shadow: none !important;
          }
        }
      `}</style>
    </div>
  );
}
