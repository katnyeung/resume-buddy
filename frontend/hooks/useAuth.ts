'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';

/**
 * Authentication hook that redirects to login if user is not authenticated
 * @returns Object with isAuthenticated and isLoading states
 */
export function useAuth() {
  const router = useRouter();
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('authToken');

    if (!token) {
      // No token found, redirect to login
      router.replace('/login?redirect=' + encodeURIComponent(window.location.pathname));
    } else {
      // Token exists
      setIsAuthenticated(true);
    }

    setIsLoading(false);
  }, [router]);

  return { isAuthenticated, isLoading };
}
