import React from 'react';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger';
  isLoading?: boolean;
}

export function Button({ variant = 'primary', isLoading, children, style, ...props }: ButtonProps) {
  const baseStyle: React.CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 'var(--control-height)',
    padding: '0 var(--space-4)',
    border: 'none',
    borderRadius: 'var(--radius-md)',
    fontSize: 'var(--font-size-body)',
    fontWeight: 'var(--font-weight-medium)',
    cursor: props.disabled || isLoading ? 'not-allowed' : 'pointer',
    opacity: props.disabled || isLoading ? 0.7 : 1,
    ...style,
  };

  if (variant === 'primary') {
    baseStyle.backgroundColor = 'var(--color-primary)';
    baseStyle.color = 'var(--color-primary-foreground)';
  } else if (variant === 'danger') {
    baseStyle.backgroundColor = 'var(--color-error)';
    baseStyle.color = 'var(--color-primary-foreground)';
  } else {
    baseStyle.backgroundColor = 'var(--color-surface-sunken)';
    baseStyle.color = 'var(--color-foreground)';
    baseStyle.border = '1px solid var(--color-border)';
  }

  return (
    <button {...props} style={baseStyle}>
      {isLoading ? 'Loading...' : children}
    </button>
  );
}
