'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { inventoryApi, InventoryBatch, InventoryBatchStatus } from '@/lib/api/inventory';

type ViewMode = 'all' | 'expiry';

function statusLabel(batch: InventoryBatch): string {
  switch (batch.status) {
    case 'EXPIRED':
      return 'Expired';
    case 'EXPIRING_TODAY':
      return 'Expiring today';
    case 'APPROACHING':
      return batch.daysRemaining == null
        ? 'Approaching'
        : `Approaching (${batch.daysRemaining} days remaining)`;
    default:
      return 'OK';
  }
}

function statusTone(status: InventoryBatchStatus): { color: string; background: string } {
  switch (status) {
    case 'EXPIRED':
      return { color: 'var(--color-error)', background: 'var(--color-error-surface)' };
    case 'EXPIRING_TODAY':
    case 'APPROACHING':
      return { color: 'var(--color-warning)', background: 'var(--color-warning-surface)' };
    default:
      return { color: 'var(--color-success)', background: 'var(--color-success-surface)' };
  }
}

export default function BatchesExpiryPage() {
  const { user } = useAuth();
  const storeId = user?.storeIds?.[0];
  const canRead = user?.permissions?.includes('INVENTORY_READ') ?? false;

  const [mode, setMode] = useState<ViewMode>('all');
  const [days, setDays] = useState(7);
  const [batches, setBatches] = useState<InventoryBatch[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!canRead || !storeId) {
      return;
    }
    const load = mode === 'expiry'
      ? inventoryApi.getExpiry(storeId, 0, 50, days)
      : inventoryApi.getBatches(storeId, 0, 50, days);
    load
      .then((res) => {
        setBatches(res.content ?? []);
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load batches');
      });
  }, [canRead, storeId, mode, days]);

  if (!canRead) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Batches &amp; Expiry</h1>
        <p role="status">Access is restricted. You do not have permission to view batches.</p>
      </div>
    );
  }

  if (!storeId) {
    return <div style={{ padding: 'var(--space-6)' }}>No store context available.</div>;
  }

  return (
    <div style={{ padding: 'var(--space-6)' }}>
      <h1>Batches &amp; Expiry</h1>

      {error && (
        <div
          role="alert"
          style={{
            marginTop: 'var(--space-4)',
            padding: 'var(--space-4)',
            background: 'var(--color-error-surface)',
            color: 'var(--color-error)',
            borderRadius: 'var(--radius-md)',
          }}
        >
          {error}
        </div>
      )}

      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 'var(--space-4)',
          marginTop: 'var(--space-4)',
          marginBottom: 'var(--space-4)',
          alignItems: 'flex-end',
        }}
      >
        <div>
          <label htmlFor="batch-view" style={{ display: 'block', fontWeight: 'var(--font-weight-medium)', marginBottom: 'var(--space-2)' }}>
            View
          </label>
          <select
            id="batch-view"
            value={mode}
            onChange={(e) => setMode(e.target.value as ViewMode)}
            style={{
              height: 'var(--control-height)',
              padding: '0 var(--space-3)',
              border: '1px solid var(--color-border)',
              borderRadius: 'var(--radius-md)',
              background: 'var(--color-surface)',
              color: 'var(--color-foreground)',
            }}
          >
            <option value="all">All batches</option>
            <option value="expiry">Expiring and expired</option>
          </select>
        </div>
        <div>
          <label htmlFor="batch-days" style={{ display: 'block', fontWeight: 'var(--font-weight-medium)', marginBottom: 'var(--space-2)' }}>
            Window (days)
          </label>
          <select
            id="batch-days"
            value={days}
            onChange={(e) => setDays(Number(e.target.value))}
            style={{
              height: 'var(--control-height)',
              padding: '0 var(--space-3)',
              border: '1px solid var(--color-border)',
              borderRadius: 'var(--radius-md)',
              background: 'var(--color-surface)',
              color: 'var(--color-foreground)',
            }}
          >
            <option value={7}>7</option>
            <option value={30}>30</option>
          </select>
        </div>
      </div>

      <table style={{ width: '100%', borderCollapse: 'collapse', background: 'var(--color-surface)' }}>
        <thead>
          <tr>
            <th style={{ textAlign: 'left', padding: 'var(--space-3)', borderBottom: '1px solid var(--color-border)' }}>Product</th>
            <th style={{ textAlign: 'left', padding: 'var(--space-3)', borderBottom: '1px solid var(--color-border)' }}>Batch</th>
            <th style={{ textAlign: 'right', padding: 'var(--space-3)', borderBottom: '1px solid var(--color-border)' }}>Quantity</th>
            <th style={{ textAlign: 'left', padding: 'var(--space-3)', borderBottom: '1px solid var(--color-border)' }}>Expiry date</th>
            <th style={{ textAlign: 'left', padding: 'var(--space-3)', borderBottom: '1px solid var(--color-border)' }}>Store</th>
            <th style={{ textAlign: 'left', padding: 'var(--space-3)', borderBottom: '1px solid var(--color-border)' }}>Status</th>
          </tr>
        </thead>
        <tbody>
          {batches.map((batch) => {
            const tone = statusTone(batch.status);
            return (
              <tr key={batch.id}>
                <td style={{ padding: 'var(--space-3)', borderBottom: '1px solid var(--color-border)' }}>
                  {batch.productName} ({batch.sku})
                </td>
                <td style={{ padding: 'var(--space-3)', borderBottom: '1px solid var(--color-border)' }}>{batch.batchNumber}</td>
                <td style={{ padding: 'var(--space-3)', borderBottom: '1px solid var(--color-border)', textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>
                  {batch.quantity}
                </td>
                <td style={{ padding: 'var(--space-3)', borderBottom: '1px solid var(--color-border)' }}>
                  {batch.expirationDate ?? '—'}
                </td>
                <td style={{ padding: 'var(--space-3)', borderBottom: '1px solid var(--color-border)' }}>{batch.storeName}</td>
                <td style={{ padding: 'var(--space-3)', borderBottom: '1px solid var(--color-border)' }}>
                  <span
                    role="status"
                    style={{
                      display: 'inline-block',
                      padding: '0 var(--space-2)',
                      borderRadius: 'var(--radius-sm)',
                      color: tone.color,
                      background: tone.background,
                      fontWeight: 'var(--font-weight-medium)',
                    }}
                  >
                    {statusLabel(batch)}
                  </span>
                </td>
              </tr>
            );
          })}
          {batches.length === 0 && (
            <tr>
              <td colSpan={6} style={{ padding: 'var(--space-4)', textAlign: 'center', color: 'var(--color-foreground-muted)' }}>
                No batches found.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
