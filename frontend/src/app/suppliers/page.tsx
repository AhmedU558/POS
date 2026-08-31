'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/features/auth/AuthContext';
import { Supplier, suppliersApi } from '@/lib/api/suppliers';
import { Page, emptyPage } from '@/lib/api/http';
import { useDebounced } from '@/hooks/useDebounced';
import { errorMessage } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card } from '@/components/ui/Card';
import { SearchInput, Select } from '@/components/ui/Field';
import { ActiveBadge } from '@/components/ui/Badge';
import { Pagination, Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { EmptyState, ErrorState, PermissionRequired, TableSkeleton } from '@/components/ui/States';

const PAGE_SIZE = 20;

export default function SuppliersPage() {
  const { user } = useAuth();
  const canRead = hasPermission(user?.permissions, P.SUPPLIER_READ);
  const canWrite = hasPermission(user?.permissions, P.SUPPLIER_WRITE);

  const [term, setTerm] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<Supplier>>(emptyPage(PAGE_SIZE));
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const debouncedTerm = useDebounced(term);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      setResult(
        await suppliersApi.list({
          query: debouncedTerm || undefined,
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
        <PermissionRequired permission={P.SUPPLIER_READ} action="Viewing suppliers" />
      </div>
    );
  }

  const hasFilters = Boolean(debouncedTerm || status);

  return (
    <div className="page">
      <PageHeader
        title="Suppliers"
        description="Who you buy from. A supplier is needed before you can raise a purchase order."
        actions={
          canWrite && (
            <Link className="btn btn--primary" href="/suppliers/new">
              Add supplier
            </Link>
          )
        }
      />

      <div className="toolbar">
        <SearchInput
          id="supplier-search"
          label="Search"
          placeholder="Name, email or supplier code"
          value={term}
          onChange={(event) => setTerm(event.target.value)}
          fieldClassName="toolbar__search"
          autoFocus
        />
        <Select
          id="supplier-status"
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
          <TableSkeleton rows={6} columns={4} />
        ) : result.content.length === 0 ? (
          <EmptyState
            icon="suppliers"
            title={hasFilters ? 'No suppliers match' : 'No suppliers yet'}
            body={
              hasFilters
                ? 'Try a different name or code.'
                : 'Add the businesses you buy stock from. You will pick one when raising a purchase order.'
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
                  ? { label: 'Add supplier', href: '/suppliers/new' }
                  : undefined
            }
          />
        ) : (
          <>
            <Table>
              <Thead>
                <Tr>
                  <Th>Supplier</Th>
                  <Th>Phone</Th>
                  <Th>Email</Th>
                  <Th>Status</Th>
                  <Th className="table__actions">Actions</Th>
                </Tr>
              </Thead>
              <Tbody>
                {result.content.map((supplier) => (
                  <Tr key={supplier.id}>
                    <Td>
                      <Link href={`/suppliers/${supplier.id}`} className="table__primary">
                        {supplier.name}
                      </Link>
                      <div className="table__secondary mono">{supplier.supplierCode}</div>
                    </Td>
                    <Td>{supplier.phone ?? <span className="text-muted">—</span>}</Td>
                    <Td>{supplier.email ?? <span className="text-muted">—</span>}</Td>
                    <Td>
                      <ActiveBadge active={supplier.active} />
                    </Td>
                    <Td className="table__actions">
                      <Link className="btn btn--secondary btn--sm" href={`/suppliers/${supplier.id}`}>
                        {canWrite ? 'Manage' : 'View'}
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
