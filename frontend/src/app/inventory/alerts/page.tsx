'use client';

import { useEffect, useState, type CSSProperties } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { inventoryApi, StockAlert } from '@/lib/api/inventory';
import { Button } from '@/components/ui/Button';

function statusLabel(status: string): string {
  return status === 'ACKNOWLEDGED' ? 'Acknowledged' : 'Open';
}

export default function StockAlertsPage() {
  const { user } = useAuth();
  const storeId = user?.storeIds?.[0];
  const canRead = user?.permissions?.includes('INVENTORY_READ') ?? false;

  const [alertType, setAlertType] = useState('');
  const [status, setStatus] = useState('');
  const [days, setDays] = useState(7);
  const [alerts, setAlerts] = useState<StockAlert[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [ackingId, setAckingId] = useState<string | null>(null);

  const load = () => {
    if (!canRead || !storeId) {
      return;
    }
    inventoryApi.getAlerts(storeId, 0, 50, alertType || undefined, status || undefined, days)
      .then((res) => {
        setAlerts(res.content ?? []);
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load alerts');
      });
  };

  useEffect(() => {
    load();
  }, [canRead, storeId, alertType, status, days]);

  const acknowledge = async (id: string) => {
    setAckingId(id);
    try {
      await inventoryApi.acknowledgeAlert(id);
      load();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to acknowledge alert');
    } finally {
      setAckingId(null);
    }
  };

  if (!canRead) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Stock Alerts</h1>
        <p role="status">Access is restricted. You do not have permission to view stock alerts.</p>
      </div>
    );
  }

  if (!storeId) {
    return <div style={{ padding: 'var(--space-6)' }}>No store context available.</div>;
  }

  return (
    <div style={{ padding: 'var(--space-6)' }}>
      <h1>Stock Alerts</h1>

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

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-4)', margin: 'var(--space-4) 0' }}>
        <div>
          <label htmlFor="alert-type" style={{ display: 'block', fontWeight: 'var(--font-weight-medium)', marginBottom: 'var(--space-2)' }}>Type</label>
          <select id="alert-type" value={alertType} onChange={(e) => setAlertType(e.target.value)} style={selectStyle}>
            <option value="">All types</option>
            <option value="LOW_STOCK">Low stock</option>
            <option value="EXPIRY">Expiry</option>
          </select>
        </div>
        <div>
          <label htmlFor="alert-status" style={{ display: 'block', fontWeight: 'var(--font-weight-medium)', marginBottom: 'var(--space-2)' }}>Status</label>
          <select id="alert-status" value={status} onChange={(e) => setStatus(e.target.value)} style={selectStyle}>
            <option value="">All statuses</option>
            <option value="OPEN">Open</option>
            <option value="ACKNOWLEDGED">Acknowledged</option>
          </select>
        </div>
        <div>
          <label htmlFor="alert-days" style={{ display: 'block', fontWeight: 'var(--font-weight-medium)', marginBottom: 'var(--space-2)' }}>Window (days)</label>
          <select id="alert-days" value={days} onChange={(e) => setDays(Number(e.target.value))} style={selectStyle}>
            <option value={7}>7</option>
            <option value={30}>30</option>
          </select>
        </div>
      </div>

      <table style={{ width: '100%', borderCollapse: 'collapse', background: 'var(--color-surface)' }}>
        <thead>
          <tr>
            <th style={th}>Product</th>
            <th style={th}>Type</th>
            <th style={{ ...th, textAlign: 'right' }}>Quantity</th>
            <th style={{ ...th, textAlign: 'right' }}>Minimum</th>
            <th style={th}>Batch / expiry</th>
            <th style={th}>Store</th>
            <th style={th}>Status</th>
            <th style={th}>Suggested action</th>
            <th style={th}> </th>
          </tr>
        </thead>
        <tbody>
          {alerts.map((alert) => {
            const expired = alert.alertType === 'EXPIRY' && (alert.daysRemaining ?? 0) < 0;
            return (
              <tr key={alert.id}>
                <td style={td}>{alert.productName} ({alert.sku})</td>
                <td style={td}>{alert.alertType === 'LOW_STOCK' ? 'Low stock' : 'Expiry'}</td>
                <td style={{ ...td, textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>{alert.quantity}</td>
                <td style={{ ...td, textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>{alert.minimumLevel ?? '—'}</td>
                <td style={td}>
                  {alert.batchNumber ? `${alert.batchNumber} / ${alert.expirationDate ?? '—'}` : '—'}
                </td>
                <td style={td}>{alert.storeName}</td>
                <td style={td}>
                  <span
                    role="status"
                    style={{
                      color: expired || alert.alertType === 'LOW_STOCK' ? 'var(--color-error)' : 'var(--color-warning)',
                      background: expired || alert.alertType === 'LOW_STOCK' ? 'var(--color-error-surface)' : 'var(--color-warning-surface)',
                      padding: '0 var(--space-2)',
                      borderRadius: 'var(--radius-sm)',
                      fontWeight: 'var(--font-weight-medium)',
                    }}
                  >
                    {statusLabel(alert.status)}
                  </span>
                </td>
                <td style={td}>{alert.suggestedAction}</td>
                <td style={td}>
                  {alert.status === 'OPEN' && (
                    <Button type="button" onClick={() => acknowledge(alert.id)} isLoading={ackingId === alert.id} disabled={ackingId === alert.id}>
                      Acknowledge
                    </Button>
                  )}
                </td>
              </tr>
            );
          })}
          {alerts.length === 0 && (
            <tr>
              <td colSpan={9} style={{ ...td, textAlign: 'center', color: 'var(--color-foreground-muted)' }}>
                No stock alerts found.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

const selectStyle: CSSProperties = {
  height: 'var(--control-height)',
  padding: '0 var(--space-3)',
  border: '1px solid var(--color-border)',
  borderRadius: 'var(--radius-md)',
  background: 'var(--color-surface)',
  color: 'var(--color-foreground)',
};

const th: CSSProperties = {
  textAlign: 'left',
  padding: 'var(--space-3)',
  borderBottom: '1px solid var(--color-border)',
};

const td: CSSProperties = {
  padding: 'var(--space-3)',
  borderBottom: '1px solid var(--color-border)',
};
