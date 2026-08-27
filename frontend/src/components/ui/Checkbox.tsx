import React from 'react';

export function Checkbox({ label, error, style, id, ...props }: React.InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string }) {
  return (
    <div style={{ marginBottom: 'var(--space-4)', ...style }}>
      <label htmlFor={id} style={{ display: 'flex', alignItems: 'center', cursor: 'pointer' }}>
        <input
          id={id}
          type="checkbox"
          {...props}
          style={{
            width: '16px',
            height: '16px',
            marginRight: 'var(--space-2)',
            cursor: 'pointer'
          }}
        />
        <span style={{ fontSize: 'var(--font-size-body)' }}>{label}</span>
      </label>
      {error && (
        <div style={{
          color: 'var(--color-error)',
          fontSize: 'var(--font-size-small)',
          marginTop: 'var(--space-1)'
        }}>
          {error}
        </div>
      )}
    </div>
  );
}
