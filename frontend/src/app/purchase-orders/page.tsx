'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { purchaseOrdersApi, PurchaseOrder } from '@/lib/api/purchase-orders';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Badge } from '@/components/ui/Badge';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/Table';

function statusVariant(status: string) {
  if (status === 'SUBMITTED') return 'success';
  if (status === 'CANCELLED') return 'error';
  return 'pending';
}

export default function PurchaseOrdersPage() {
  const router = useRouter();
  const { user } = useAuth();
  const canRead = user?.permissions?.includes('PURCHASE_READ') ?? false;
  const canWrite = user?.permissions?.includes('PURCHASE_WRITE') ?? false;

  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [orders, setOrders] = useState<PurchaseOrder[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!canRead) {
      return;
    }
    setIsLoading(true);
    purchaseOrdersApi.list(query || undefined, status || undefined)
      .then((res) => {
        setOrders(res.content ?? []);
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load purchase orders');
      })
      .finally(() => setIsLoading(false));
  }, [canRead, query, status]);

  if (!canRead) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Purchase Orders</h1>
        <p role="status">Access is restricted. You do not have permission to view purchase orders.</p>
      </div>
    );
  }

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-6)' }}>
        <h1>Purchase Orders</h1>
        {canWrite && (
          <Button type="button" onClick={() => router.push('/purchase-orders/new')}>Create Purchase Order</Button>
        )}
      </div>

      {error && (
        <div role="alert" style={{ marginBottom: 'var(--space-4)', padding: 'var(--space-4)', background: 'var(--color-error-surface)', color: 'var(--color-error)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-4)' }}>
        <div style={{ flex: '1 1 16rem' }}>
          <Input id="po-search" label="Search" placeholder="PO number" value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
        <div style={{ flex: '0 1 12rem' }}>
          <Select
            id="po-status"
            label="Status"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            options={[
              { value: 'DRAFT', label: 'Draft' },
              { value: 'SUBMITTED', label: 'Submitted' },
              { value: 'CANCELLED', label: 'Cancelled' },
            ]}
          />
        </div>
      </div>

      {isLoading ? (
        <p>Loading purchase orders...</p>
      ) : orders.length === 0 ? (
        <div style={{ padding: 'var(--space-8)', textAlign: 'center', backgroundColor: 'var(--color-surface-sunken)', borderRadius: 'var(--radius-md)' }}>
          No purchase orders found.
        </div>
      ) : (
        <Table>
          <Thead>
            <Tr>
              <Th>PO number</Th>
              <Th>Supplier</Th>
              <Th>Status</Th>
              <Th> </Th>
            </Tr>
          </Thead>
          <Tbody>
            {orders.map((order) => (
              <Tr key={order.id}>
                <Td>{order.poNumber}</Td>
                <Td>{order.supplierName}</Td>
                <Td>
                  <Badge variant={statusVariant(order.status)}>{order.status}</Badge>
                </Td>
                <Td>
                  <Button type="button" variant="secondary" onClick={() => router.push('/purchase-orders/' + order.id)}>
                    {canWrite ? 'Manage' : 'View'}
                  </Button>
                </Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      )}
    </div>
  );
}
