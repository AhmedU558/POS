"use client";

import React, { useState } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { apiClient } from '@/lib/apiClient';

export default function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const { login } = useAuth();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsLoading(true);

    try {
      const res = await apiClient('/auth/login', {
        method: 'POST',
        requiresAuth: false,
        body: JSON.stringify({ username, password }),
      });

      const body = await res.json();

      if (res.ok) {
        login(body.data);
      } else {
        // Map backend errors safely to avoid leaking sensitive details (SCR-001)
        if (res.status === 401) {
          setError('Invalid username or password.');
        } else if (res.status === 429) {
          setError('Too many attempts. Please try again later.');
        } else {
          setError(body.error?.message || 'Login failed. Please try again.');
        }
      }
    } catch (err) {
      setError('Network error. Please check your connection.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '100vh',
      backgroundColor: 'var(--color-surface-sunken)',
      padding: 'var(--space-4)'
    }}>
      <div style={{
        backgroundColor: 'var(--color-surface)',
        padding: 'var(--space-8)',
        borderRadius: 'var(--radius-lg)',
        boxShadow: 'var(--shadow-md)',
        width: '100%',
        maxWidth: '400px'
      }}>
        <h1 style={{
          fontSize: 'var(--font-size-heading)',
          fontWeight: 'var(--font-weight-semibold)',
          marginBottom: 'var(--space-6)',
          textAlign: 'center'
        }}>Sign In</h1>

        {error && (
          <div style={{
            backgroundColor: 'var(--color-error-surface)',
            color: 'var(--color-error)',
            padding: 'var(--space-3)',
            borderRadius: 'var(--radius-sm)',
            marginBottom: 'var(--space-4)',
            fontSize: 'var(--font-size-small)'
          }}>
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: 'var(--space-4)' }}>
            <label htmlFor="username" style={{
              display: 'block',
              fontSize: 'var(--font-size-small)',
              fontWeight: 'var(--font-weight-medium)',
              marginBottom: 'var(--space-2)'
            }}>Username</label>
            <input id="username" type="text"
              required
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              disabled={isLoading}
              style={{
                width: '100%',
                height: 'var(--control-height)',
                padding: '0 var(--space-3)',
                border: '1px solid var(--color-border)',
                borderRadius: 'var(--radius-md)',
                fontSize: 'var(--font-size-body)'
              }}
            />
          </div>

          <div style={{ marginBottom: 'var(--space-6)' }}>
            <label htmlFor="password" style={{
              display: 'block',
              fontSize: 'var(--font-size-small)',
              fontWeight: 'var(--font-weight-medium)',
              marginBottom: 'var(--space-2)'
            }}>Password</label>
            <div style={{ position: 'relative' }}>
              <input id="password" type={showPassword ? 'text' : 'password'}
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={isLoading}
                style={{
                  width: '100%',
                  height: 'var(--control-height)',
                  padding: '0 var(--space-3)',
                  paddingRight: 'var(--space-10)',
                  border: '1px solid var(--color-border)',
                  borderRadius: 'var(--radius-md)',
                  fontSize: 'var(--font-size-body)'
                }}
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                tabIndex={-1}
                style={{
                  position: 'absolute',
                  right: 'var(--space-2)',
                  top: '50%',
                  transform: 'translateY(-50%)',
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  fontSize: 'var(--font-size-small)',
                  color: 'var(--color-foreground-muted)'
                }}
              >
                {showPassword ? 'Hide' : 'Show'}
              </button>
            </div>
            <div style={{ textAlign: 'right', marginTop: 'var(--space-2)' }}>
              <a href="#" style={{
                fontSize: 'var(--font-size-small)',
                color: 'var(--color-primary)',
                textDecoration: 'none'
              }}>Forgot password?</a>
            </div>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            style={{
              width: '100%',
              height: 'var(--control-height)',
              backgroundColor: 'var(--color-primary)',
              color: 'var(--color-primary-foreground)',
              border: 'none',
              borderRadius: 'var(--radius-md)',
              fontSize: 'var(--font-size-body)',
              fontWeight: 'var(--font-weight-medium)',
              cursor: isLoading ? 'not-allowed' : 'pointer',
              opacity: isLoading ? 0.7 : 1
            }}
          >
            {isLoading ? 'Signing in...' : 'Sign In'}
          </button>
        </form>
      </div>
    </div>
  );
}

