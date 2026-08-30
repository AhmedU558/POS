'use client';

import { FormEvent, useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { purchaseOrdersApi, PurchaseOrder } from '@/lib/api/purchase-orders';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/Table';

function statusVariant(status: string) {
  if (status === 'SUBMITTED') return 'success';
  if (status === 'CANCELLED') return 'error';
  return 'pending';
}

export default function PurchaseOrderDetailPage() {
  const { id } = useParams() as { id: string };
  const router = useRouter();
  const { user } = useAuth();
  const canRead = user?.permissions?.includes('PURCHASE_READ') ?? false;
  const canWrite = user?.permissions?.includes('PURCHASE_WRITE') ?? false;
  const canApprove = user?.permissions?.includes('PURCHASE_APPROVE') ?? false;
  const canReceive = user?.permissions?.includes('INVENTORY_RECEIVE') ?? false;

  const [order, setOrder] = useState<PurchaseOrder | null>(null);
  const [poNumber, setPoNumber] = useState('');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!canRead || !id) {
      setIsLoading(false);
      return;
    }
    purchaseOrdersApi.get(id)
      .then((loaded) => {
        setOrder(loaded);
        setPoNumber(loaded.poNumber);
        setNotes(loaded.notes ?? '');
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load purchase order');
      })
      .finally(() => setIsLoading(false));
  }, [canRead, id]);

  if (!canRead) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Purchase Order</h1>
        <p role="status">Access is restricted. You do not have permission to view purchase orders.</p>
      </div>
    );
  }

  const isDraft = order?.status === 'DRAFT';

  const onSave = async (event: FormEvent) => {
    event.preventDefault();
    if (!order) {
      return;
    }
    setIsSubmitting(true);
    setError(null);
    try {
      const updated = await purchaseOrdersApi.update(id, {
        poNumber,
        supplierId: order.supplierId,
        notes: notes || null,
        items: order.items.map((item) => ({ productId: item.productId, quantity: item.quantity })),
      });
      setOrder(updated);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to update purchase order');
    } finally {
      setIsSubmitting(false);
    }
  };

  const runAction = async (action: 'submit' | 'cancel') => {
    setIsSubmitting(true);
    setError(null);
    try {
      const updated = action === 'submit'
        ? await purchaseOrdersApi.submit(id)
        : await purchaseOrdersApi.cancel(id);
      setOrder(updated);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to update purchase order');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <Button type="button" variant="secondary" onClick={() => router.push('/purchase-orders')} style={{ marginBottom: 'var(--space-4)' }}>
        Back to purchase orders
      </Button>
      <h1>Purchase Order</h1>
      {order && <Badge variant={statusVariant(order.status)}>{order.status}</Badge>}

      {error && (
        <div role="alert" style={{ margin: 'var(--space-4) 0', padding: 'var(--space-4)', background: 'var(--color-error-surface)', color: 'var(--color-error)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}

      {isLoading || !order ? (
        <p>Loading purchase order...</p>
      ) : (
        <>
          <p>Supplier: {order.supplierName}</p>
          <form onSubmit={onSave}>
            <Input id="po-number" label="PO number" value={poNumber} onChange={(e) => setPoNumber(e.target.value)} required disabled={!canWrite || !isDraft} />
            <Input id="po-notes" label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} disabled={!canWrite || !isDraft} />
            {canWrite && isDraft && (
              <Button type="submit" isLoading={isSubmitting} disabled={isSubmitting}>Save</Button>
            )}
          </form>

          <h2 style={{ fontSize: 'var(--font-size-heading-sm)', margin: 'var(--space-8) 0 var(--space-4)' }}>Line items</h2>
          {order.items.length === 0 ? (
            <div style={{ padding: 'var(--space-6)', textAlign: 'center', backgroundColor: 'var(--color-surface-sunken)', borderRadius: 'var(--radius-md)' }}>
              No line items.
            </div>
          ) : (
            <Table>
              <Thead>
                <Tr>
                  <Th>SKU</Th>
                  <Th>Name</Th>
                  <Th>Quantity</Th>
                </Tr>
              </Thead>
              <Tbody>
                {order.items.map((item) => (
                  <Tr key={item.id}>
                    <Td>{item.sku}</Td>
                    <Td>{item.name}</Td>
                    <Td>{item.quantity}</Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
          )}

          {canReceive && order.status === 'SUBMITTED' && (
            <div style={{ marginTop: 'var(--space-6)' }}>
              <Button type="button" onClick={() => router.push('/purchase-orders/' + id + '/receive')}>
                Receive
              </Button>
            </div>
          )}

          {canApprove && isDraft && (
            <div style={{ display: 'flex', gap: 'var(--space-3)', marginTop: 'var(--space-6)' }}>
              <Button type="button" disabled={isSubmitting} onClick={() => void runAction('submit')}>Submit</Button>
              <Button type="button" variant="secondary" disabled={isSubmitting} onClick={() => void runAction('cancel')}>Cancel order</Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
