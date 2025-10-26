'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/hooks/useAuth';
import { getUserProfile, updateUserName, deleteAccount, getTransactionHistory } from '@/lib/api';
import { getCurrentUserId } from '@/lib/userUtils';
import AppHeader from '@/components/AppHeader';

export default function ProfilePage() {
  useAuth(); // Auth guard
  const router = useRouter();
  const [profile, setProfile] = useState<any>(null);
  const [transactions, setTransactions] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingName, setEditingName] = useState(false);
  const [newName, setNewName] = useState('');
  const [savingName, setSavingName] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadProfile();
    loadTransactions();
  }, []);

  const loadProfile = async () => {
    try {
      setLoading(true);
      const data = await getUserProfile(getCurrentUserId());
      setProfile(data);
      setNewName(data.fullName || '');
    } catch (err: any) {
      console.error('Failed to load profile:', err);
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const loadTransactions = async () => {
    try {
      const data = await getTransactionHistory(getCurrentUserId());
      setTransactions(data);
    } catch (err: any) {
      console.error('Failed to load transactions:', err);
    }
  };

  const handleSaveName = async () => {
    if (!newName.trim()) {
      setError('Name cannot be empty');
      return;
    }

    try {
      setSavingName(true);
      setError(null);
      await updateUserName(getCurrentUserId(), newName.trim());
      await loadProfile();
      setEditingName(false);
    } catch (err: any) {
      console.error('Failed to update name:', err);
      setError(err.message);
    } finally {
      setSavingName(false);
    }
  };

  const handleDeleteAccount = async () => {
    try {
      setDeleting(true);
      await deleteAccount(getCurrentUserId());
      // Logout and redirect
      localStorage.removeItem('authToken');
      router.push('/login');
    } catch (err: any) {
      console.error('Failed to delete account:', err);
      setError(err.message);
      setDeleting(false);
      setShowDeleteModal(false);
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  const getTransactionIcon = (type: string) => {
    switch (type) {
      case 'PURCHASE':
        return <span className="text-green-600">+</span>;
      case 'USAGE':
        return <span className="text-red-600">-</span>;
      case 'REFUND':
        return <span className="text-blue-600">+</span>;
      case 'ADMIN_ADJUSTMENT':
        return <span className="text-purple-600">*</span>;
      default:
        return null;
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50">
        <AppHeader title="Profile" showBackButton />
        <div className="max-w-4xl mx-auto px-4 py-8">
          <div className="animate-pulse space-y-4">
            <div className="bg-white rounded-lg h-48"></div>
            <div className="bg-white rounded-lg h-96"></div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <AppHeader title="Profile" showBackButton />

      <div className="max-w-4xl mx-auto px-4 py-8">
        {/* Error Message */}
        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg mb-6">
            {error}
          </div>
        )}

        {/* Profile Info Card */}
        <div className="bg-white rounded-lg shadow-sm p-6 mb-6">
          <h2 className="text-xl font-bold text-gray-900 mb-4">Account Information</h2>

          <div className="space-y-4">
            {/* Name */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Full Name</label>
              {editingName ? (
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={newName}
                    onChange={(e) => setNewName(e.target.value)}
                    className="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="Enter your name"
                  />
                  <button
                    onClick={handleSaveName}
                    disabled={savingName}
                    className="px-4 py-2 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 disabled:opacity-50"
                  >
                    {savingName ? 'Saving...' : 'Save'}
                  </button>
                  <button
                    onClick={() => {
                      setEditingName(false);
                      setNewName(profile.fullName || '');
                    }}
                    className="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg font-medium hover:bg-gray-300"
                  >
                    Cancel
                  </button>
                </div>
              ) : (
                <div className="flex items-center justify-between">
                  <span className="text-gray-900">{profile?.fullName || 'Not set'}</span>
                  <button
                    onClick={() => setEditingName(true)}
                    className="text-blue-600 hover:text-blue-700 text-sm font-medium"
                  >
                    Edit
                  </button>
                </div>
              )}
            </div>

            {/* Email (readonly) */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
              <span className="text-gray-900">{profile?.email}</span>
            </div>

            {/* Auth Provider */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Login Method</label>
              <span className="text-gray-900">{profile?.authProvider}</span>
            </div>

            {/* Account Created */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Member Since</label>
              <span className="text-gray-900">
                {profile?.createdAt ? formatDate(profile.createdAt) : 'N/A'}
              </span>
            </div>
          </div>
        </div>

        {/* Credit Summary Card */}
        <div className="bg-white rounded-lg shadow-sm p-6 mb-6">
          <h2 className="text-xl font-bold text-gray-900 mb-4">Credit Balance</h2>
          <div className="grid grid-cols-3 gap-4">
            <div>
              <div className="text-sm text-gray-600 mb-1">Total Credits</div>
              <div className="text-2xl font-bold text-gray-900">
                {parseFloat(profile?.credits?.totalCredits || 0).toFixed(0)}
              </div>
            </div>
            <div>
              <div className="text-sm text-gray-600 mb-1">Used Credits</div>
              <div className="text-2xl font-bold text-red-600">
                {parseFloat(profile?.credits?.usedCredits || 0).toFixed(0)}
              </div>
            </div>
            <div>
              <div className="text-sm text-gray-600 mb-1">Available Credits</div>
              <div className="text-2xl font-bold text-green-600">
                {parseFloat(profile?.credits?.availableCredits || 0).toFixed(0)}
              </div>
            </div>
          </div>
          <button
            onClick={() => router.push('/credits')}
            className="mt-4 w-full py-2 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700"
          >
            Buy More Credits
          </button>
        </div>

        {/* Transaction History */}
        <div className="bg-white rounded-lg shadow-sm p-6 mb-6">
          <h2 className="text-xl font-bold text-gray-900 mb-4">Transaction History</h2>
          {transactions.length === 0 ? (
            <p className="text-gray-500 text-center py-8">No transactions yet</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="border-b border-gray-200">
                  <tr className="text-left text-gray-600">
                    <th className="pb-2">Date</th>
                    <th className="pb-2">Type</th>
                    <th className="pb-2">Description</th>
                    <th className="pb-2 text-right">Amount</th>
                    <th className="pb-2 text-right">Balance After</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {transactions.map((tx) => (
                    <tr key={tx.id} className="hover:bg-gray-50">
                      <td className="py-3 text-gray-600">
                        {formatDate(tx.createdAt)}
                      </td>
                      <td className="py-3">
                        <span className={`inline-flex px-2 py-1 rounded text-xs font-medium ${
                          tx.transactionType === 'PURCHASE' ? 'bg-green-100 text-green-700' :
                          tx.transactionType === 'USAGE' ? 'bg-red-100 text-red-700' :
                          tx.transactionType === 'REFUND' ? 'bg-blue-100 text-blue-700' :
                          'bg-purple-100 text-purple-700'
                        }`}>
                          {tx.transactionType}
                        </span>
                      </td>
                      <td className="py-3 text-gray-900">{tx.description || 'N/A'}</td>
                      <td className="py-3 text-right font-medium">
                        {getTransactionIcon(tx.transactionType)}
                        {Math.abs(parseFloat(tx.amount || 0)).toFixed(0)}
                      </td>
                      <td className="py-3 text-right text-gray-600">
                        {parseFloat(tx.balanceAfter || 0).toFixed(0)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Danger Zone */}
        <div className="bg-white rounded-lg shadow-sm p-6 border-2 border-red-200">
          <h2 className="text-xl font-bold text-red-700 mb-2">Danger Zone</h2>
          <p className="text-gray-600 text-sm mb-4">
            Once you delete your account, there is no going back. All your data, including resumes and credits, will be permanently deleted.
          </p>
          <button
            onClick={() => setShowDeleteModal(true)}
            className="px-4 py-2 bg-red-600 text-white rounded-lg font-medium hover:bg-red-700"
          >
            Delete Account
          </button>
        </div>
      </div>

      {/* Delete Confirmation Modal */}
      {showDeleteModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg max-w-md w-full p-6">
            <h3 className="text-xl font-bold text-gray-900 mb-2">Delete Account?</h3>
            <p className="text-gray-600 mb-6">
              Are you absolutely sure? This action cannot be undone. All your data will be permanently deleted.
            </p>
            <div className="flex gap-3 justify-end">
              <button
                onClick={() => setShowDeleteModal(false)}
                disabled={deleting}
                className="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg font-medium hover:bg-gray-300 disabled:opacity-50"
              >
                Cancel
              </button>
              <button
                onClick={handleDeleteAccount}
                disabled={deleting}
                className="px-4 py-2 bg-red-600 text-white rounded-lg font-medium hover:bg-red-700 disabled:opacity-50"
              >
                {deleting ? 'Deleting...' : 'Yes, Delete My Account'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
