'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { inventoryApi } from '@/lib/api/inventory';
import { getProducts } from '@/lib/api/catalog';
import { Product } from '@/types/catalog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

function asProductList(res: unknown): Product[] {
  if (Array.isArray(res)) {
    return res;
  }
  if (res && typeof res === 'object' && 'content' in res && Array.isArray((res as { content: unknown }).content)) {
    return (res as { content: Product[] }).content;
  }
  return [];
}

export default function StockReceivingPage() {
  const router = useRouter();
  const { user } = useAuth();
  const storeId = user?.storeIds?.[0];
  const canReceive = user?.permissions?.includes('INVENTORY_RECEIVE') ?? false;

  const [products, setProducts] = useState<Product[]>([]);
  const [selectedProductId, setSelectedProductId] = useState('');
  const [quantity, setQuantity] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [resultingQuantity, setResultingQuantity] = useState<number | null>(null);

  useEffect(() => {
    if (!canReceive) {
      return;
    }
    getProducts({ isActive: true, size: 50 }).then((res) => {
      setProducts(asProductList(res));
    });
  }, [canReceive]);

  const selectedProduct = products.find((p) => p.id === selectedProductId);

  const handleReview = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const parsed = Number(quantity);
    if (!storeId || !selectedProductId || !quantity || Number.isNaN(parsed) || parsed <= 0) {
      setError('Select a product and enter a quantity greater than zero.');
      return;
    }
    setConfirming(true);
  };

  const handleConfirm = async () => {
    if (!storeId || !selectedProductId) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const result = await inventoryApi.receiveStock({
        storeId,
        productId: selectedProductId,
        quantity: Number(quantity),
      });
      setResultingQuantity(result.quantity);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to receive stock');
      setConfirming(false);
    } finally {
      setLoading(false);
    }
  };

  if (!canReceive) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Receive Stock</h1>
        <p role="status">Access is restricted. You do not have permission to receive stock.</p>
      </div>
    );
  }

  if (!storeId) {
    return <div style={{ padding: 'var(--space-6)' }}>No store context available.</div>;
  }

  if (resultingQuantity !== null) {
    return (
      <div style={{ padding: 'var(--space-6)', maxWidth: '40rem', margin: '0 auto' }}>
        <h1>Receive Stock</h1>
        <div
          role="status"
          style={{
            marginTop: 'var(--space-4)',
            padding: 'var(--space-4)',
            background: 'var(--color-success-surface)',
            color: 'var(--color-success)',
            borderRadius: 'var(--radius-md)',
          }}
        >
          Receipt confirmed. On-hand quantity is now {resultingQuantity}.
        </div>
        <div style={{ marginTop: 'var(--space-4)' }}>
          <Button type="button" onClick={() => router.push('/inventory')}>
            Back to Inventory
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: '40rem', margin: '0 auto' }}>
      <h1>Receive Stock</h1>

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

      {!confirming ? (
        <form onSubmit={handleReview} style={{ marginTop: 'var(--space-6)' }}>
          <div style={{ marginBottom: 'var(--space-4)' }}>
            <label htmlFor="product" style={{ display: 'block', fontWeight: 'var(--font-weight-medium)', marginBottom: 'var(--space-2)' }}>
              Product
            </label>
            <select
              id="product"
              value={selectedProductId}
              onChange={(e) => setSelectedProductId(e.target.value)}
              required
              style={{
                width: '100%',
                height: 'var(--control-height)',
                padding: '0 var(--space-3)',
                border: '1px solid var(--color-border)',
                borderRadius: 'var(--radius-md)',
                background: 'var(--color-surface)',
                color: 'var(--color-foreground)',
              }}
            >
              <option value="">Select a product...</option>
              {products.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} ({p.sku})
                </option>
              ))}
            </select>
          </div>

          <Input
            id="quantity"
            label="Quantity"
            type="number"
            min="0.0001"
            step="0.0001"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            required
          />

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-3)' }}>
            <Button type="button" variant="secondary" onClick={() => router.back()}>
              Cancel
            </Button>
            <Button type="submit">Review Receipt</Button>
          </div>
        </form>
      ) : (
        <div style={{ marginTop: 'var(--space-6)' }}>
          <p>Confirm receipt of {quantity} of {selectedProduct ? `${selectedProduct.name} (${selectedProduct.sku})` : 'the selected product'}.</p>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-3)', marginTop: 'var(--space-4)' }}>
            <Button type="button" variant="secondary" onClick={() => setConfirming(false)} disabled={loading}>
              Back
            </Button>
            <Button type="button" onClick={handleConfirm} isLoading={loading} disabled={loading}>
              Confirm Receipt
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
