'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/features/auth/AuthContext';
import { PayablesSummary, SupplierInvoice, accountsPayableApi } from '@/lib/api/accounts-payable';
import { Page, emptyPage } from '@/lib/api/http';
import { useDebounced } from '@/hooks/useDebounced';
import { errorMessage, formatDate, formatMoney } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, Metric } from '@/components/ui/Card';
import { SearchInput, Select } from '@/components/ui/Field';
import { Badge, StatusBadge } from '@/components/ui/Badge';
import { Pagination, Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { EmptyState, ErrorState, PermissionRequired, TableSkeleton } from '@/components/ui/States';

const PAGE_SIZE = 20;

/** Supplier invoices: what you owe, what is overdue, and what has been paid. */
export default function AccountsPayablePage() {
  const { user } = useAuth();
  const canRead = hasPermission(user?.permissions, P.AP_READ);
  const canWrite = hasPermission(user?.permissions, P.AP_WRITE);

  const [term, setTerm] = useState('');
  const [status, setStatus] = useState('');
  const [scope, setScope] = useState<'all' | 'overdue'>('all');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<SupplierInvoice>>(emptyPage(PAGE_SIZE));
  const [summary, setSummary] = useState<PayablesSummary | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const debouncedTerm = useDebounced(term);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      setResult(
        scope === 'overdue'
          ? await accountsPayableApi.overdue({ page, size: PAGE_SIZE })
          : await accountsPayableApi.listInvoices({
              query: debouncedTerm || undefined,
              status: status || undefined,
              page,
              size: PAGE_SIZE,
              sort: 'dueDate,asc',
            })
      );
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setIsLoading(false);
    }
  }, [scope, debouncedTerm, status, page]);

  useEffect(() => {
    if (canRead) void load();
  }, [canRead, load]);

  useEffect(() => {
    if (!canRead) return;
    accountsPayableApi
      .summary()
      .then(setSummary)
      .catch(() => setSummary(null));
  }, [canRead, result]);

  useEffect(() => {
    setPage(0);
  }, [debouncedTerm, status, scope]);

  if (!canRead) {
    return (
      <div className="page">
        <PermissionRequired permission={P.AP_READ} action="Viewing supplier bills" />
      </div>
    );
  }

  return (
    <div className="page">
      <PageHeader
        title="Bills to pay"
        description="Invoices from your suppliers. Record a bill when it arrives, then record payments against it."
        actions={
          canWrite && (
            <Link className="btn btn--primary" href="/accounts-payable/new">
              Record a bill
            </Link>
          )
        }
      />

      {summary && (
        <div className="metric-grid" style={{ marginBottom: 'var(--space-6)' }}>
          <Metric label="Outstanding" value={formatMoney(summary.outstanding)} meta="Still to pay" />
          <Metric
            label="Overdue"
            value={formatMoney(summary.overdue)}
            meta={summary.overdue > 0 ? <Badge variant="error">Past the due date</Badge> : 'Nothing past due'}
          />
          <Metric label="Paid" value={formatMoney(summary.paid)} meta="Settled to date" />
          <Metric label="Total invoiced" value={formatMoney(summary.totalInvoiced)} />
        </div>
      )}

      <div className="toolbar">
        <SearchInput
          id="ap-search"
          label="Search"
          placeholder="Invoice number or supplier"
          value={term}
          disabled={scope === 'overdue'}
          onChange={(event) => setTerm(event.target.value)}
          fieldClassName="toolbar__search"
        />
        <Select
          id="ap-scope"
          label="Show"
          placeholder={null}
          value={scope}
          onChange={(event) => setScope(event.target.value as 'all' | 'overdue')}
          options={[
            { value: 'all', label: 'All bills' },
            { value: 'overdue', label: 'Overdue only' },
          ]}
          fieldClassName="toolbar__filter"
        />
        {scope === 'all' && (
          <Select
            id="ap-status"
            label="Status"
            placeholder="All"
            value={status}
            onChange={(event) => setStatus(event.target.value)}
            options={[
              { value: 'OPEN', label: 'Unpaid' },
              { value: 'PAID', label: 'Paid' },
              { value: 'CANCELLED', label: 'Cancelled' },
            ]}
            fieldClassName="toolbar__filter"
          />
        )}
      </div>

      <Card flush>
        {error ? (
          <ErrorState message={error} onRetry={() => void load()} />
        ) : isLoading && result.content.length === 0 ? (
          <TableSkeleton rows={6} columns={5} />
        ) : result.content.length === 0 ? (
          <EmptyState
            icon="payables"
            title={scope === 'overdue' ? 'Nothing is overdue' : 'No bills recorded'}
            body={
              scope === 'overdue'
                ? 'Every supplier invoice is within its payment terms.'
                : 'Record a supplier invoice when it arrives so you can track what you owe and when it is due.'
            }
            action={scope === 'all' && canWrite ? { label: 'Record a bill', href: '/accounts-payable/new' } : undefined}
          />
        ) : (
          <>
            <Table>
              <Thead>
                <Tr>
                  <Th>Invoice</Th>
                  <Th>Supplier</Th>
                  <Th>Due</Th>
                  <Th className="table__num">Total</Th>
                  <Th className="table__num">Still owed</Th>
                  <Th>Status</Th>
                  <Th className="table__actions">Actions</Th>
                </Tr>
              </Thead>
              <Tbody>
                {result.content.map((invoice) => {
                  const overdue = invoice.status === 'OPEN' && new Date(invoice.dueDate) < startOfToday();
                  return (
                    <Tr key={invoice.id}>
                      <Td>
                        <Link href={`/accounts-payable/${invoice.id}`} className="table__primary mono">
                          {invoice.invoiceNumber}
                        </Link>
                      </Td>
                      <Td>
                        <Link href={`/suppliers/${invoice.supplierId}`}>{invoice.supplierName}</Link>
                      </Td>
                      <Td>
                        {formatDate(invoice.dueDate)}
                        {overdue && (
                          <div>
                            <Badge variant="error">Overdue</Badge>
                          </div>
                        )}
                      </Td>
                      <Td className="table__num">{formatMoney(invoice.totalAmount)}</Td>
                      <Td className="table__num">
                        <span className="money">{formatMoney(invoice.remainingAmount)}</span>
                      </Td>
                      <Td>
                        <StatusBadge kind="invoice" status={invoice.status === 'OPEN' ? 'UNPAID' : invoice.status} />
                      </Td>
                      <Td className="table__actions">
                        <Link className="btn btn--secondary btn--sm" href={`/accounts-payable/${invoice.id}`}>
                          Open
                        </Link>
                        {invoice.status === 'OPEN' && hasPermission(user?.permissions, P.AP_PAYMENT_CREATE) && (
                          <Link className="btn btn--primary btn--sm" href={`/accounts-payable/${invoice.id}/pay`}>
                            Pay
                          </Link>
                        )}
                      </Td>
                    </Tr>
                  );
                })}
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

function startOfToday(): Date {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return today;
}
