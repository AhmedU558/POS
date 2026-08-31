'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/features/auth/AuthContext';
import { useStoreContext } from '@/features/session/StoreContext';
import { InventoryReportRow, inventoryApi } from '@/lib/api/inventory';
import { getCategories } from '@/lib/api/catalog';
import { Category } from '@/types/catalog';
import { Page, emptyPage } from '@/lib/api/http';
import { useDebounced } from '@/hooks/useDebounced';
import { errorMessage, formatDateTime, formatQuantity } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card } from '@/components/ui/Card';
import { Checkbox, SearchInput, Select } from '@/components/ui/Field';
import { Badge } from '@/components/ui/Badge';
import { Pagination, Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { EmptyState, ErrorState, PermissionRequired, TableSkeleton } from '@/components/ui/States';
import { InventoryTabs } from '@/features/inventory/InventoryTabs';

const PAGE_SIZE = 20;

/**
 * Stock on hand.
 *
 * Reads the inventory report rather than raw balances, because the report is the only response
 * that carries the re-order level alongside the quantity — which is what turns a number into
 * "do I need to order more?".
 */
export default function InventoryPage() {
  const { user } = useAuth();
  const { activeStoreId, activeStore } = useStoreContext();

  const canRead = hasPermission(user?.permissions, P.INVENTORY_READ);
  const canReport = hasPermission(user?.permissions, P.REPORT_INVENTORY);

  const [term, setTerm] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [lowStockOnly, setLowStockOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [categories, setCategories] = useState<Category[]>([]);
  const [rows, setRows] = useState<Page<InventoryReportRow>>(emptyPage(PAGE_SIZE));
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const debouncedTerm = useDebounced(term);

  const load = useCallback(async () => {
    if (!activeStoreId) {
      setRows(emptyPage(PAGE_SIZE));
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    setError(null);
    try {
      if (canReport) {
        const report = await inventoryApi.getInventoryReport({
          storeId: activeStoreId,
          page,
          size: PAGE_SIZE,
          lowStockOnly,
          categoryId: categoryId || undefined,
          query: debouncedTerm || undefined,
        });
        setRows(report);
      } else {
        const balances = await inventoryApi.getBalances({
          storeId: activeStoreId,
          page,
          size: PAGE_SIZE,
          categoryId: categoryId || undefined,
          query: debouncedTerm || undefined,
        });
        setRows({
          ...balances,
          content: balances.content.map((balance) => ({
            ...balance,
            minStock: 0,
            belowMinimum: false,
          })),
        });
      }
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setIsLoading(false);
    }
  }, [activeStoreId, canReport, page, lowStockOnly, debouncedTerm, categoryId]);

  useEffect(() => {
    if (canRead) void load();
  }, [canRead, load]);

  useEffect(() => {
    if (!canRead) return;
    getCategories()
      .then((all) => setCategories(all.filter((category) => category.active)))
      .catch(() => setCategories([]));
  }, [canRead]);

  useEffect(() => {
    setPage(0);
  }, [debouncedTerm, categoryId, lowStockOnly]);

  if (!canRead) {
    return (
      <div className="page">
        <PermissionRequired permission={P.INVENTORY_READ} action="Viewing stock" />
      </div>
    );
  }

  return (
    <div className="page">
      <PageHeader
        title="Inventory"
        description={
          activeStore
            ? `What is physically in ${activeStore.name} right now. Stock changes when you receive goods, adjust a count, or sell at the till.`
            : 'What is physically in the store right now.'
        }
        actions={
          <>
            {hasPermission(user?.permissions, P.INVENTORY_ADJUST) && (
              <Link className="btn btn--secondary" href="/inventory/adjust">
                Adjust stock
              </Link>
            )}
            {hasPermission(user?.permissions, P.INVENTORY_RECEIVE) && (
              <Link className="btn btn--primary" href="/inventory/receive">
                Receive stock
              </Link>
            )}
          </>
        }
      />

      <InventoryTabs active="stock" permissions={user?.permissions} />

      {!activeStoreId ? (
        <Card>
          <EmptyState
            icon="store"
            title="No store selected"
            body="Stock is held per store. Set one up before receiving goods."
            action={{ label: 'Go to setup', href: '/setup' }}
          />
        </Card>
      ) : (
        <>
          <div className="toolbar">
            <SearchInput
              id="inventory-search"
              label="Search"
              placeholder="Product name or SKU"
              value={term}
              onChange={(event) => setTerm(event.target.value)}
              fieldClassName="toolbar__search"
            />
            <Select
              id="inventory-category"
              label="Category"
              placeholder="All categories"
              value={categoryId}
              onChange={(event) => setCategoryId(event.target.value)}
              options={categories.map((category) => ({ value: category.id, label: category.name }))}
              fieldClassName="toolbar__filter"
            />
            {canReport && (
              <Checkbox
                id="inventory-low-only"
                label="Low stock only"
                checked={lowStockOnly}
                onChange={(event) => setLowStockOnly(event.target.checked)}
              />
            )}
          </div>

          <Card flush>
            {error ? (
              <ErrorState message={error} onRetry={() => void load()} />
            ) : isLoading && rows.content.length === 0 ? (
              <TableSkeleton rows={6} columns={4} />
            ) : rows.content.length === 0 ? (
              <EmptyState
                icon="inventory"
                title={lowStockOnly ? 'Nothing is running low' : term ? 'No stock matches' : 'No stock recorded yet'}
                body={
                  lowStockOnly
                    ? 'Every product is above its re-order level.'
                    : term
                      ? 'Try a different name or SKU. Products with no stock movement yet do not appear here.'
                      : 'Stock appears once you receive goods against a product. Receiving is also how a purchase order lands in inventory.'
                }
                action={
                  !term && !lowStockOnly && hasPermission(user?.permissions, P.INVENTORY_RECEIVE)
                    ? { label: 'Receive stock', href: '/inventory/receive' }
                    : undefined
                }
              />
            ) : (
              <>
                <Table>
                  <Thead>
                    <Tr>
                      <Th>Product</Th>
                      <Th>SKU</Th>
                      <Th className="table__num">On hand</Th>
                      {canReport && <Th className="table__num">Re-order at</Th>}
                      <Th>Status</Th>
                      <Th>Last movement</Th>
                    </Tr>
                  </Thead>
                  <Tbody>
                    {rows.content.map((row) => (
                      <Tr key={row.productId}>
                        <Td>
                          <Link href={`/products/${row.productId}`} className="table__primary">
                            {row.productName}
                          </Link>
                        </Td>
                        <Td>
                          <span className="mono">{row.sku}</span>
                        </Td>
                        <Td className="table__num">{formatQuantity(row.quantity)}</Td>
                        {canReport && <Td className="table__num">{formatQuantity(row.minStock)}</Td>}
                        <Td>
                          {row.quantity <= 0 ? (
                            <Badge variant="error">Out of stock</Badge>
                          ) : canReport && row.belowMinimum ? (
                            <Badge variant="warning">Low</Badge>
                          ) : (
                            <Badge variant="success">In stock</Badge>
                          )}
                        </Td>
                        <Td className="text-muted">{formatDateTime(row.lastUpdatedAt)}</Td>
                      </Tr>
                    ))}
                  </Tbody>
                </Table>
                <Pagination
                  page={rows.number}
                  totalPages={rows.totalPages}
                  totalElements={rows.totalElements}
                  pageSize={rows.size || PAGE_SIZE}
                  onPageChange={setPage}
                  isLoading={isLoading}
                />
              </>
            )}
          </Card>
        </>
      )}
    </div>
  );
}
