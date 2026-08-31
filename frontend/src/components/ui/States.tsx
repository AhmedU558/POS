import React from 'react';
import Link from 'next/link';
import { Icon, IconName } from './Icon';
import { Button } from './Button';

/*
 * The four states every screen owes the user: loading, empty, error, and no-permission.
 *
 * These exist as components so a screen cannot ship with only one of them — the previous pages
 * variously rendered "Loading products...", a bare paragraph, or nothing at all.
 */

export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="loading-block" role="status">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function TableSkeleton({ rows = 5, columns = 4 }: { rows?: number; columns?: number }) {
  return (
    <div className="card__body stack" aria-hidden="true">
      {Array.from({ length: rows }).map((_, rowIndex) => (
        <div className="row" key={rowIndex}>
          {Array.from({ length: columns }).map((__, columnIndex) => (
            <span
              className="skeleton grow"
              key={columnIndex}
              style={{ maxWidth: columnIndex === 0 ? '8rem' : undefined }}
            />
          ))}
        </div>
      ))}
    </div>
  );
}

export interface EmptyStateProps {
  icon?: IconName;
  title: string;
  body?: string;
  action?: { label: string; href?: string; onClick?: () => void };
}

export function EmptyState({ icon = 'box', title, body, action }: EmptyStateProps) {
  return (
    <div className="empty-state">
      <span className="empty-state__icon">
        <Icon name={icon} size={36} />
      </span>
      <h3 className="empty-state__title">{title}</h3>
      {body && <p className="empty-state__body">{body}</p>}
      {action &&
        (action.href ? (
          <Link className="btn btn--primary" href={action.href}>
            <Icon name="plus" size={16} />
            {action.label}
          </Link>
        ) : (
          <Button icon="plus" onClick={action.onClick}>
            {action.label}
          </Button>
        ))}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="empty-state">
      <span className="empty-state__icon" style={{ color: 'var(--color-error)' }}>
        <Icon name="alert" size={36} />
      </span>
      <h3 className="empty-state__title">Something went wrong</h3>
      <p className="empty-state__body">{message}</p>
      {onRetry && (
        <Button variant="secondary" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}

export function Alert({
  tone = 'info',
  title,
  children,
  actions,
}: {
  tone?: 'info' | 'success' | 'warning' | 'error';
  title?: string;
  children: React.ReactNode;
  actions?: React.ReactNode;
}) {
  const icon: IconName = tone === 'error' ? 'error' : tone === 'warning' ? 'alert' : tone === 'success' ? 'check-circle' : 'info';
  return (
    <div className={`alert alert--${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      <span className="alert__icon">
        <Icon name={icon} size={18} />
      </span>
      <div className="grow">
        {title && <p className="alert__title">{title}</p>}
        <div>{children}</div>
        {actions && <div className="row" style={{ marginTop: 'var(--space-3)' }}>{actions}</div>}
      </div>
    </div>
  );
}

/**
 * Shown instead of a blank page when the signed-in role lacks the permission a screen needs
 * (UI/UX Specification 9.2). Naming the permission gives the user something to ask their manager
 * for, rather than a dead end.
 */
export function PermissionRequired({ permission, action }: { permission: string; action: string }) {
  return (
    <div className="empty-state">
      <span className="empty-state__icon">
        <Icon name="user" size={36} />
      </span>
      <h3 className="empty-state__title">You do not have access</h3>
      <p className="empty-state__body">
        {action} requires the <strong>{permission}</strong> permission. Ask an administrator to add it to your role.
      </p>
      <Link className="btn btn--secondary" href="/">
        Back to dashboard
      </Link>
    </div>
  );
}
