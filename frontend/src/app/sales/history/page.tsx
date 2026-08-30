'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { salesApi, SaleReceipt, SaleSummary } from '@/lib/api/sales';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/Table';

export default function SalesHistoryPage() {
  const { user } = useAuth();
  const canRead = user?.permissions?.includes('SALE_READ') ?? false;
  const canReadReceipt = user?.permissions?.includes('RECEIPT_READ') ?? false;
  const canReprint = user?.permissions?.includes('RECEIPT_REPRINT') ?? false;

  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [sales, setSales] = useState<SaleSummary[]>([]);
  const [receipt, setReceipt] = useState<SaleReceipt | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isWorking, setIsWorking] = useState(false);

  useEffect(() => {
    if (!canRead) {
      return;
    }
    setIsLoading(true);
    salesApi.list({ query: query || undefined, status: status || undefined })
      .then((res) => {
        setSales(res.content ?? []);
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load sales');
      })
      .finally(() => setIsLoading(false));
  }, [canRead, query, status]);

  if (!canRead) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Sales History</h1>
        <p role="status">Access is restricted. You do not have permission to view sales.</p>
      </div>
    );
  }

  const viewReceipt = async (id: string) => {
    setIsWorking(true);
    setError(null);
    try {
      setReceipt(await salesApi.receipt(id));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load receipt');
    } finally {
      setIsWorking(false);
    }
  };

  const reprint = async (id: string) => {
    setIsWorking(true);
    setError(null);
    try {
      setReceipt(await salesApi.reprint(id));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to reprint receipt');
    } finally {
      setIsWorking(false);
    }
  };

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <h1>Sales History</h1>

      {error && (
        <div role="alert" style={{ margin: 'var(--space-4) 0', padding: 'var(--space-4)', background: 'var(--color-error-surface)', color: 'var(--color-error)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-4)' }}>
        <div style={{ flex: '1 1 16rem' }}>
          <Input id="sale-search" label="Receipt number" value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
        <div style={{ flex: '0 1 12rem' }}>
          <Select
            id="sale-status"
            label="Status"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            options={[
              { value: 'COMPLETED', label: 'Completed' },
              { value: 'HELD', label: 'Held' },
            ]}
          />
        </div>
      </div>

      {isLoading ? (
        <p>Loading sales...</p>
      ) : sales.length === 0 ? (
        <div style={{ padding: 'var(--space-6)', textAlign: 'center', backgroundColor: 'var(--color-surface-sunken)', borderRadius: 'var(--radius-md)' }}>
          No sales found.
        </div>
      ) : (
        <Table>
          <Thead>
            <Tr>
              <Th>Receipt</Th>
              <Th>Status</Th>
              <Th>Total</Th>
              <Th> </Th>
            </Tr>
          </Thead>
          <Tbody>
            {sales.map((sale) => (
              <Tr key={sale.id}>
                <Td>{sale.receiptNumber}</Td>
                <Td>{sale.status}</Td>
                <Td>{sale.grandTotal}</Td>
                <Td>
                  {canReadReceipt && (
                    <Button type="button" variant="secondary" onClick={() => void viewReceipt(sale.id)} disabled={isWorking}>
                      View receipt
                    </Button>
                  )}
                  {canReprint && (
                    <Button type="button" variant="secondary" onClick={() => void reprint(sale.id)} disabled={isWorking}>
                      Reprint
                    </Button>
                  )}
                </Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      )}

      {receipt && (
        <section style={{ marginTop: 'var(--space-8)' }} aria-label="Receipt">
          <h2 style={{ fontSize: 'var(--font-size-heading-sm)' }}>Receipt {receipt.receiptNumber}</h2>
          <p>Store: {receipt.storeName}</p>
          <p>Status: {receipt.status}</p>
          <p>Subtotal: {receipt.subtotal}</p>
          <p>Tax: {receipt.taxTotal}</p>
          <p>Total: {receipt.grandTotal}</p>
          {receipt.payments.map((payment, index) => (
            <p key={index}>{payment.paymentMethod}: {payment.amount}</p>
          ))}
        </section>
      )}
    </div>
  );
}
