'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { useStoreContext } from '@/features/session/StoreContext';
import { PurchaseOrder, goodsReceiptsApi, purchaseOrdersApi } from '@/lib/api/purchase-orders';
import { searchProducts } from '@/lib/api/catalog';
import { Product } from '@/types/catalog';
import { errorMessage, formatQuantity } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardFooter, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Field';
import { Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { ConfirmDialog } from '@/components/ui/Modal';
import { Alert, EmptyState, ErrorState, LoadingState, PermissionRequired } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';

interface ReceiptLine {
  productId: string;
  sku: string;
  name: string;
  ordered: number;
  receiving: string;
  batchNumber: string;
  expirationDate: string;
  trackBatch: boolean;
  trackExpiry: boolean;
}

/**
 * Recording a delivery against an order.
 *
 * Lines default to the ordered quantity, since a full delivery is the common case, and can be
 * reduced to what actually arrived. Only lines with a quantity are sent, so a partial delivery is
 * simply a shorter list.
 */
export default function ReceivePurchaseOrderPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const toast = useToast();
  const { user } = useAuth();
  const { activeStoreId, activeStore } = useStoreContext();
  const canReceive = hasPermission(user?.permissions, P.INVENTORY_RECEIVE);

  const [order, setOrder] = useState<PurchaseOrder | null>(null);
  const [lines, setLines] = useState<ReceiptLine[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [confirming, setConfirming] = useState(false);

  const load = useCallback(async () => {
    setIsLoading(true);
    setLoadError(null);
    try {
      const loaded = await purchaseOrdersApi.get(id);
      setOrder(loaded);

      /*
       * Batch and expiry requirements live on the product, not on the order line, so the products
       * are looked up to decide which extra fields this delivery needs.
       */
      const productsById = new Map<string, Product>();
      await Promise.all(
        loaded.items.map(async (item) => {
          try {
            const page = await searchProducts({ query: item.sku, size: 5 });
            const match = page.content.find((candidate) => candidate.id === item.productId);
            if (match) productsById.set(item.productId, match);
          } catch {
            // Falls back to no batch fields, which the API accepts for untracked products.
          }
        })
      );

      setLines(
        loaded.items.map((item) => ({
          productId: item.productId,
          sku: item.sku,
          name: item.name,
          ordered: Number(item.quantity),
          receiving: String(item.quantity),
          batchNumber: '',
          expirationDate: '',
          trackBatch: productsById.get(item.productId)?.trackBatch ?? false,
          trackExpiry: productsById.get(item.productId)?.trackExpiry ?? false,
        }))
      );
    } catch (caught) {
      setLoadError(errorMessage(caught));
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    if (canReceive) void load();
  }, [canReceive, load]);

  if (!canReceive) {
    return (
      <div className="page">
        <PermissionRequired permission={P.INVENTORY_RECEIVE} action="Receiving goods" />
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="page">
        <LoadingState label="Loading purchase order…" />
      </div>
    );
  }

  if (loadError || !order) {
    return (
      <div className="page">
        <ErrorState message={loadError ?? 'Purchase order not found.'} onRetry={() => void load()} />
      </div>
    );
  }

  if (order.status !== 'SUBMITTED') {
    return (
      <div className="page page-narrow">
        <PageHeader
          title="Receive goods"
          breadcrumbs={[
            { label: 'Purchase orders', href: '/purchase-orders' },
            { label: order.poNumber, href: `/purchase-orders/${id}` },
            { label: 'Receive' },
          ]}
        />
        <Card>
          <CardBody>
            <EmptyState
              icon="purchases"
              title={order.status === 'DRAFT' ? 'This order has not been submitted' : 'This order was cancelled'}
              body={
                order.status === 'DRAFT'
                  ? 'Goods can only be received against a submitted order. Submit it first.'
                  : 'A cancelled order cannot receive goods.'
              }
              action={{ label: 'Back to the order', href: `/purchase-orders/${id}` }}
            />
          </CardBody>
        </Card>
      </div>
    );
  }

  const receiving = lines.filter((line) => Number(line.receiving) > 0);
  const missingBatch = receiving.filter((line) => (line.trackBatch || line.trackExpiry) && !line.batchNumber.trim());
  const missingExpiry = receiving.filter((line) => line.trackExpiry && !line.expirationDate);

  const review = () => {
    if (!activeStoreId) {
      setSubmitError('No store is selected to receive into.');
      return;
    }
    if (receiving.length === 0) {
      setSubmitError('Enter a quantity for at least one line.');
      return;
    }
    if (missingBatch.length > 0) {
      setSubmitError(`A batch number is required for: ${missingBatch.map((line) => line.name).join(', ')}.`);
      return;
    }
    if (missingExpiry.length > 0) {
      setSubmitError(`An expiry date is required for: ${missingExpiry.map((line) => line.name).join(', ')}.`);
      return;
    }
    setSubmitError(null);
    setConfirming(true);
  };

  const submit = async () => {
    if (!activeStoreId) return;
    setIsSubmitting(true);
    setSubmitError(null);
    try {
      await goodsReceiptsApi.create({
        purchaseOrderId: id,
        storeId: activeStoreId,
        items: receiving.map((line) => ({
          productId: line.productId,
          quantity: Number(line.receiving),
          ...(line.trackBatch || line.trackExpiry
            ? { batchNumber: line.batchNumber.trim(), expirationDate: line.expirationDate || null }
            : {}),
        })),
      });
      toast.success(`Goods received into ${activeStore?.name ?? 'the store'} and added to stock.`);
      router.push('/inventory');
    } catch (caught) {
      setSubmitError(errorMessage(caught));
      setConfirming(false);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="page">
      <PageHeader
        title="Receive goods"
        breadcrumbs={[
          { label: 'Purchase orders', href: '/purchase-orders' },
          { label: order.poNumber, href: `/purchase-orders/${id}` },
          { label: 'Receive' },
        ]}
        description={`Record what actually arrived from ${order.supplierName}. Anything you receive here goes straight into ${activeStore?.name ?? 'your store'}'s stock.`}
      />

      <form
        className="stack"
        onSubmit={(event) => {
          event.preventDefault();
          review();
        }}
      >
        {submitError && <Alert tone="error">{submitError}</Alert>}

        <Alert tone="info">
          Quantities start at what was ordered. Change any line that arrived short, and set a line to 0 if none of it
          turned up — the rest can be received later against the same order.
        </Alert>

        <Card flush>
          <CardHeader title={`Delivery against ${order.poNumber}`} />
          <Table>
            <Thead>
              <Tr>
                <Th>Product</Th>
                <Th className="table__num">Ordered</Th>
                <Th className="table__num">Received</Th>
                <Th>Batch details</Th>
              </Tr>
            </Thead>
            <Tbody>
              {lines.map((line, index) => (
                <Tr key={line.productId}>
                  <Td>
                    <Link href={`/products/${line.productId}`} className="table__primary">
                      {line.name}
                    </Link>
                    <div className="table__secondary mono">{line.sku}</div>
                  </Td>
                  <Td className="table__num">{formatQuantity(line.ordered)}</Td>
                  <Td className="table__num">
                    <input
                      className="control"
                      style={{ width: '7rem', textAlign: 'right' }}
                      type="number"
                      min="0"
                      step="any"
                      value={line.receiving}
                      aria-label={`Quantity of ${line.name} received`}
                      onChange={(event) =>
                        setLines((current) =>
                          current.map((item, i) => (i === index ? { ...item, receiving: event.target.value } : item))
                        )
                      }
                    />
                  </Td>
                  <Td>
                    {line.trackBatch || line.trackExpiry ? (
                      <div className="row row-wrap">
                        <Input
                          id={`batch-${line.productId}`}
                          label="Batch"
                          value={line.batchNumber}
                          onChange={(event) =>
                            setLines((current) =>
                              current.map((item, i) =>
                                i === index ? { ...item, batchNumber: event.target.value } : item
                              )
                            )
                          }
                        />
                        {line.trackExpiry && (
                          <Input
                            id={`expiry-${line.productId}`}
                            label="Expires"
                            type="date"
                            value={line.expirationDate}
                            onChange={(event) =>
                              setLines((current) =>
                                current.map((item, i) =>
                                  i === index ? { ...item, expirationDate: event.target.value } : item
                                )
                              )
                            }
                          />
                        )}
                      </div>
                    ) : (
                      <span className="text-muted">Not tracked</span>
                    )}
                  </Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
          <CardFooter>
            <span className="grow text-muted">
              Receiving {receiving.length} of {lines.length} lines
            </span>
            <Button variant="secondary" onClick={() => router.push(`/purchase-orders/${id}`)} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={receiving.length === 0}>
              Review delivery
            </Button>
          </CardFooter>
        </Card>
      </form>

      <ConfirmDialog
        open={confirming}
        title="Add this delivery to stock?"
        description={`${receiving.length} ${receiving.length === 1 ? 'line' : 'lines'} will be added to ${activeStore?.name ?? 'the store'}'s stock. Receiving cannot be undone — a stock adjustment would be needed to correct it.`}
        confirmLabel="Receive into stock"
        isWorking={isSubmitting}
        onCancel={() => setConfirming(false)}
        onConfirm={() => void submit()}
      />
    </div>
  );
}
