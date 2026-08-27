import React from 'react';

export function Badge({ children, variant = 'info' }: { children: React.ReactNode, variant?: 'success' | 'warning' | 'error' | 'info' | 'pending' }) {
  let bgColor = 'var(--color-info-surface)';
  let color = 'var(--color-info)';

  if (variant === 'success') {
    bgColor = 'var(--color-success-surface)';
    color = 'var(--color-success)';
  } else if (variant === 'warning') {
    bgColor = 'var(--color-warning-surface)';
    color = 'var(--color-warning)';
  } else if (variant === 'error') {
    bgColor = 'var(--color-error-surface)';
    color = 'var(--color-error)';
  } else if (variant === 'pending') {
    bgColor = 'var(--color-pending-surface)';
    color = 'var(--color-pending)';
  }

  return (
    <span style={{
      display: 'inline-flex',
      alignItems: 'center',
      padding: '2px var(--space-2)',
      borderRadius: 'var(--radius-sm)',
      fontSize: 'var(--font-size-small)',
      fontWeight: 'var(--font-weight-medium)',
      backgroundColor: bgColor,
      color: color,
    }}>
      {children}
    </span>
  );
}
