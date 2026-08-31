import React from 'react';

export type BadgeVariant = 'success' | 'warning' | 'error' | 'info' | 'pending';

export interface BadgeProps {
  children: React.ReactNode;
  variant?: BadgeVariant;
  className?: string;
}

/**
 * Colour is never the only signal (UI/UX Specification 7.1): every badge carries a shape marker
 * alongside its hue, so the state survives a monochrome display or colour-blind vision.
 */
export function Badge({ children, variant = 'info', className }: BadgeProps) {
  return (
    <span className={['badge', `badge--${variant}`, className ?? ''].filter(Boolean).join(' ')}>
      <span className="badge__dot" aria-hidden="true" />
      {children}
    </span>
  );
}

/*
 * Status vocabularies.
 *
 * The API returns raw enum names. Showing "PARTIALLY_RECEIVED" to a store manager is a leak of
 * the database into the shop floor, so every status is mapped to a human label and a variant in
 * exactly one place.
 */

interface StatusMeta {
  label: string;
  variant: BadgeVariant;
}

const SALE_STATUS: Record<string, StatusMeta> = {
  COMPLETED: { label: 'Completed', variant: 'success' },
  HELD: { label: 'On hold', variant: 'warning' },
  VOIDED: { label: 'Voided', variant: 'error' },
  REFUNDED: { label: 'Refunded', variant: 'pending' },
};

const PURCHASE_ORDER_STATUS: Record<string, StatusMeta> = {
  DRAFT: { label: 'Draft', variant: 'pending' },
  SUBMITTED: { label: 'Submitted', variant: 'info' },
  PARTIALLY_RECEIVED: { label: 'Partially received', variant: 'warning' },
  RECEIVED: { label: 'Received', variant: 'success' },
  CANCELLED: { label: 'Cancelled', variant: 'error' },
};

const SESSION_STATUS: Record<string, StatusMeta> = {
  OPEN: { label: 'Open', variant: 'success' },
  CLOSED: { label: 'Closed', variant: 'pending' },
};

const BATCH_STATUS: Record<string, StatusMeta> = {
  OK: { label: 'In date', variant: 'success' },
  APPROACHING: { label: 'Expiring soon', variant: 'warning' },
  EXPIRING_TODAY: { label: 'Expires today', variant: 'warning' },
  EXPIRED: { label: 'Expired', variant: 'error' },
};

const INVOICE_STATUS: Record<string, StatusMeta> = {
  UNPAID: { label: 'Unpaid', variant: 'warning' },
  PARTIALLY_PAID: { label: 'Part paid', variant: 'info' },
  PAID: { label: 'Paid', variant: 'success' },
  OVERDUE: { label: 'Overdue', variant: 'error' },
  CANCELLED: { label: 'Cancelled', variant: 'pending' },
};

const ALERT_STATUS: Record<string, StatusMeta> = {
  OPEN: { label: 'Needs action', variant: 'warning' },
  ACKNOWLEDGED: { label: 'Acknowledged', variant: 'pending' },
};

const VOCABULARIES = {
  sale: SALE_STATUS,
  purchaseOrder: PURCHASE_ORDER_STATUS,
  session: SESSION_STATUS,
  batch: BATCH_STATUS,
  invoice: INVOICE_STATUS,
  alert: ALERT_STATUS,
} as const;

export type StatusKind = keyof typeof VOCABULARIES;

/** Falls back to a title-cased enum name rather than hiding a status the backend added later. */
export function StatusBadge({ kind, status }: { kind: StatusKind; status: string | null | undefined }) {
  if (!status) {
    return <span className="text-subtle">—</span>;
  }
  const meta = VOCABULARIES[kind][status];
  if (meta) {
    return <Badge variant={meta.variant}>{meta.label}</Badge>;
  }
  const readable = status.charAt(0) + status.slice(1).toLowerCase().replace(/_/g, ' ');
  return <Badge variant="pending">{readable}</Badge>;
}

export function ActiveBadge({ active }: { active: boolean }) {
  return <Badge variant={active ? 'success' : 'pending'}>{active ? 'Active' : 'Inactive'}</Badge>;
}
