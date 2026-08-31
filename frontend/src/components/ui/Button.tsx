import React from 'react';
import { Icon, IconName } from './Icon';

export interface ButtonProps extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'className'> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  size?: 'sm' | 'md' | 'lg';
  isLoading?: boolean;
  block?: boolean;
  icon?: IconName;
  className?: string;
}

/**
 * The application's single button.
 *
 * While loading, the label stays put and a spinner appears beside it. Replacing the label with
 * the word "Loading" — as this component used to — makes the button change width mid-click and
 * loses the only clue about what the user just triggered.
 */
export function Button({
  variant = 'primary',
  size = 'md',
  isLoading = false,
  block = false,
  icon,
  children,
  disabled,
  className,
  type = 'button',
  ...props
}: ButtonProps) {
  const classes = [
    'btn',
    `btn--${variant}`,
    size !== 'md' ? `btn--${size}` : '',
    block ? 'btn--block' : '',
    className ?? '',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <button {...props} type={type} className={classes} disabled={disabled || isLoading} aria-busy={isLoading || undefined}>
      {isLoading ? <span className="btn__spinner" aria-hidden="true" /> : icon ? <Icon name={icon} size={16} /> : null}
      {children}
    </button>
  );
}
