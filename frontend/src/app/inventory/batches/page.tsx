'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/features/auth/AuthContext';
import { useStoreContext } from '@/features/session/StoreContext';
import { InventoryBatch, inventoryApi } from '@/lib/api/inventory';
import { Page, emptyPage } from '@/lib/api/http';
import { errorMessage, formatDate, formatQuantity } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card } from '@/components/ui/Card';
import { Select } from '@/components/ui/Field';
import { StatusBadge } from '@/components/ui/Badge';
import { Pagination, Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { EmptyState, ErrorState, PermissionRequired, TableSkeleton } from '@/components/ui/States';
import { InventoryTabs } from '@/features/inventory/InventoryTabs';

const PAGE_SIZE = 20;

/** Batches held in the store, ordered so what expires soonest is dealt with first. */
export default function BatchesPage() {
  const { user } = useAuth();
  const { activeStoreId, activeStore } = useStoreContext();
  const canRead = hasPermission(user?.permissions, P.INVENTORY_READ);

  const [scope, setScope] = useState<'all' | 'expiring'>('all');
  const [days, setDays] = useState(30);
  const [page, setPage] = useState(0);
  const [batches, setBatches] = useState<Page<InventoryBatch>>(emptyPage(PAGE_SIZE));
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!activeStoreId) {
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    setError(null);
    try {
      setBatches(
        scope === 'expiring'
          ? await inventoryApi.getExpiry({ storeId: activeStoreId, page, size: PAGE_SIZE, days })
          : await inventoryApi.getBatches({ storeId: activeStoreId, page, size: PAGE_SIZE })
      );
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setIsLoading(false);
    }
  }, [activeStoreId, scope, days, page]);

  useEffect(() => {
    if (canRead) void load();
  }, [canRead, load]);

  useEffect(() => {
    setPage(0);
  }, [scope, days]);

  if (!canRead) {
    return (
      <div className="page">
        <PermissionRequired permission={P.INVENTORY_READ} action="Viewing batches" />
      </div>
    );
  }

  return (
    <div className="page">
      <PageHeader
        title="Batches & expiry"
        breadcrumbs={[{ label: 'Inventory', href: '/inventory' }, { label: 'Batches & expiry' }]}
        description={`Batches recorded against stock in ${activeStore?.name ?? 'this store'}. A batch is created when tracked stock is received.`}
      />

      <InventoryTabs active="batches" permissions={user?.permissions} />

      <div className="toolbar">
        <Select
          id="batch-scope"
          label="Show"
          placeholder={null}
          value={scope}
          onChange={(event) => setScope(event.target.value as 'all' | 'expiring')}
          options={[
            { value: 'all', label: 'All batches' },
            { value: 'expiring', label: 'Expiring soon' },
          ]}
          fieldClassName="toolbar__filter"
        />
        {scope === 'expiring' && (
          <Select
            id="batch-days"
            label="Within"
            placeholder={null}
            value={String(days)}
            onChange={(event) => setDays(Number(event.target.value))}
            options={[
              { value: '7', label: '7 days' },
              { value: '30', label: '30 days' },
              { value: '90', label: '90 days' },
            ]}
            fieldClassName="toolbar__filter"
          />
        )}
      </div>

      <Card flush>
        {error ? (
          <ErrorState message={error} onRetry={() => void load()} />
        ) : isLoading && batches.content.length === 0 ? (
          <TableSkeleton rows={5} columns={5} />
        ) : batches.content.length === 0 ? (
          <EmptyState
            icon="box"
            title={scope === 'expiring' ? 'Nothing expiring in that window' : 'No batches recorded'}
            body={
              scope === 'expiring'
                ? 'Widen the window, or switch to all batches.'
                : 'Batches appear when you receive stock for a product that has batch tracking turned on. Turn it on from the product’s stock control settings.'
            }
            action={{ label: 'Receive stock', href: '/inventory/receive' }}
          />
        ) : (
          <>
            <Table>
              <Thead>
                <Tr>
                  <Th>Product</Th>
                  <Th>Batch</Th>
                  <Th className="table__num">Quantity</Th>
                  <Th>Expires</Th>
                  <Th>Status</Th>
                </Tr>
              </Thead>
              <Tbody>
                {batches.content.map((batch) => (
                  <Tr key={batch.id}>
                    <Td>
                      <Link href={`/products/${batch.productId}`} className="table__primary">
                        {batch.productName}
                      </Link>
                      <div className="table__secondary mono">{batch.sku}</div>
                    </Td>
                    <Td>
                      <span className="mono">{batch.batchNumber}</span>
                    </Td>
                    <Td className="table__num">{formatQuantity(batch.quantity)}</Td>
                    <Td>
                      {batch.expirationDate ? (
                        <>
                          {formatDate(batch.expirationDate)}
                          {batch.daysRemaining !== null && (
                            <div className="table__secondary">
                              {batch.daysRemaining < 0
                                ? `${Math.abs(batch.daysRemaining)} days ago`
                                : `in ${batch.daysRemaining} days`}
                            </div>
                          )}
                        </>
                      ) : (
                        <span className="text-muted">No expiry</span>
                      )}
                    </Td>
                    <Td>
                      <StatusBadge kind="batch" status={batch.status} />
                    </Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
            <Pagination
              page={batches.number}
              totalPages={batches.totalPages}
              totalElements={batches.totalElements}
              pageSize={batches.size || PAGE_SIZE}
              onPageChange={setPage}
              isLoading={isLoading}
            />
          </>
        )}
      </Card>
    </div>
  );
}
