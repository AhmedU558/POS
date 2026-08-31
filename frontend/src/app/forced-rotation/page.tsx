'use client';

import React, { useState } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { apiClient } from '@/lib/apiClient';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Field';
import { Alert } from '@/components/ui/States';

/**
 * Mandatory password change on first sign-in.
 *
 * The initial password was chosen by an operator and travelled through a pipeline and at least
 * one person before reaching its holder (ADR-013), so it is treated as compromised and must be
 * replaced before anything else can be done.
 */
export default function ForcedRotationPage() {
  const { refreshAuth, logout } = useAuth();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const mismatch = confirmPassword.length > 0 && newPassword !== confirmPassword;

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);

    if (newPassword !== confirmPassword) {
      setError('The two new passwords do not match.');
      return;
    }
    if (newPassword === currentPassword) {
      setError('The new password must be different from the current one.');
      return;
    }

    setIsLoading(true);
    try {
      const response = await apiClient('/auth/change-password', {
        method: 'POST',
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      if (response.ok) {
        await refreshAuth();
        return;
      }
      const body = await response.json().catch(() => ({}));
      setError(body.error?.message || 'That password was not accepted. Try a longer one with a mix of characters.');
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
          maxWidth: '26rem',
          background: 'var(--color-surface)',
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-lg)',
          boxShadow: 'var(--shadow-md)',
          padding: 'var(--space-8)',
        }}
      >
        <h1 style={{ fontSize: 'var(--font-size-heading-sm)', marginBottom: 'var(--space-2)' }}>
          Choose a new password
        </h1>
        <p className="text-muted" style={{ marginBottom: 'var(--space-6)' }}>
          Your account was set up with a temporary password. Replace it before you carry on.
        </p>

        <form onSubmit={handleSubmit} className="stack" noValidate>
          {error && <Alert tone="error">{error}</Alert>}

          <Input
            id="current-password"
            label="Current password"
            required
            type="password"
            autoComplete="current-password"
            autoFocus
            value={currentPassword}
            disabled={isLoading}
            onChange={(event) => setCurrentPassword(event.target.value)}
          />
          <Input
            id="new-password"
            label="New password"
            required
            type="password"
            autoComplete="new-password"
            value={newPassword}
            disabled={isLoading}
            hint="At least 12 characters. A memorable phrase works well."
            onChange={(event) => setNewPassword(event.target.value)}
          />
          <Input
            id="confirm-password"
            label="Confirm new password"
            required
            type="password"
            autoComplete="new-password"
            value={confirmPassword}
            disabled={isLoading}
            error={mismatch ? 'The two passwords do not match.' : undefined}
            onChange={(event) => setConfirmPassword(event.target.value)}
          />

          <Button type="submit" block size="lg" isLoading={isLoading} disabled={mismatch}>
            Set new password
          </Button>
          <Button variant="ghost" block onClick={() => void logout()} disabled={isLoading}>
            Sign out instead
          </Button>
        </form>
      </main>
    </div>
  );
}
