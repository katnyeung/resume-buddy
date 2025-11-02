'use client';

import { useState, useEffect, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuth } from '@/hooks/useAuth';
import { verifyPaymentSuccess } from '@/lib/api';
import AppHeader from '@/components/AppHeader';

function PaymentSuccessContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const sessionId = searchParams?.get('session_id');

  const [loading, setLoading] = useState(true);
  const [paymentData, setPaymentData] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (sessionId) {
      pollPaymentStatus();
    } else {
      setError('No session ID provided');
      setLoading(false);
    }
  }, [sessionId]);

  const pollPaymentStatus = async (attempt = 1, maxAttempts = 10) => {
    try {
      setLoading(true);
      const data = await verifyPaymentSuccess(sessionId!);

      // If payment is completed, show success
      if (data.status === 'COMPLETED') {
        setPaymentData(data);
        setLoading(false);
        return;
      }

      // If still pending and we have attempts left, poll again
      if (data.status === 'PENDING' && attempt < maxAttempts) {
        console.log(`Payment still pending, retrying in 3 seconds... (attempt ${attempt}/${maxAttempts})`);
        setTimeout(() => pollPaymentStatus(attempt + 1, maxAttempts), 3000);
        return;
      }

      // If failed or max attempts reached
      setPaymentData(data);
      setLoading(false);

    } catch (err: any) {
      console.error('Failed to verify payment:', err);

      // Retry on error if we have attempts left
      if (attempt < maxAttempts) {
        console.log(`Error checking payment, retrying in 3 seconds... (attempt ${attempt}/${maxAttempts})`);
        setTimeout(() => pollPaymentStatus(attempt + 1, maxAttempts), 3000);
      } else {
        setError(err.message || 'Failed to verify payment. The payment may still be processing. Please check your account or contact support.');
        setLoading(false);
      }
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50">
        <AppHeader title="Payment Processing" showBackButton />
        <div className="max-w-2xl mx-auto px-4 py-16">
          <div className="bg-white rounded-lg shadow-sm p-12 text-center">
            <div className="animate-spin rounded-full h-16 w-16 border-b-2 border-blue-600 mx-auto mb-4"></div>
            <p className="text-gray-600">Verifying your payment...</p>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50">
        <AppHeader title="Payment Error" showBackButton />
        <div className="max-w-2xl mx-auto px-4 py-16">
          <div className="bg-white rounded-lg shadow-sm p-12 text-center">
            <div className="w-16 h-16 bg-red-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg className="w-8 h-8 text-red-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </div>
            <h2 className="text-2xl font-bold text-gray-900 mb-2">Payment Verification Failed</h2>
            <p className="text-gray-600 mb-6">{error}</p>
            <button
              onClick={() => router.push('/credits')}
              className="px-6 py-3 bg-blue-600 text-white rounded-lg font-semibold hover:bg-blue-700 transition-colors"
            >
              Back to Credits
            </button>
          </div>
        </div>
      </div>
    );
  }

  const isCompleted = paymentData?.status === 'COMPLETED';
  const isPending = paymentData?.status === 'PENDING';

  return (
    <div className="min-h-screen bg-gray-50">
      <AppHeader title="Payment Status" showBackButton />
      <div className="max-w-2xl mx-auto px-4 py-16">
        <div className="bg-white rounded-lg shadow-sm p-12 text-center">
          {isCompleted ? (
            <>
              <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
                <svg className="w-8 h-8 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
              </div>
              <h2 className="text-2xl font-bold text-gray-900 mb-2">Payment Successful!</h2>
              <p className="text-gray-600 mb-6">
                Your credits have been added to your account.
              </p>

              <div className="bg-blue-50 border border-blue-200 rounded-lg p-6 mb-6">
                <div className="text-sm text-gray-600 mb-2">Credits Added</div>
                <div className="text-4xl font-bold text-blue-600 mb-1">
                  {parseFloat(paymentData.creditsPurchased || 0).toFixed(0)}
                </div>
                <div className="text-sm text-gray-500">
                  Amount Paid: £{parseFloat(paymentData.amountGbp || 0).toFixed(2)} GBP
                </div>
              </div>

              <div className="flex gap-4 justify-center">
                <button
                  onClick={() => router.push('/')}
                  className="px-6 py-3 bg-blue-600 text-white rounded-lg font-semibold hover:bg-blue-700 transition-colors"
                >
                  Go to Dashboard
                </button>
                <button
                  onClick={() => router.push('/credits')}
                  className="px-6 py-3 bg-gray-200 text-gray-700 rounded-lg font-semibold hover:bg-gray-300 transition-colors"
                >
                  Buy More Credits
                </button>
              </div>
            </>
          ) : isPending ? (
            <>
              <div className="w-16 h-16 bg-yellow-100 rounded-full flex items-center justify-center mx-auto mb-4">
                <svg className="w-8 h-8 text-yellow-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
              <h2 className="text-2xl font-bold text-gray-900 mb-2">Payment Processing</h2>
              <p className="text-gray-600 mb-6">
                Your payment is being processed. Credits will be added to your account shortly.
              </p>
              <button
                onClick={() => pollPaymentStatus(1, 10)}
                className="px-6 py-3 bg-blue-600 text-white rounded-lg font-semibold hover:bg-blue-700 transition-colors"
              >
                Check Status Again
              </button>
            </>
          ) : (
            <>
              <div className="w-16 h-16 bg-gray-100 rounded-full flex items-center justify-center mx-auto mb-4">
                <svg className="w-8 h-8 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
              <h2 className="text-2xl font-bold text-gray-900 mb-2">Unknown Status</h2>
              <p className="text-gray-600 mb-6">
                Payment status: {paymentData?.status || 'Unknown'}
              </p>
              <button
                onClick={() => router.push('/')}
                className="px-6 py-3 bg-blue-600 text-white rounded-lg font-semibold hover:bg-blue-700 transition-colors"
              >
                Go to Dashboard
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

export default function PaymentSuccessPage() {
  useAuth(); // Auth guard

  return (
    <Suspense fallback={
      <div className="min-h-screen bg-gray-50">
        <AppHeader title="Payment Processing" showBackButton />
        <div className="max-w-2xl mx-auto px-4 py-16">
          <div className="bg-white rounded-lg shadow-sm p-12 text-center">
            <div className="animate-spin rounded-full h-16 w-16 border-b-2 border-blue-600 mx-auto mb-4"></div>
            <p className="text-gray-600">Loading payment details...</p>
          </div>
        </div>
      </div>
    }>
      <PaymentSuccessContent />
    </Suspense>
  );
}
