'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { inventoryApi, InventoryBatch, InventoryReportRow, InventoryTransaction } from '@/lib/api/inventory';

type ReportKind = 'stock' | 'movements' | 'expiry';

export default function InventoryReportsPage() {
  const { user } = useAuth();
  const storeId = user?.storeIds?.[0];
  const canReport = user?.permissions?.includes('REPORT_INVENTORY') ?? false;

  const [kind, setKind] = useState<ReportKind>('stock');
  const [lowStockOnly, setLowStockOnly] = useState(false);
  const [days, setDays] = useState(7);
  const [stock, setStock] = useState<InventoryReportRow[]>([]);
  const [movements, setMovements] = useState<InventoryTransaction[]>([]);
  const [expiry, setExpiry] = useState<InventoryBatch[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!canReport || !storeId) {
      return;
    }
    const load =
      kind === 'stock'
        ? inventoryApi.getInventoryReport(storeId, 0, 50, lowStockOnly).then((res) => setStock(res.content ?? []))
        : kind === 'movements'
          ? inventoryApi.getMovementReport(storeId).then((res) => setMovements(res.content ?? []))
          : inventoryApi.getExpiryReport(storeId, 0, 50, days).then((res) => setExpiry(res.content ?? []));
    load.catch((err: unknown) => {
      setError(err instanceof Error ? err.message : 'Failed to load report');
    });
  }, [canReport, storeId, kind, lowStockOnly, days]);

  if (!canReport) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Inventory Reports</h1>
        <p role="status">Access is restricted. You do not have permission to view inventory reports.</p>
      </div>
    );
  }

  if (!storeId) {
    return <div style={{ padding: 'var(--space-6)' }}>No store context available.</div>;
  }

  return (
    <div style={{ padding: 'var(--space-6)' }}>
      <h1>Inventory Reports</h1>

      {error && (
        <div role="alert" style={{ marginTop: 'var(--space-4)', padding: 'var(--space-4)', background: 'var(--color-error-surface)', color: 'var(--color-error)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-4)', margin: 'var(--space-4) 0' }}>
        <div>
          <label htmlFor="report-kind" style={{ display: 'block', fontWeight: 'var(--font-weight-medium)', marginBottom: 'var(--space-2)' }}>Report</label>
          <select id="report-kind" value={kind} onChange={(e) => setKind(e.target.value as ReportKind)} style={selectStyle}>
            <option value="stock">Current stock</option>
            <option value="movements">Stock movements</option>
            <option value="expiry">Expiry</option>
          </select>
        </div>
        {kind === 'stock' && (
          <label htmlFor="low-stock-only" style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
            <input id="low-stock-only" type="checkbox" checked={lowStockOnly} onChange={(e) => setLowStockOnly(e.target.checked)} />
            Low stock only
          </label>
        )}
        {kind === 'expiry' && (
          <div>
            <label htmlFor="report-days" style={{ display: 'block', fontWeight: 'var(--font-weight-medium)', marginBottom: 'var(--space-2)' }}>Window (days)</label>
            <select id="report-days" value={days} onChange={(e) => setDays(Number(e.target.value))} style={selectStyle}>
              <option value={7}>7</option>
              <option value={30}>30</option>
            </select>
          </div>
        )}
      </div>

      {kind === 'stock' && (
        <table style={tableStyle}>
          <thead>
            <tr>
              <th style={th}>Product</th>
              <th style={{ ...th, textAlign: 'right' }}>Quantity</th>
              <th style={{ ...th, textAlign: 'right' }}>Minimum</th>
              <th style={th}>Below minimum</th>
            </tr>
          </thead>
          <tbody>
            {stock.map((row) => (
              <tr key={row.productId}>
                <td style={td}>{row.productName} ({row.sku})</td>
                <td style={{ ...td, textAlign: 'right' }}>{row.quantity}</td>
                <td style={{ ...td, textAlign: 'right' }}>{row.minStock}</td>
                <td style={td}>{row.belowMinimum ? 'Yes' : 'No'}</td>
              </tr>
            ))}
            {stock.length === 0 && <tr><td colSpan={4} style={{ ...td, textAlign: 'center' }}>No inventory rows.</td></tr>}
          </tbody>
        </table>
      )}

      {kind === 'movements' && (
        <table style={tableStyle}>
          <thead>
            <tr>
              <th style={th}>Product</th>
              <th style={th}>Type</th>
              <th style={{ ...th, textAlign: 'right' }}>Quantity</th>
              <th style={th}>Reason</th>
            </tr>
          </thead>
          <tbody>
            {movements.map((row) => (
              <tr key={row.id}>
                <td style={td}>{row.productName}</td>
                <td style={td}>{row.transactionType}</td>
                <td style={{ ...td, textAlign: 'right' }}>{row.quantity}</td>
                <td style={td}>{row.reason || '—'}</td>
              </tr>
            ))}
            {movements.length === 0 && <tr><td colSpan={4} style={{ ...td, textAlign: 'center' }}>No movements.</td></tr>}
          </tbody>
        </table>
      )}

      {kind === 'expiry' && (
        <table style={tableStyle}>
          <thead>
            <tr>
              <th style={th}>Product</th>
              <th style={th}>Batch</th>
              <th style={th}>Expiry date</th>
              <th style={th}>Status</th>
            </tr>
          </thead>
          <tbody>
            {expiry.map((row) => (
              <tr key={row.id}>
                <td style={td}>{row.productName} ({row.sku})</td>
                <td style={td}>{row.batchNumber}</td>
                <td style={td}>{row.expirationDate ?? '—'}</td>
                <td style={td}>{row.status}</td>
              </tr>
            ))}
            {expiry.length === 0 && <tr><td colSpan={4} style={{ ...td, textAlign: 'center' }}>No expiring batches.</td></tr>}
          </tbody>
        </table>
      )}
    </div>
  );
}

const selectStyle = {
  height: 'var(--control-height)',
  padding: '0 var(--space-3)',
  border: '1px solid var(--color-border)',
  borderRadius: 'var(--radius-md)',
  background: 'var(--color-surface)',
  color: 'var(--color-foreground)',
} as const;

const tableStyle = { width: '100%', borderCollapse: 'collapse', background: 'var(--color-surface)' } as const;
const th = { textAlign: 'left' as const, padding: 'var(--space-3)', borderBottom: '1px solid var(--color-border)' };
const td = { padding: 'var(--space-3)', borderBottom: '1px solid var(--color-border)' };
