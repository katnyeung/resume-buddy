'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/hooks/useAuth';
import { createCheckoutSession, getUserCredits } from '@/lib/api';
import { getCurrentUserId } from '@/lib/userUtils';
import AppHeader from '@/components/AppHeader';

interface CreditPackage {
  id: string;
  credits: number;
  price: number;
  popular?: boolean;
}

const packages: CreditPackage[] = [
  { id: '200', credits: 200, price: 2 },
  { id: '500', credits: 500, price: 5, popular: true },
  { id: '1000', credits: 1000, price: 8 }
];

export default function CreditsPage() {
  useAuth(); // Auth guard
  const router = useRouter();
  const [loading, setLoading] = useState<string | null>(null);
  const [credits, setCredits] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadCredits();
  }, []);

  const loadCredits = async () => {
    try {
      const data = await getUserCredits(getCurrentUserId());
      setCredits(data);
    } catch (err: any) {
      console.error('Failed to load credits:', err);
    }
  };

  const handlePurchase = async (packageId: string) => {
    try {
      setLoading(packageId);
      setError(null);

      const response = await createCheckoutSession(packageId);

      // Redirect to Stripe Checkout
      if (response.checkoutUrl) {
        window.location.href = response.checkoutUrl;
      } else {
        throw new Error('No checkout URL received');
      }
    } catch (err: any) {
      console.error('Failed to create checkout session:', err);
      setError(err.message || 'Failed to start payment process. Please try again.');
      setLoading(null);
    }
  };

  const available = credits ? parseFloat(credits.availableCredits || 0) : 0;

  return (
    <div className="min-h-screen bg-gray-50">
      <AppHeader title="Buy Credits" showBackButton />

      <div className="max-w-6xl mx-auto px-4 py-8">
        {/* Current Balance */}
        <div className="bg-white rounded-lg shadow-sm p-6 mb-8">
          <h2 className="text-lg font-semibold text-gray-900 mb-2">Current Balance</h2>
          {credits ? (
            <div className="flex items-baseline gap-2">
              <span className="text-4xl font-bold text-blue-600">{available.toFixed(0)}</span>
              <span className="text-gray-600">credits available</span>
            </div>
          ) : (
            <div className="animate-pulse bg-gray-200 h-12 w-48 rounded"></div>
          )}
          <p className="text-sm text-gray-500 mt-2">
            Total: {credits ? parseFloat(credits.totalCredits || 0).toFixed(0) : '...'} |
            Used: {credits ? parseFloat(credits.usedCredits || 0).toFixed(0) : '...'}
          </p>
        </div>

        {/* Error Message */}
        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg mb-6">
            {error}
          </div>
        )}

        {/* Credit Packages */}
        <h2 className="text-2xl font-bold text-gray-900 mb-6">Choose a Package</h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {packages.map((pkg) => (
            <div
              key={pkg.id}
              className={`relative bg-white rounded-lg shadow-sm border-2 p-6 transition-all hover:shadow-md ${
                pkg.popular ? 'border-blue-500' : 'border-gray-200'
              }`}
            >
              {pkg.popular && (
                <div className="absolute -top-3 left-1/2 transform -translate-x-1/2">
                  <span className="bg-blue-500 text-white text-xs font-semibold px-3 py-1 rounded-full">
                    BEST VALUE
                  </span>
                </div>
              )}

              <div className="text-center mb-6">
                <div className="text-4xl font-bold text-gray-900 mb-2">
                  {pkg.credits}
                </div>
                <div className="text-gray-600">credits</div>
              </div>

              <div className="text-center mb-6">
                <div className="text-3xl font-bold text-blue-600">
                  £{pkg.price}
                </div>
                <div className="text-sm text-gray-500 mt-1">
                  £{(pkg.price / pkg.credits).toFixed(3)} per credit
                </div>
              </div>

              <div className="space-y-2 mb-6 text-sm text-gray-600">
                <div className="flex items-center gap-2">
                  <svg className="w-5 h-5 text-green-500" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                  </svg>
                  <span>~{Math.floor(pkg.credits / 50)} resume analyses</span>
                </div>
                <div className="flex items-center gap-2">
                  <svg className="w-5 h-5 text-green-500" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                  </svg>
                  <span>~{Math.floor(pkg.credits / 50)} job analyses</span>
                </div>
                <div className="flex items-center gap-2">
                  <svg className="w-5 h-5 text-green-500" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                  </svg>
                  <span>Never expires</span>
                </div>
              </div>

              <button
                onClick={() => handlePurchase(pkg.id)}
                disabled={loading !== null}
                className={`w-full py-3 px-4 rounded-lg font-semibold transition-colors ${
                  pkg.popular
                    ? 'bg-blue-600 text-white hover:bg-blue-700'
                    : 'bg-gray-100 text-gray-900 hover:bg-gray-200'
                } disabled:opacity-50 disabled:cursor-not-allowed`}
              >
                {loading === pkg.id ? (
                  <span className="flex items-center justify-center gap-2">
                    <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                    </svg>
                    Processing...
                  </span>
                ) : (
                  'Buy Now'
                )}
              </button>
            </div>
          ))}
        </div>

        {/* FAQ Section */}
        <div className="mt-12 bg-white rounded-lg shadow-sm p-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">Frequently Asked Questions</h3>
          <div className="space-y-4 text-sm">
            <div>
              <p className="font-medium text-gray-900">How do credits work?</p>
              <p className="text-gray-600 mt-1">
                Each analysis costs a certain number of credits: Resume analysis (50 credits),
                Job experience analysis (50 credits), Job profile generation (25 credits).
              </p>
            </div>
            <div>
              <p className="font-medium text-gray-900">Do credits expire?</p>
              <p className="text-gray-600 mt-1">
                No, credits never expire. Use them whenever you need.
              </p>
            </div>
            <div>
              <p className="font-medium text-gray-900">Is payment secure?</p>
              <p className="text-gray-600 mt-1">
                Yes, all payments are processed securely through Stripe. We never store your credit card information.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
