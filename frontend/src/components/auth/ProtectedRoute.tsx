"use client";

import { useAuth } from '@/features/auth/AuthContext';
import { useRouter, usePathname } from 'next/navigation';
import { useEffect } from 'react';

export const ProtectedRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated, isLoading, passwordChangeRequired } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (!isLoading) {
      if (!isAuthenticated) {
        if (pathname !== '/login') {
          router.replace('/login');
        }
      } else if (passwordChangeRequired) {
        if (pathname !== '/forced-rotation') {
          router.replace('/forced-rotation');
        }
      } else {
        // Authenticated and no password change required
        if (pathname === '/login' || pathname === '/forced-rotation') {
          router.replace('/');
        }
      }
    }
  }, [isLoading, isAuthenticated, passwordChangeRequired, pathname, router]);

  if (isLoading) {
    return <div style={{ padding: 'var(--space-8)' }}>Loading...</div>;
  }

  // If we are redirecting, we might still render children for a tick.
  // To avoid flashing unauthorized content, return null if state doesn't match intended route.
  if (!isAuthenticated && pathname !== '/login') {
    return null;
  }

  if (isAuthenticated && passwordChangeRequired && pathname !== '/forced-rotation') {
    return null;
  }

  return <>{children}</>;
};