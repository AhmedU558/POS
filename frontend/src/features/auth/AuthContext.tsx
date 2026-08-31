"use client";

import React, { createContext, useContext, useEffect, useState, ReactNode } from 'react';
import { apiClient, setTokens, clearTokens } from '@/lib/apiClient';

export interface User {
  id: string;
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  permissions: string[];
  storeIds: string[];
}

export interface LoginSession {
  accessToken: string;
  refreshToken: string;
  passwordChangeRequired?: boolean;
}

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  passwordChangeRequired: boolean;
  login: (data: LoginSession) => void;
  logout: () => Promise<void>;
  refreshAuth: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [passwordChangeRequired, setPasswordChangeRequired] = useState<boolean>(false);

  const fetchCurrentUser = async () => {
    try {
      const res = await apiClient('/auth/me');
      if (res.ok) {
        const body = await res.json();
        setUser(body.data);
        setIsAuthenticated(true);
        // The /auth/me doesn't explicitly return passwordChangeRequired in the body by default
        // But the login/refresh tokens contain the flag. 
        // Also if we hit a 403 on other endpoints we can set it.
        // For now, if we successfully fetch /auth/me, we know we are authenticated.
      } else if (res.status === 403) {
        const body = await res.json();
        if (body.error?.code === 'PASSWORD_CHANGE_REQUIRED') {
           setIsAuthenticated(true);
           setPasswordChangeRequired(true);
        } else {
           handleLogoutLocal();
        }
      } else {
        handleLogoutLocal();
      }
    } catch (error) {
      handleLogoutLocal();
    } finally {
      setIsLoading(false);
    }
  };

  const login = (data: LoginSession) => {
    setTokens(data.accessToken, data.refreshToken);
    setIsAuthenticated(true);
    setPasswordChangeRequired(!!data.passwordChangeRequired);
    if (!data.passwordChangeRequired) {
      fetchCurrentUser();
    } else {
      setIsLoading(false);
    }
  };

  const handleLogoutLocal = () => {
    clearTokens();
    setUser(null);
    setIsAuthenticated(false);
    setPasswordChangeRequired(false);
  };

  const logout = async () => {
    try {
      await apiClient('/auth/logout', { method: 'POST' });
    } catch (e) {
      // Ignore network errors on logout
    } finally {
      handleLogoutLocal();
    }
  };

  useEffect(() => {
    // Initial load
    const token = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
    if (token) {
      fetchCurrentUser();
    } else {
      setIsLoading(false);
    }

    // Listen to forced logout events from apiClient
    const handleForcedLogout = () => {
      handleLogoutLocal();
    };

    if (typeof window !== 'undefined') {
      window.addEventListener('auth:logout', handleForcedLogout);
    }
    return () => {
      if (typeof window !== 'undefined') {
        window.removeEventListener('auth:logout', handleForcedLogout);
      }
    };
  }, []);

  return (
    <AuthContext.Provider value={{ user, isAuthenticated, isLoading, passwordChangeRequired, login, logout, refreshAuth: fetchCurrentUser }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};