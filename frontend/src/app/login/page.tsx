'use client';

import React, { useState } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { apiClient } from '@/lib/apiClient';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Field';
import { Icon } from '@/components/ui/Icon';
import { Alert } from '@/components/ui/States';

/**
 * Sign in.
 *
 * Errors are deliberately vague about which half was wrong (UI/UX Specification 9.2), and the
 * lockout and rate-limit responses are given plain wording so a cashier locked out mid-shift
 * knows to fetch a manager rather than keep retrying.
 */
export default function LoginPage() {
  const { login } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setIsLoading(true);

    try {
      const response = await apiClient('/auth/login', {
        method: 'POST',
        requiresAuth: false,
        body: JSON.stringify({ username, password }),
      });
      const body = await response.json().catch(() => ({}));

      if (response.ok) {
        login(body.data);
        return;
      }

      if (response.status === 401) {
        setError('That username and password do not match. Check both and try again.');
      } else if (response.status === 429) {
        setError('Too many attempts. Wait a minute before trying again.');
      } else if (response.status === 423) {
        setError('This account is locked. Ask an administrator to unlock it.');
      } else {
        setError(body.error?.message || 'Could not sign in. Try again in a moment.');
      }
    } catch {
      setError('Cannot reach the server. Check the connection and try again.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        background: 'var(--color-surface-sunken)',
        padding: 'var(--space-4)',
      }}
    >
      <main
        style={{
          width: '100%',
          maxWidth: '24rem',
          background: 'var(--color-surface)',
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-lg)',
          boxShadow: 'var(--shadow-md)',
          padding: 'var(--space-8)',
        }}
      >
        <div className="stack" style={{ alignItems: 'center', marginBottom: 'var(--space-6)' }}>
          <span className="shell__brand-mark" style={{ width: '2.5rem', height: '2.5rem', fontSize: 'var(--font-size-body)' }}>
            PO
          </span>
          <h1 style={{ fontSize: 'var(--font-size-heading-sm)' }}>Sign in to POS Manager</h1>
        </div>

        <form onSubmit={handleSubmit} className="stack" noValidate>
          {error && <Alert tone="error">{error}</Alert>}

          <Input
            id="username"
            label="Username"
            required
            autoComplete="username"
            autoFocus
            value={username}
            disabled={isLoading}
            onChange={(event) => setUsername(event.target.value)}
          />

          <div style={{ position: 'relative' }}>
            <Input
              id="password"
              label="Password"
              required
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              value={password}
              disabled={isLoading}
              onChange={(event) => setPassword(event.target.value)}
              style={{ paddingRight: 'var(--space-12)' }}
            />
            <button
              type="button"
              onClick={() => setShowPassword((current) => !current)}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
              style={{
                position: 'absolute',
                right: 'var(--space-2)',
                top: '1.85rem',
                minHeight: 0,
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                color: 'var(--color-foreground-muted)',
                display: 'flex',
                padding: 'var(--space-2)',
              }}
            >
              <Icon name={showPassword ? 'eye-off' : 'eye'} size={18} />
            </button>
          </div>

          <Button type="submit" block size="lg" isLoading={isLoading}>
            Sign in
          </Button>

          {/*
            No self-service password reset exists in the API, so the screen says who to ask rather
            than offering a link that goes nowhere — the previous page linked to "#".
          */}
          <p className="text-small text-muted text-center">
            Forgotten your password? An administrator can reset it for you.
          </p>
        </form>
      </main>
    </div>
  );
}
