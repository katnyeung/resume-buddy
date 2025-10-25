/**
 * Utility functions for user management
 */

/**
 * Get the current logged-in user's ID
 * Priority: user.id from localStorage
 * Fallback: 'default_user' for development/testing
 */
export function getCurrentUserId(): string {
  if (typeof window === 'undefined') {
    // Server-side rendering
    return 'default_user';
  }

  try {
    const storedUser = localStorage.getItem('user');
    if (storedUser) {
      const user = JSON.parse(storedUser);
      if (user.id) {
        return user.id;
      }
    }
  } catch (error) {
    console.error('Failed to get user ID from localStorage:', error);
  }

  // Fallback for development
  return 'default_user';
}

/**
 * Get the current logged-in user's email
 */
export function getCurrentUserEmail(): string | null {
  if (typeof window === 'undefined') {
    return null;
  }

  try {
    const storedUser = localStorage.getItem('user');
    if (storedUser) {
      const user = JSON.parse(storedUser);
      return user.email || null;
    }
  } catch (error) {
    console.error('Failed to get user email from localStorage:', error);
  }

  return null;
}

/**
 * Get the current logged-in user object
 */
export function getCurrentUser(): { id: string; email: string; fullName: string } | null {
  if (typeof window === 'undefined') {
    return null;
  }

  try {
    const storedUser = localStorage.getItem('user');
    if (storedUser) {
      return JSON.parse(storedUser);
    }
  } catch (error) {
    console.error('Failed to get user from localStorage:', error);
  }

  return null;
}
