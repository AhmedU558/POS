'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { PurchaseOrder, purchaseOrdersApi } from '@/lib/api/purchase-orders';
import { errorMessage, formatDateTime, formatQuantity } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, DetailItem, DetailList } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { StatusBadge } from '@/components/ui/Badge';
import { Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { ConfirmDialog } from '@/components/ui/Modal';
import { Alert, ErrorState, LoadingState, PermissionRequired } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';

export default function PurchaseOrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const toast = useToast();
  const { user } = useAuth();

  const canRead = hasPermission(user?.permissions, P.PURCHASE_READ);
  const canApprove = hasPermission(user?.permissions, P.PURCHASE_APPROVE);
  const canReceive = hasPermission(user?.permissions, P.INVENTORY_RECEIVE);

  const [order, setOrder] = useState<PurchaseOrder | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isWorking, setIsWorking] = useState(false);
  const [prompt, setPrompt] = useState<'submit' | 'cancel' | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      setOrder(await purchaseOrdersApi.get(id));
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    if (canRead) void load();
  }, [canRead, load]);

  if (!canRead) {
    return (
      <div className="page">
        <PermissionRequired permission={P.PURCHASE_READ} action="Viewing purchase orders" />
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

  if (error || !order) {
    return (
      <div className="page">
        <ErrorState message={error ?? 'Purchase order not found.'} onRetry={() => void load()} />
      </div>
    );
  }

  const act = async (action: 'submit' | 'cancel') => {
    setIsWorking(true);
    try {
      setOrder(action === 'submit' ? await purchaseOrdersApi.submit(id) : await purchaseOrdersApi.cancel(id));
      toast.success(action === 'submit' ? 'Order submitted to the supplier.' : 'Order cancelled.');
    } catch (caught) {
      toast.error(errorMessage(caught));
    } finally {
      setIsWorking(false);
      setPrompt(null);
    }
  };

  return (
    <div className="page">
      <PageHeader
        title={order.poNumber}
        breadcrumbs={[{ label: 'Purchase orders', href: '/purchase-orders' }, { label: order.poNumber }]}
        description={`Ordered from ${order.supplierName}`}
        actions={
          <>
            <StatusBadge kind="purchaseOrder" status={order.status} />
            {order.status === 'DRAFT' && canApprove && (
              <Button onClick={() => setPrompt('submit')} isLoading={isWorking}>
                Submit order
              </Button>
            )}
            {order.status === 'SUBMITTED' && canReceive && (
              <Link className="btn btn--primary" href={`/purchase-orders/${id}/receive`}>
                Receive goods
              </Link>
            )}
            {order.status !== 'CANCELLED' && canApprove && (
              <Button variant="secondary" onClick={() => setPrompt('cancel')}>
                Cancel order
              </Button>
            )}
          </>
        }
      />

      <div className="stack-lg stack">
        <NextStep order={order} canApprove={canApprove} canReceive={canReceive} />

        <Card>
          <CardBody>
            <DetailList>
              <DetailItem label="Supplier">
                <Link href={`/suppliers/${order.supplierId}`}>{order.supplierName}</Link>
              </DetailItem>
              <DetailItem label="Raised">{formatDateTime(order.createdAt)}</DetailItem>
              <DetailItem label="Last updated">{formatDateTime(order.updatedAt)}</DetailItem>
              <DetailItem label="Notes">{order.notes || <span className="text-muted">None</span>}</DetailItem>
            </DetailList>
          </CardBody>
        </Card>

        <Card flush>
          <CardHeader title={`Ordered items (${order.items.length})`} />
          <Table>
            <Thead>
              <Tr>
                <Th>Product</Th>
                <Th>SKU</Th>
                <Th className="table__num">Quantity ordered</Th>
              </Tr>
            </Thead>
            <Tbody>
              {order.items.map((item) => (
                <Tr key={item.id}>
                  <Td>
                    <Link href={`/products/${item.productId}`} className="table__primary">
                      {item.name}
                    </Link>
                  </Td>
                  <Td>
                    <span className="mono">{item.sku}</span>
                  </Td>
                  <Td className="table__num">{formatQuantity(item.quantity)}</Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        </Card>
      </div>

      <ConfirmDialog
        open={prompt === 'submit'}
        title="Submit this order?"
        description={`${order.poNumber} will be marked as submitted to ${order.supplierName} and can no longer be edited. You will then be able to receive goods against it.`}
        confirmLabel="Submit order"
        isWorking={isWorking}
        onCancel={() => setPrompt(null)}
        onConfirm={() => void act('submit')}
      />

      <ConfirmDialog
        open={prompt === 'cancel'}
        title="Cancel this order?"
        description={`${order.poNumber} will be cancelled. Goods can no longer be received against it, and this cannot be undone.`}
        confirmLabel="Cancel order"
        cancelLabel="Keep order"
        destructive
        isWorking={isWorking}
        onCancel={() => setPrompt(null)}
        onConfirm={() => void act('cancel')}
      />
    </div>
  );
}

/** Says what happens next, so the status badge is not the only guidance on the page. */
function NextStep({
  order,
  canApprove,
  canReceive,
}: {
  order: PurchaseOrder;
  canApprove: boolean;
  canReceive: boolean;
}) {
  if (order.status === 'CANCELLED') {
    return <Alert tone="warning">This order was cancelled. Nothing further can be done with it.</Alert>;
  }
  if (order.status === 'DRAFT') {
    return (
      <Alert tone="info" title="Next: submit the order">
        {canApprove
          ? 'Submitting marks the order as sent to the supplier. Goods can only be received against a submitted order.'
          : 'This order is still a draft. Someone with approval rights needs to submit it before goods can be received.'}
      </Alert>
    );
  }
  return (
    <Alert tone="info" title="Next: receive the goods">
      {canReceive
        ? 'When the delivery arrives, record what actually turned up. That is what moves the stock onto your shelves.'
        : 'When the delivery arrives, someone with stock-receiving rights records what turned up.'}
    </Alert>
  );
}
