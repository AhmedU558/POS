'use client';

import { FormEvent, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { purchaseOrdersApi } from '@/lib/api/purchase-orders';
import { suppliersApi, Supplier } from '@/lib/api/suppliers';
import { getProducts } from '@/lib/api/catalog';
import { Product } from '@/types/catalog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';

export default function NewPurchaseOrderPage() {
  const router = useRouter();
  const { user } = useAuth();
  const canWrite = user?.permissions?.includes('PURCHASE_WRITE') ?? false;

  const [poNumber, setPoNumber] = useState('');
  const [supplierId, setSupplierId] = useState('');
  const [notes, setNotes] = useState('');
  const [productId, setProductId] = useState('');
  const [quantity, setQuantity] = useState('1');
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!canWrite) {
      return;
    }
    Promise.all([
      suppliersApi.list(undefined, 'true'),
      getProducts({ size: 100 }),
    ]).then(([supplierPage, catalog]) => {
      setSuppliers(supplierPage.content ?? []);
      setProducts(Array.isArray(catalog) ? catalog : ((catalog as { content?: Product[] }).content ?? []));
    }).catch((err: unknown) => {
      setError(err instanceof Error ? err.message : 'Failed to load catalogs');
    });
  }, [canWrite]);

  if (!canWrite) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Create Purchase Order</h1>
        <p role="status">Access is restricted. You do not have permission to create purchase orders.</p>
      </div>
    );
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setIsSubmitting(true);
    setError(null);
    try {
      const created = await purchaseOrdersApi.create({
        poNumber,
        supplierId,
        notes: notes || null,
        items: [{ productId, quantity: Number(quantity) }],
      });
      router.push('/purchase-orders/' + created.id);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to create purchase order');
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <h1>Create Purchase Order</h1>
      {error && (
        <div role="alert" style={{ margin: 'var(--space-4) 0', padding: 'var(--space-4)', background: 'var(--color-error-surface)', color: 'var(--color-error)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}
      <form onSubmit={onSubmit}>
        <Input id="po-number" label="PO number" value={poNumber} onChange={(e) => setPoNumber(e.target.value)} required />
        <Select
          id="po-supplier"
          label="Supplier"
          value={supplierId}
          onChange={(e) => setSupplierId(e.target.value)}
          required
          options={suppliers.map((supplier) => ({ value: supplier.id, label: supplier.name }))}
        />
        <Select
          id="po-product"
          label="Product"
          value={productId}
          onChange={(e) => setProductId(e.target.value)}
          required
          options={products.map((product) => ({ value: product.id, label: product.sku + ' — ' + product.name }))}
        />
        <Input id="po-qty" label="Quantity" type="number" min="0.0001" step="any" value={quantity} onChange={(e) => setQuantity(e.target.value)} required />
        <Input id="po-notes" label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} />
        <div style={{ display: 'flex', gap: 'var(--space-3)' }}>
          <Button type="submit" isLoading={isSubmitting} disabled={isSubmitting}>Save</Button>
          <Button type="button" variant="secondary" onClick={() => router.push('/purchase-orders')}>Cancel</Button>
        </div>
      </form>
    </div>
  );
}
