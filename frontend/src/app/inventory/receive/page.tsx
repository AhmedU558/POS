'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { useStoreContext } from '@/features/session/StoreContext';
import { inventoryApi } from '@/lib/api/inventory';
import { Product } from '@/types/catalog';
import { errorMessage, formatQuantity } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardFooter } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Field';
import { Alert, EmptyState, PermissionRequired } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';
import { ProductPicker } from '@/features/products/ProductPicker';

/**
 * Receiving stock into a store.
 *
 * Batch and expiry fields appear only for products configured to track them, so the form asks for
 * what this particular product needs and nothing else.
 */
export default function ReceiveStockPage() {
  const router = useRouter();
  const toast = useToast();
  const { user } = useAuth();
  const { activeStoreId, activeStore } = useStoreContext();
  const canReceive = hasPermission(user?.permissions, P.INVENTORY_RECEIVE);

  const [product, setProduct] = useState<Product | null>(null);
  const [quantity, setQuantity] = useState('');
  const [batchNumber, setBatchNumber] = useState('');
  const [expirationDate, setExpirationDate] = useState('');
  const [manufacturingDate, setManufacturingDate] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [received, setReceived] = useState<{ name: string; quantity: number } | null>(null);

  if (!canReceive) {
    return (
      <div className="page">
        <PermissionRequired permission={P.INVENTORY_RECEIVE} action="Receiving stock" />
      </div>
    );
  }

  if (!activeStoreId) {
    return (
      <div className="page">
        <Card>
          <EmptyState
            icon="store"
            title="No store to receive into"
            body="Stock is held per store. Create one first."
            action={{ label: 'Go to setup', href: '/setup' }}
          />
        </Card>
      </div>
    );
  }

  const needsBatch = product?.trackBatch || product?.trackExpiry;

  const submit = async () => {
    const found: Record<string, string> = {};
    const amount = Number(quantity);
    if (!product) found.product = 'Choose the product you are receiving.';
    if (quantity.trim() === '' || !Number.isFinite(amount) || amount <= 0) {
      found.quantity = 'Enter how many you received.';
    }
    if (needsBatch && !batchNumber.trim()) {
      found.batchNumber = 'This product is tracked by batch, so a batch number is required.';
    }
    if (product?.trackExpiry && !expirationDate) {
      found.expirationDate = 'This product is tracked by expiry date.';
    }
    setErrors(found);
    if (Object.keys(found).length > 0 || !product) {
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const balance = await inventoryApi.receiveStock({
        storeId: activeStoreId,
        productId: product.id,
        quantity: amount,
        ...(needsBatch
          ? {
              batchNumber: batchNumber.trim(),
              expirationDate: expirationDate || null,
              manufacturingDate: manufacturingDate || null,
            }
          : {}),
      });
      toast.success(`${formatQuantity(amount)} × ${product.name} received.`);
      setReceived({ name: product.name, quantity: balance.quantity });
      setProduct(null);
      setQuantity('');
      setBatchNumber('');
      setExpirationDate('');
      setManufacturingDate('');
    } catch (caught) {
      setSubmitError(errorMessage(caught));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="page page-narrow">
      <PageHeader
        title="Receive stock"
        breadcrumbs={[{ label: 'Inventory', href: '/inventory' }, { label: 'Receive stock' }]}
        description={`Add stock to ${activeStore?.name ?? 'this store'}. Use this for deliveries that did not come through a purchase order — otherwise receive against the order so the two stay in step.`}
      />

      <form
        className="stack"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
        noValidate
      >
        {submitError && <Alert tone="error">{submitError}</Alert>}
        {received && (
          <Alert tone="success" title={`${received.name} received`}>
            Stock in {activeStore?.name ?? 'this store'} is now {formatQuantity(received.quantity)}. Receive another
            below, or go back to the stock list.
          </Alert>
        )}

        <Card>
          <CardBody className="stack">
            <ProductPicker
              id="receive-product"
              required
              selected={product}
              onSelect={(next) => {
                setProduct(next);
                setErrors((current) => ({ ...current, product: '' }));
              }}
              error={errors.product || undefined}
              autoFocus
            />

            <Input
              id="receive-quantity"
              label="Quantity received"
              required
              type="number"
              min="0"
              step="any"
              inputMode="decimal"
              inputSize="lg"
              value={quantity}
              error={errors.quantity}
              onChange={(event) => setQuantity(event.target.value)}
            />

            {needsBatch && (
              <div className="form-section">
                <h2 className="form-section__title">Batch details</h2>
                <p className="form-section__description">
                  {product?.name} is tracked by {product?.trackExpiry ? 'batch and expiry date' : 'batch'}.
                </p>
                <div className="form-grid">
                  <Input
                    id="receive-batch"
                    label="Batch number"
                    required
                    value={batchNumber}
                    error={errors.batchNumber}
                    onChange={(event) => setBatchNumber(event.target.value)}
                  />
                  <Input
                    id="receive-expiry"
                    label="Expiry date"
                    required={product?.trackExpiry}
                    type="date"
                    value={expirationDate}
                    error={errors.expirationDate}
                    onChange={(event) => setExpirationDate(event.target.value)}
                  />
                  <Input
                    id="receive-manufactured"
                    label="Manufactured on"
                    type="date"
                    value={manufacturingDate}
                    hint="Optional."
                    onChange={(event) => setManufacturingDate(event.target.value)}
                  />
                </div>
              </div>
            )}
          </CardBody>
          <CardFooter>
            <Button variant="secondary" onClick={() => router.push('/inventory')} disabled={isSubmitting}>
              Back to stock
            </Button>
            <Button type="submit" isLoading={isSubmitting}>
              Receive stock
            </Button>
          </CardFooter>
        </Card>
      </form>
    </div>
  );
}
