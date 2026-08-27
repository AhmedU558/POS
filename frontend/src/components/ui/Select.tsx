import React from 'react';

export interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  options: { label: string; value: string }[];
}

export function Select({ label, error, options, style, id, ...props }: SelectProps) {
  return (
    <div style={{ marginBottom: 'var(--space-4)', width: '100%' }}>
      {label && (
        <label htmlFor={id} style={{
          display: 'block',
          fontSize: 'var(--font-size-small)',
          fontWeight: 'var(--font-weight-medium)',
          marginBottom: 'var(--space-2)'
        }}>
          {label}
        </label>
      )}
      <select
        id={id}
        {...props}
        style={{
          width: '100%',
          height: 'var(--control-height)',
          padding: '0 var(--space-3)',
          border: error ? '1px solid var(--color-error)' : '1px solid var(--color-border)',
          borderRadius: 'var(--radius-md)',
          fontSize: 'var(--font-size-body)',
          backgroundColor: 'var(--color-surface)',
          color: 'var(--color-foreground)',
          ...style
        }}
      >
        <option value="">-- Select --</option>
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}>{opt.label}</option>
        ))}
      </select>
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
