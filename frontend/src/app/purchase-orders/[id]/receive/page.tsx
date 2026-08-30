'use client';

import { FormEvent, useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { purchaseOrdersApi, PurchaseOrder } from '@/lib/api/purchase-orders';
import { goodsReceiptsApi } from '@/lib/api/goods-receipts';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

export default function ReceivePurchaseOrderPage() {
  const { id } = useParams() as { id: string };
  const router = useRouter();
  const { user } = useAuth();
  const canReceive = user?.permissions?.includes('INVENTORY_RECEIVE') ?? false;
  const storeId = user?.storeIds?.[0];

  const [order, setOrder] = useState<PurchaseOrder | null>(null);
  const [quantities, setQuantities] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!canReceive || !id) {
      setIsLoading(false);
      return;
    }
    purchaseOrdersApi.get(id)
      .then((loaded) => {
        setOrder(loaded);
        const next: Record<string, string> = {};
        loaded.items.forEach((item) => {
          next[item.productId] = String(item.quantity);
        });
        setQuantities(next);
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load purchase order');
      })
      .finally(() => setIsLoading(false));
  }, [canReceive, id]);

  if (!canReceive) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Receive Purchase Order</h1>
        <p role="status">Access is restricted. You do not have permission to receive stock.</p>
      </div>
    );
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!order || !storeId) {
      setError('A store is required to receive stock.');
      return;
    }
    setIsSubmitting(true);
    setError(null);
    try {
      const items = order.items
        .map((item) => ({
          productId: item.productId,
          quantity: Number(quantities[item.productId]),
        }))
        .filter((item) => item.quantity > 0);
      await goodsReceiptsApi.create({
        purchaseOrderId: order.id,
        storeId,
        items,
      });
      router.push('/purchase-orders/' + order.id);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to receive purchase order');
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <Button type="button" variant="secondary" onClick={() => router.push('/purchase-orders/' + id)} style={{ marginBottom: 'var(--space-4)' }}>
        Back to purchase order
      </Button>
      <h1>Receive Purchase Order</h1>
      {order && <p>PO {order.poNumber} — ordered vs this receipt</p>}

      {error && (
        <div role="alert" style={{ margin: 'var(--space-4) 0', padding: 'var(--space-4)', background: 'var(--color-error-surface)', color: 'var(--color-error)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}

      {isLoading || !order ? (
        <p>Loading purchase order...</p>
      ) : (
        <form onSubmit={onSubmit}>
          {order.items.map((item) => (
            <Input
              key={item.id}
              id={'qty-' + item.productId}
              label={item.sku + ' — ' + item.name + ' (ordered ' + item.quantity + ')'}
              type="number"
              min="0.0001"
              step="any"
              value={quantities[item.productId] ?? ''}
              onChange={(e) => setQuantities((current) => ({ ...current, [item.productId]: e.target.value }))}
              required
            />
          ))}
          <Button type="submit" isLoading={isSubmitting} disabled={isSubmitting || !storeId}>Confirm receipt</Button>
        </form>
      )}
    </div>
  );
}
