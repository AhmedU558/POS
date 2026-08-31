'use client';

import { useEffect, useState } from 'react';
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
import { Input, Select, Textarea } from '@/components/ui/Field';
import { ConfirmDialog } from '@/components/ui/Modal';
import { Alert, EmptyState, PermissionRequired } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';
import { ProductPicker } from '@/features/products/ProductPicker';

/*
 * The API takes a signed difference. Asking a stocktaker for "-3" is asking them to do the
 * subtraction and get the sign right; asking for the counted total is asking them what they can
 * see. The screen takes the count and derives the difference.
 */
const REASONS = [
  'Stock count correction',
  'Damaged goods',
  'Expired goods',
  'Theft or loss',
  'Returned to supplier',
  'Internal use',
];

export default function AdjustStockPage() {
  const router = useRouter();
  const toast = useToast();
  const { user } = useAuth();
  const { activeStoreId, activeStore } = useStoreContext();
  const canAdjust = hasPermission(user?.permissions, P.INVENTORY_ADJUST);

  const [product, setProduct] = useState<Product | null>(null);
  const [currentQuantity, setCurrentQuantity] = useState<number | null>(null);
  const [countedQuantity, setCountedQuantity] = useState('');
  const [reason, setReason] = useState(REASONS[0]);
  const [note, setNote] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [confirming, setConfirming] = useState(false);

  // Adjusting blind is how counts drift; the current figure is fetched as soon as one is chosen.
  useEffect(() => {
    if (!product || !activeStoreId) {
      setCurrentQuantity(null);
      return;
    }
    inventoryApi
      .getBalance(product.id, activeStoreId)
      .then((balance) => setCurrentQuantity(balance.quantity))
      .catch(() => setCurrentQuantity(0));
  }, [product, activeStoreId]);

  if (!canAdjust) {
    return (
      <div className="page">
        <PermissionRequired permission={P.INVENTORY_ADJUST} action="Adjusting stock" />
      </div>
    );
  }

  if (!activeStoreId) {
    return (
      <div className="page">
        <Card>
          <EmptyState
            icon="store"
            title="No store selected"
            body="Stock is held per store."
            action={{ label: 'Go to setup', href: '/setup' }}
          />
        </Card>
      </div>
    );
  }

  const counted = Number(countedQuantity);
  const difference =
    currentQuantity !== null && countedQuantity.trim() !== '' && Number.isFinite(counted)
      ? Number((counted - currentQuantity).toFixed(4))
      : null;

  const review = () => {
    const found: Record<string, string> = {};
    if (!product) found.product = 'Choose the product you counted.';
    if (countedQuantity.trim() === '' || !Number.isFinite(counted) || counted < 0) {
      found.countedQuantity = 'Enter the quantity you actually counted.';
    }
    if (!reason.trim()) found.reason = 'Say why the count differs. This is recorded against the adjustment.';
    if (difference !== null && Math.abs(difference) < 0.00005) {
      found.countedQuantity = 'The count already matches the system. Nothing to adjust.';
    }
    setErrors(found);
    if (Object.keys(found).length === 0) setConfirming(true);
  };

  const submit = async () => {
    if (!product || difference === null) return;
    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const balance = await inventoryApi.adjustStock({
        storeId: activeStoreId,
        productId: product.id,
        quantity: difference,
        reason: note.trim() ? `${reason} — ${note.trim()}` : reason,
      });
      toast.success(`${product.name} adjusted to ${formatQuantity(balance.quantity)}.`);
      router.push('/inventory');
    } catch (caught) {
      setSubmitError(errorMessage(caught));
      setConfirming(false);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="page page-narrow">
      <PageHeader
        title="Adjust stock"
        breadcrumbs={[{ label: 'Inventory', href: '/inventory' }, { label: 'Adjust stock' }]}
        description={`Correct what the system thinks is in ${activeStore?.name ?? 'this store'}. Every adjustment is recorded with its reason and who made it.`}
      />

      <form
        className="stack"
        onSubmit={(event) => {
          event.preventDefault();
          review();
        }}
        noValidate
      >
        {submitError && <Alert tone="error">{submitError}</Alert>}

        <Card>
          <CardBody className="stack">
            <ProductPicker
              id="adjust-product"
              required
              selected={product}
              onSelect={(next) => {
                setProduct(next);
                setCountedQuantity('');
                setErrors({});
              }}
              error={errors.product || undefined}
              autoFocus
            />

            {product && (
              <div className="form-grid form-grid--2">
                <div className="field">
                  <span className="field__label">System says</span>
                  <p style={{ fontSize: '1.25rem', fontWeight: 'var(--font-weight-semibold)' }} className="money">
                    {currentQuantity === null ? '…' : formatQuantity(currentQuantity)}
                  </p>
                </div>
                <Input
                  id="adjust-counted"
                  label="You counted"
                  required
                  type="number"
                  min="0"
                  step="any"
                  inputMode="decimal"
                  inputSize="lg"
                  value={countedQuantity}
                  error={errors.countedQuantity}
                  onChange={(event) => setCountedQuantity(event.target.value)}
                />
              </div>
            )}

            {difference !== null && Math.abs(difference) >= 0.00005 && (
              <Alert tone={difference > 0 ? 'info' : 'warning'}>
                This will {difference > 0 ? 'add' : 'remove'} {formatQuantity(Math.abs(difference))}{' '}
                {difference > 0 ? 'to' : 'from'} stock.
              </Alert>
            )}

            <Select
              id="adjust-reason"
              label="Reason"
              required
              placeholder={null}
              value={reason}
              error={errors.reason}
              onChange={(event) => setReason(event.target.value)}
              options={REASONS.map((option) => ({ value: option, label: option }))}
            />
            <Textarea
              id="adjust-note"
              label="Note"
              rows={2}
              value={note}
              hint="Optional detail, kept with the adjustment record."
              onChange={(event) => setNote(event.target.value)}
            />
          </CardBody>
          <CardFooter>
            <Button variant="secondary" onClick={() => router.push('/inventory')} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={!product}>
              Review adjustment
            </Button>
          </CardFooter>
        </Card>
      </form>

      <ConfirmDialog
        open={confirming}
        title="Apply this adjustment?"
        description={
          difference === null || !product
            ? ''
            : `${product.name} will change from ${formatQuantity(currentQuantity ?? 0)} to ${formatQuantity(
                counted
              )} — a ${difference > 0 ? 'gain' : 'loss'} of ${formatQuantity(Math.abs(difference))}. Adjustments cannot be undone; a further adjustment would be needed to reverse it.`
        }
        confirmLabel="Apply adjustment"
        destructive={difference !== null && difference < 0}
        isWorking={isSubmitting}
        onCancel={() => setConfirming(false)}
        onConfirm={() => void submit()}
      />
    </div>
  );
}
