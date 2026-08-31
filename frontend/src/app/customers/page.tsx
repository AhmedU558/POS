'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/features/auth/AuthContext';
import { Customer, customersApi } from '@/lib/api/customers';
import { Page, emptyPage } from '@/lib/api/http';
import { useDebounced } from '@/hooks/useDebounced';
import { errorMessage, formatMoney } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card } from '@/components/ui/Card';
import { SearchInput, Select } from '@/components/ui/Field';
import { ActiveBadge } from '@/components/ui/Badge';
import { Pagination, Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { EmptyState, ErrorState, PermissionRequired, TableSkeleton } from '@/components/ui/States';

const PAGE_SIZE = 20;

export default function CustomersPage() {
  const { user } = useAuth();
  const canRead = hasPermission(user?.permissions, P.CUSTOMER_READ);
  const canWrite = hasPermission(user?.permissions, P.CUSTOMER_WRITE);

  const [term, setTerm] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<Customer>>(emptyPage(PAGE_SIZE));
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const debouncedTerm = useDebounced(term);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      setResult(
        await customersApi.list({
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
        <PermissionRequired permission={P.CUSTOMER_READ} action="Viewing customers" />
      </div>
    );
  }

  const hasFilters = Boolean(debouncedTerm || status);

  return (
    <div className="page">
      <PageHeader
        title="Customers"
        description="Account customers. Adding one to a sale records their purchase history and lets them pay on store credit."
        actions={
          canWrite && (
            <Link className="btn btn--primary" href="/customers/new">
              Add customer
            </Link>
          )
        }
      />

      <div className="toolbar">
        <SearchInput
          id="customer-search"
          label="Search"
          placeholder="Name, phone, email or customer code"
          value={term}
          onChange={(event) => setTerm(event.target.value)}
          fieldClassName="toolbar__search"
          autoFocus
        />
        <Select
          id="customer-status"
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
            icon="customers"
            title={hasFilters ? 'No customers match' : 'No customers yet'}
            body={
              hasFilters
                ? 'Try a different name or phone number.'
                : 'You do not need a customer to make a sale. Add one when you want to track their purchases or offer credit.'
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
                  ? { label: 'Add customer', href: '/customers/new' }
                  : undefined
            }
          />
        ) : (
          <>
            <Table>
              <Thead>
                <Tr>
                  <Th>Customer</Th>
                  <Th>Phone</Th>
                  <Th>Email</Th>
                  <Th className="table__num">Credit limit</Th>
                  <Th>Status</Th>
                  <Th className="table__actions">Actions</Th>
                </Tr>
              </Thead>
              <Tbody>
                {result.content.map((customer) => (
                  <Tr key={customer.id}>
                    <Td>
                      <Link href={`/customers/${customer.id}`} className="table__primary">
                        {customer.name}
                      </Link>
                      <div className="table__secondary mono">{customer.customerCode}</div>
                    </Td>
                    <Td>{customer.phone ?? <span className="text-muted">—</span>}</Td>
                    <Td>{customer.email ?? <span className="text-muted">—</span>}</Td>
                    <Td className="table__num">{formatMoney(customer.creditLimit)}</Td>
                    <Td>
                      <ActiveBadge active={customer.active} />
                    </Td>
                    <Td className="table__actions">
                      <Link className="btn btn--secondary btn--sm" href={`/customers/${customer.id}`}>
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
