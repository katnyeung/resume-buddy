'use client';

import Link from 'next/link';

interface LegalCheckboxProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  required?: boolean;
}

export default function LegalCheckbox({ checked, onChange, required = true }: LegalCheckboxProps) {
  return (
    <div className="flex items-start gap-3">
      <input
        id="legal-consent"
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        required={required}
        className="mt-1 h-4 w-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500 cursor-pointer"
      />
      <label htmlFor="legal-consent" className="text-sm text-gray-700 cursor-pointer">
        I agree to the{' '}
        <Link
          href="/legal/terms"
          target="_blank"
          rel="noopener noreferrer"
          className="text-blue-600 hover:text-blue-700 underline"
        >
          Terms of Service
        </Link>
        {' '}and{' '}
        <Link
          href="/legal/privacy"
          target="_blank"
          rel="noopener noreferrer"
          className="text-blue-600 hover:text-blue-700 underline"
        >
          Privacy Notice
        </Link>
        {required && <span className="text-red-500 ml-1">*</span>}
      </label>
    </div>
  );
}
