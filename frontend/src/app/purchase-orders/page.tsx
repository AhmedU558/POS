'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/features/auth/AuthContext';
import { PurchaseOrder, purchaseOrdersApi } from '@/lib/api/purchase-orders';
import { Page, emptyPage } from '@/lib/api/http';
import { useDebounced } from '@/hooks/useDebounced';
import { errorMessage, formatDate } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card } from '@/components/ui/Card';
import { SearchInput, Select } from '@/components/ui/Field';
import { StatusBadge } from '@/components/ui/Badge';
import { Pagination, Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { Alert, EmptyState, ErrorState, PermissionRequired, TableSkeleton } from '@/components/ui/States';

const PAGE_SIZE = 20;

/**
 * Purchase orders: supplier → order → submit → receive → stock.
 *
 * The header states that sequence, because "submitted" on its own does not tell a store manager
 * what they are supposed to do next.
 */
export default function PurchaseOrdersPage() {
  const { user } = useAuth();
  const canRead = hasPermission(user?.permissions, P.PURCHASE_READ);
  const canWrite = hasPermission(user?.permissions, P.PURCHASE_WRITE);

  const [term, setTerm] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<PurchaseOrder>>(emptyPage(PAGE_SIZE));
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const debouncedTerm = useDebounced(term);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      setResult(
        await purchaseOrdersApi.list({
          query: debouncedTerm || undefined,
          status: status || undefined,
          page,
          size: PAGE_SIZE,
          sort: 'createdAt,desc',
        })
      );
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setIsLoading(false);
    }
  }, [debouncedTerm, status, page]);

  useEffect(() => {
    if (canRead) void load();
  }, [canRead, load]);

  useEffect(() => {
    setPage(0);
  }, [debouncedTerm, status]);

  if (!canRead) {
    return (
      <div className="page">
        <PermissionRequired permission={P.PURCHASE_READ} action="Viewing purchase orders" />
      </div>
    );
  }

  const hasFilters = Boolean(debouncedTerm || status);

  return (
    <div className="page">
      <PageHeader
        title="Purchase orders"
        description="What you have ordered from suppliers. Draft an order, submit it, then receive the goods when they arrive — receiving is what puts the stock on your shelves."
        actions={
          canWrite && (
            <Link className="btn btn--primary" href="/purchase-orders/new">
              New purchase order
            </Link>
          )
        }
      />

      <div className="toolbar">
        <SearchInput
          id="po-search"
          label="Search"
          placeholder="PO number or supplier"
          value={term}
          onChange={(event) => setTerm(event.target.value)}
          fieldClassName="toolbar__search"
          autoFocus
        />
        <Select
          id="po-status"
          label="Status"
          placeholder="All"
          value={status}
          onChange={(event) => setStatus(event.target.value)}
          options={[
            { value: 'DRAFT', label: 'Draft' },
            { value: 'SUBMITTED', label: 'Submitted' },
            { value: 'CANCELLED', label: 'Cancelled' },
          ]}
          fieldClassName="toolbar__filter"
        />
      </div>

      <Card flush>
        {error ? (
          <ErrorState message={error} onRetry={() => void load()} />
        ) : isLoading && result.content.length === 0 ? (
          <TableSkeleton rows={6} columns={5} />
        ) : result.content.length === 0 ? (
          <EmptyState
            icon="purchases"
            title={hasFilters ? 'No orders match' : 'No purchase orders yet'}
            body={
              hasFilters
                ? 'Try a different PO number or supplier.'
                : 'Raise an order to record what you have asked a supplier for. You will need a supplier and at least one product.'
            }
            action={
              hasFilters
                ? {
                    label: 'Clear filters',
                    onClick: () => {
                      setTerm('');
                      setStatus('');
                    },
                  }
                : canWrite
                  ? { label: 'New purchase order', href: '/purchase-orders/new' }
                  : undefined
            }
          />
        ) : (
          <>
            <Table>
              <Thead>
                <Tr>
                  <Th>PO number</Th>
                  <Th>Supplier</Th>
                  <Th>Raised</Th>
                  <Th className="table__num">Lines</Th>
                  <Th>Status</Th>
                  <Th className="table__actions">Actions</Th>
                </Tr>
              </Thead>
              <Tbody>
                {result.content.map((order) => (
                  <Tr key={order.id}>
                    <Td>
                      <Link href={`/purchase-orders/${order.id}`} className="table__primary mono">
                        {order.poNumber}
                      </Link>
                    </Td>
                    <Td>
                      <Link href={`/suppliers/${order.supplierId}`}>{order.supplierName}</Link>
                    </Td>
                    <Td>{formatDate(order.createdAt)}</Td>
                    <Td className="table__num">{order.items.length}</Td>
                    <Td>
                      <StatusBadge kind="purchaseOrder" status={order.status} />
                    </Td>
                    <Td className="table__actions">
                      <Link className="btn btn--secondary btn--sm" href={`/purchase-orders/${order.id}`}>
                        Open
                      </Link>
                      {order.status === 'SUBMITTED' && hasPermission(user?.permissions, P.INVENTORY_RECEIVE) && (
                        <Link className="btn btn--primary btn--sm" href={`/purchase-orders/${order.id}/receive`}>
                          Receive
                        </Link>
                      )}
                    </Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
            <Pagination
              page={result.number}
              totalPages={result.totalPages}
              totalElements={result.totalElements}
              pageSize={result.size || PAGE_SIZE}
              onPageChange={setPage}
              isLoading={isLoading}
            />
          </>
        )}
      </Card>

      {result.content.some((order) => order.status === 'SUBMITTED') && (
        <div style={{ marginTop: 'var(--space-4)' }}>
          <Alert tone="info">
            An order stays &ldquo;submitted&rdquo; after goods arrive. Receiving records what turned up and moves it into
            stock; it does not close the order.
          </Alert>
        </div>
      )}
    </div>
  );
}
