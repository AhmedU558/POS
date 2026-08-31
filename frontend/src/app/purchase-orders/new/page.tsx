'use client';

import { useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { Supplier, suppliersApi } from '@/lib/api/suppliers';
import { purchaseOrdersApi } from '@/lib/api/purchase-orders';
import { ApiError } from '@/lib/api/http';
import { Product } from '@/types/catalog';
import { errorMessage, formatMoney, formatQuantity } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardFooter, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input, Select, Textarea } from '@/components/ui/Field';
import { Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { Alert, EmptyState, LoadingState, PermissionRequired } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';
import { ProductPicker } from '@/features/products/ProductPicker';

interface Line {
  product: Product;
  quantity: string;
}

/** A draft order: pick the supplier, add lines, save. Submitting is a separate, deliberate step. */
export default function NewPurchaseOrderPage() {
  const router = useRouter();
  const params = useSearchParams();
  const toast = useToast();
  const { user } = useAuth();
  const canWrite = hasPermission(user?.permissions, P.PURCHASE_WRITE);

  const [suppliers, setSuppliers] = useState<Supplier[] | null>(null);
  const [supplierId, setSupplierId] = useState(params?.get('supplierId') ?? '');
  const [poNumber, setPoNumber] = useState(suggestPoNumber());
  const [notes, setNotes] = useState('');
  const [lines, setLines] = useState<Line[]>([]);
  const [picked, setPicked] = useState<Product | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!canWrite) return;
    suppliersApi
      .list({ isActive: true, size: 200, sort: 'name,asc' })
      .then((page) => setSuppliers(page.content))
      .catch(() => setSuppliers([]));
  }, [canWrite]);

  if (!canWrite) {
    return (
      <div className="page">
        <PermissionRequired permission={P.PURCHASE_WRITE} action="Raising purchase orders" />
      </div>
    );
  }

  if (suppliers === null) {
    return (
      <div className="page">
        <LoadingState label="Loading suppliers…" />
      </div>
    );
  }

  if (suppliers.length === 0) {
    return (
      <div className="page page-narrow">
        <PageHeader
          title="New purchase order"
          breadcrumbs={[{ label: 'Purchase orders', href: '/purchase-orders' }, { label: 'New' }]}
        />
        <Card>
          <CardBody>
            <EmptyState
              icon="suppliers"
              title="You need a supplier first"
              body="A purchase order is always raised against a supplier. Add one, then come back here."
              action={{ label: 'Add supplier', href: '/suppliers/new' }}
            />
          </CardBody>
        </Card>
      </div>
    );
  }

  const addLine = () => {
    if (!picked) return;
    setLines((current) =>
      current.some((line) => line.product.id === picked.id)
        ? current
        : [...current, { product: picked, quantity: '1' }]
    );
    setPicked(null);
    setErrors((current) => ({ ...current, lines: '' }));
  };

  const estimatedCost = lines.reduce(
    (total, line) => total + (Number(line.quantity) || 0) * (Number(line.product.purchasePrice) || 0),
    0
  );

  const submit = async () => {
    const found: Record<string, string> = {};
    if (!supplierId) found.supplierId = 'Choose the supplier you are ordering from.';
    if (!poNumber.trim()) found.poNumber = 'Give the order a reference number.';
    if (lines.length === 0) found.lines = 'Add at least one product to the order.';
    if (lines.some((line) => !(Number(line.quantity) > 0))) {
      found.lines = 'Every line needs a quantity greater than zero.';
    }
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const created = await purchaseOrdersApi.create({
        poNumber: poNumber.trim(),
        supplierId,
        notes: notes.trim() || null,
        items: lines.map((line) => ({ productId: line.product.id, quantity: Number(line.quantity) })),
      });
      toast.success(`${created.poNumber} saved as a draft. Submit it when you are ready to send it.`);
      router.push(`/purchase-orders/${created.id}`);
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'CONFLICT') {
        setErrors({ poNumber: 'Another order already uses this number.' });
        setSubmitError('That PO number is taken. Pick a different one.');
      } else {
        setSubmitError(errorMessage(caught));
      }
      setIsSubmitting(false);
    }
  };

  return (
    <div className="page page-narrow">
      <PageHeader
        title="New purchase order"
        breadcrumbs={[{ label: 'Purchase orders', href: '/purchase-orders' }, { label: 'New' }]}
        description="This saves as a draft. Nothing reaches your stock until the goods are received against it."
      />

      <form
        className="stack-lg stack"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
        noValidate
      >
        {submitError && <Alert tone="error">{submitError}</Alert>}

        <Card>
          <CardBody className="stack">
            <div className="form-grid form-grid--2">
              <Select
                id="po-supplier"
                label="Supplier"
                required
                placeholder="Choose a supplier"
                value={supplierId}
                error={errors.supplierId}
                onChange={(event) => setSupplierId(event.target.value)}
                options={suppliers.map((supplier) => ({ value: supplier.id, label: supplier.name }))}
              />
              <Input
                id="po-number"
                label="PO number"
                required
                value={poNumber}
                error={errors.poNumber}
                hint="Your reference for this order."
                onChange={(event) => setPoNumber(event.target.value)}
              />
            </div>
            <Textarea
              id="po-notes"
              label="Notes"
              rows={2}
              value={notes}
              hint="Optional. Delivery instructions, agreed terms, anything worth recording."
              onChange={(event) => setNotes(event.target.value)}
            />
          </CardBody>
        </Card>

        <Card flush>
          <CardHeader title="What you are ordering" />
          <CardBody>
            <div className="row row-wrap">
              <div className="grow">
                <ProductPicker id="po-product" label="Add a product" selected={picked} onSelect={setPicked} />
              </div>
              <Button variant="secondary" disabled={!picked} onClick={addLine}>
                Add to order
              </Button>
            </div>
            {errors.lines && <p className="field__error">{errors.lines}</p>}
          </CardBody>

          {lines.length === 0 ? (
            <EmptyState icon="box" title="No lines yet" body="Search for a product above and add it to the order." />
          ) : (
            <Table>
              <Thead>
                <Tr>
                  <Th>Product</Th>
                  <Th className="table__num">Quantity</Th>
                  <Th className="table__num">Est. cost</Th>
                  <Th className="table__actions" />
                </Tr>
              </Thead>
              <Tbody>
                {lines.map((line) => (
                  <Tr key={line.product.id}>
                    <Td>
                      <span className="table__primary">{line.product.name}</span>
                      <div className="table__secondary mono">{line.product.sku}</div>
                    </Td>
                    <Td className="table__num">
                      <input
                        className="control"
                        style={{ width: '7rem', textAlign: 'right' }}
                        type="number"
                        min="0"
                        step="any"
                        value={line.quantity}
                        aria-label={`Quantity of ${line.product.name}`}
                        onChange={(event) =>
                          setLines((current) =>
                            current.map((item) =>
                              item.product.id === line.product.id ? { ...item, quantity: event.target.value } : item
                            )
                          )
                        }
                      />
                    </Td>
                    <Td className="table__num">
                      {formatMoney((Number(line.quantity) || 0) * (Number(line.product.purchasePrice) || 0))}
                    </Td>
                    <Td className="table__actions">
                      <Button
                        variant="ghost"
                        size="sm"
                        icon="trash"
                        aria-label={`Remove ${line.product.name}`}
                        onClick={() =>
                          setLines((current) => current.filter((item) => item.product.id !== line.product.id))
                        }
                      />
                    </Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
          )}

          <CardFooter>
            <span className="grow text-muted">
              {lines.length > 0 && (
                <>
                  {formatQuantity(lines.reduce((sum, line) => sum + (Number(line.quantity) || 0), 0))} items ·
                  estimated {formatMoney(estimatedCost)} at current cost prices
                </>
              )}
            </span>
            <Button variant="secondary" onClick={() => router.push('/purchase-orders')} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" isLoading={isSubmitting}>
              Save draft
            </Button>
          </CardFooter>
        </Card>
      </form>
    </div>
  );
}

function suggestPoNumber(): string {
  const now = new Date();
  const stamp = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`;
  return `PO-${stamp}-${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}`;
}
