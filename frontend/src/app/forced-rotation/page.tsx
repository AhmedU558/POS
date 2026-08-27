"use client";

import React, { useState } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { apiClient } from '@/lib/apiClient';

export default function ForcedRotationPage() {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const { refreshAuth, logout } = useAuth();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (newPassword !== confirmPassword) {
      setError('New passwords do not match.');
      return;
    }

    setIsLoading(true);

    try {
      const res = await apiClient('/auth/change-password', {
        method: 'POST',
        body: JSON.stringify({ currentPassword, newPassword }),
      });

      if (res.ok) {
        // Successful rotation, refresh auth to remove passwordChangeRequired state
        await refreshAuth();
      } else {
        const body = await res.json();
        setError(body.error?.message || 'Failed to change password. Please check requirements and try again.');
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
          fontSize: 'var(--font-size-heading-sm)',
          fontWeight: 'var(--font-weight-semibold)',
          marginBottom: 'var(--space-2)',
          textAlign: 'center'
        }}>Action Required</h1>
        <p style={{
          fontSize: 'var(--font-size-small)',
          color: 'var(--color-foreground-muted)',
          textAlign: 'center',
          marginBottom: 'var(--space-6)'
        }}>
          For security reasons, you must change your password before continuing.
        </p>

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
            <label htmlFor="currentPassword" style={{ display: 'block', fontSize: 'var(--font-size-small)', fontWeight: 'var(--font-weight-medium)', marginBottom: 'var(--space-2)' }}>Current Password</label>
            <input id="currentPassword" type={showPassword ? 'text' : 'password'} required value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              disabled={isLoading}
              style={{
                width: '100%', height: 'var(--control-height)', padding: '0 var(--space-3)',
                border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)', fontSize: 'var(--font-size-body)'
              }}
            />
          </div>

          <div style={{ marginBottom: 'var(--space-4)' }}>
            <label htmlFor="newPassword" style={{ display: 'block', fontSize: 'var(--font-size-small)', fontWeight: 'var(--font-weight-medium)', marginBottom: 'var(--space-2)' }}>New Password</label>
            <input id="newPassword" type={showPassword ? 'text' : 'password'} required value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              disabled={isLoading}
              style={{
                width: '100%', height: 'var(--control-height)', padding: '0 var(--space-3)',
                border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)', fontSize: 'var(--font-size-body)'
              }}
            />
          </div>

          <div style={{ marginBottom: 'var(--space-6)' }}>
            <label htmlFor="confirmPassword" style={{ display: 'block', fontSize: 'var(--font-size-small)', fontWeight: 'var(--font-weight-medium)', marginBottom: 'var(--space-2)' }}>Confirm New Password</label>
            <input id="confirmPassword" type={showPassword ? 'text' : 'password'} required value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              disabled={isLoading}
              style={{
                width: '100%', height: 'var(--control-height)', padding: '0 var(--space-3)',
                border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)', fontSize: 'var(--font-size-body)'
              }}
            />
            <div style={{ marginTop: 'var(--space-2)', textAlign: 'right' }}>
               <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                tabIndex={-1}
                style={{
                  background: 'none', border: 'none', cursor: 'pointer',
                  fontSize: 'var(--font-size-small)', color: 'var(--color-foreground-muted)'
                }}
              >
                {showPassword ? 'Hide Passwords' : 'Show Passwords'}
              </button>
            </div>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            style={{
              width: '100%', height: 'var(--control-height)', backgroundColor: 'var(--color-primary)',
              color: 'var(--color-primary-foreground)', border: 'none', borderRadius: 'var(--radius-md)',
              fontSize: 'var(--font-size-body)', fontWeight: 'var(--font-weight-medium)',
              cursor: isLoading ? 'not-allowed' : 'pointer', opacity: isLoading ? 0.7 : 1,
              marginBottom: 'var(--space-3)'
            }}
          >
            {isLoading ? 'Updating...' : 'Change Password'}
          </button>
          
          <button
            type="button"
            onClick={() => logout()}
            disabled={isLoading}
            style={{
              width: '100%', height: 'var(--control-height)', backgroundColor: 'transparent',
              color: 'var(--color-foreground-muted)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)',
              fontSize: 'var(--font-size-body)', fontWeight: 'var(--font-weight-medium)',
              cursor: isLoading ? 'not-allowed' : 'pointer'
            }}
          >
            Cancel & Logout
          </button>
        </form>
      </div>
    </div>
  );
}

