'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/features/auth/AuthContext';
import { getCategories, searchProducts } from '@/lib/api/catalog';
import { Page, emptyPage } from '@/lib/api/http';
import { Category, Product } from '@/types/catalog';
import { useDebounced } from '@/hooks/useDebounced';
import { errorMessage, formatMoney, formatQuantity } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card } from '@/components/ui/Card';
import { SearchInput, Select } from '@/components/ui/Field';
import { ActiveBadge } from '@/components/ui/Badge';
import { Pagination, Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { EmptyState, ErrorState, PermissionRequired, TableSkeleton } from '@/components/ui/States';

const PAGE_SIZE = 20;

/**
 * The product catalogue.
 *
 * One search box covers name, SKU and barcode because the backend query already spans all three
 * — a manager holding a physical item can scan it here rather than deciding which field to use.
 */
export default function ProductsPage() {
  const { user } = useAuth();
  const canRead = hasPermission(user?.permissions, P.PRODUCT_READ);
  const canWrite = hasPermission(user?.permissions, P.PRODUCT_WRITE);

  const [term, setTerm] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);

  const [result, setResult] = useState<Page<Product>>(emptyPage(PAGE_SIZE));
  const [categories, setCategories] = useState<Category[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const debouncedTerm = useDebounced(term);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      setResult(
        await searchProducts({
          query: debouncedTerm || undefined,
          categoryId: categoryId || undefined,
          isActive: status === '' ? undefined : status === 'active',
          page,
          size: PAGE_SIZE,
          sort: 'name,asc',
        })
      );
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setIsLoading(false);
    }
  }, [debouncedTerm, categoryId, status, page]);

  useEffect(() => {
    if (canRead) void load();
  }, [canRead, load]);

  useEffect(() => {
    if (!canRead) return;
    getCategories()
      .then((all) => setCategories(all.filter((category) => category.active)))
      .catch(() => setCategories([]));
  }, [canRead]);

  // Any filter change invalidates the page number: page 4 of the old result set means nothing.
  useEffect(() => {
    setPage(0);
  }, [debouncedTerm, categoryId, status]);

  if (!canRead) {
    return (
      <div className="page">
        <PermissionRequired permission={P.PRODUCT_READ} action="Viewing products" />
      </div>
    );
  }

  const hasFilters = Boolean(debouncedTerm || categoryId || status);

  return (
    <div className="page">
      <PageHeader
        title="Products"
        description="Everything you sell. Add a product here before it can be scanned at the till or received into stock."
        actions={
          <>
            <Link className="btn btn--secondary" href="/products/reference">
              Categories &amp; units
            </Link>
            {canWrite && (
              <Link className="btn btn--primary" href="/products/new">
                Add product
              </Link>
            )}
          </>
        }
      />

      <div className="toolbar">
        <SearchInput
          id="product-search"
          label="Search"
          placeholder="Name, SKU or scan a barcode"
          value={term}
          onChange={(event) => setTerm(event.target.value)}
          fieldClassName="toolbar__search"
          autoFocus
        />
        <Select
          id="product-category"
          label="Category"
          placeholder="All categories"
          value={categoryId}
          onChange={(event) => setCategoryId(event.target.value)}
          options={categories.map((category) => ({ value: category.id, label: category.name }))}
          fieldClassName="toolbar__filter"
        />
        <Select
          id="product-status"
          label="Status"
          placeholder="All"
          value={status}
          onChange={(event) => setStatus(event.target.value)}
          options={[
            { value: 'active', label: 'Active' },
            { value: 'inactive', label: 'Inactive' },
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
          hasFilters ? (
            <EmptyState
              icon="search"
              title="No products match those filters"
              body="Try a different search term, or clear the filters to see the whole catalogue."
              action={{
                label: 'Clear filters',
                onClick: () => {
                  setTerm('');
                  setCategoryId('');
                  setStatus('');
                },
              }}
            />
          ) : (
            <EmptyState
              icon="products"
              title="No products yet"
              body="Add your first product to start selling. You will need a name, a SKU and a selling price — everything else can wait."
              action={canWrite ? { label: 'Add product', href: '/products/new' } : undefined}
            />
          )
        ) : (
          <>
            <Table>
              <Thead>
                <Tr>
                  <Th>Product</Th>
                  <Th>SKU</Th>
                  <Th className="table__num">Cost</Th>
                  <Th className="table__num">Price</Th>
                  <Th className="table__num">Min. stock</Th>
                  <Th>Status</Th>
                  <Th className="table__actions">Actions</Th>
                </Tr>
              </Thead>
              <Tbody>
                {result.content.map((product) => (
                  <Tr key={product.id}>
                    <Td>
                      <Link href={`/products/${product.id}`} className="table__primary">
                        {product.name}
                      </Link>
                      {product.description && <div className="table__secondary truncate">{product.description}</div>}
                    </Td>
                    <Td>
                      <span className="mono">{product.sku}</span>
                    </Td>
                    <Td className="table__num">{formatMoney(product.purchasePrice)}</Td>
                    <Td className="table__num">
                      <span className="money">{formatMoney(product.sellingPrice)}</span>
                    </Td>
                    <Td className="table__num">{formatQuantity(product.minStock)}</Td>
                    <Td>
                      <ActiveBadge active={product.isActive} />
                    </Td>
                    <Td className="table__actions">
                      <Link className="btn btn--secondary btn--sm" href={`/products/${product.id}`}>
                        {canWrite ? 'Edit' : 'View'}
                      </Link>
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
    </div>
  );
}
