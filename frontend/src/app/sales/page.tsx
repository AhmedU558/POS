'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/features/auth/AuthContext';
import { SaleReceipt, SaleSummary, salesApi } from '@/lib/api/sales';
import { Page, emptyPage } from '@/lib/api/http';
import { useDebounced } from '@/hooks/useDebounced';
import { errorMessage, formatDateTime, formatMoney } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input, SearchInput, Select } from '@/components/ui/Field';
import { StatusBadge } from '@/components/ui/Badge';
import { Pagination, Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { EmptyState, ErrorState, PermissionRequired, TableSkeleton } from '@/components/ui/States';
import { ReceiptDialog } from '@/features/pos/ReceiptDialog';
import { useToast } from '@/components/ui/Toast';

const PAGE_SIZE = 20;

/**
 * Sales history: "find yesterday's sale".
 *
 * Dates are entered as plain days and widened to whole-day instants here, because the API takes
 * timestamps and nobody looking for a receipt thinks in ISO offsets.
 */
export default function SalesHistoryPage() {
  const { user } = useAuth();
  const toast = useToast();
  const canRead = hasPermission(user?.permissions, P.SALE_READ);
  const canReadReceipt = hasPermission(user?.permissions, P.RECEIPT_READ);
  const canReprint = hasPermission(user?.permissions, P.RECEIPT_REPRINT);

  const [term, setTerm] = useState('');
  const [status, setStatus] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);

  const [result, setResult] = useState<Page<SaleSummary>>(emptyPage(PAGE_SIZE));
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<SaleReceipt | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const debouncedTerm = useDebounced(term);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      setResult(
        await salesApi.search({
          query: debouncedTerm || undefined,
          status: status || undefined,
          from: from ? new Date(`${from}T00:00:00`).toISOString() : undefined,
          to: to ? new Date(`${to}T23:59:59.999`).toISOString() : undefined,
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
  }, [debouncedTerm, status, from, to, page]);

  useEffect(() => {
    if (canRead) void load();
  }, [canRead, load]);

  useEffect(() => {
    setPage(0);
  }, [debouncedTerm, status, from, to]);

  if (!canRead) {
    return (
      <div className="page">
        <PermissionRequired permission={P.SALE_READ} action="Viewing sales" />
      </div>
    );
  }

  const openReceipt = async (id: string, reprint: boolean) => {
    setBusyId(id);
    try {
      setReceipt(reprint ? await salesApi.reprint(id) : await salesApi.receipt(id));
    } catch (caught) {
      toast.error(errorMessage(caught));
    } finally {
      setBusyId(null);
    }
  };

  const hasFilters = Boolean(debouncedTerm || status || from || to);

  return (
    <div className="page">
      <PageHeader
        title="Sales"
        description="Every completed and held sale. Search by receipt number, or narrow to a date range."
        actions={
          <Link className="btn btn--secondary" href="/sales/held">
            Held sales
          </Link>
        }
      />

      <div className="toolbar">
        <SearchInput
          id="sales-search"
          label="Receipt number"
          placeholder="e.g. R-2026-000123"
          value={term}
          onChange={(event) => setTerm(event.target.value)}
          fieldClassName="toolbar__search"
        />
        <Select
          id="sales-status"
          label="Status"
          placeholder="All"
          value={status}
          onChange={(event) => setStatus(event.target.value)}
          options={[
            { value: 'COMPLETED', label: 'Completed' },
            { value: 'HELD', label: 'On hold' },
          ]}
          fieldClassName="toolbar__filter"
        />
        <Input
          id="sales-from"
          label="From"
          type="date"
          value={from}
          onChange={(event) => setFrom(event.target.value)}
          fieldClassName="toolbar__filter"
        />
        <Input
          id="sales-to"
          label="To"
          type="date"
          value={to}
          onChange={(event) => setTo(event.target.value)}
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
            icon="reports"
            title={hasFilters ? 'No sales match those filters' : 'No sales yet'}
            body={
              hasFilters
                ? 'Try widening the date range, or clear the filters.'
                : 'Completed sales appear here as soon as the first one is rung up at the till.'
            }
            action={
              hasFilters
                ? {
                    label: 'Clear filters',
                    onClick: () => {
                      setTerm('');
                      setStatus('');
                      setFrom('');
                      setTo('');
                    },
                  }
                : { label: 'Go to the till', href: '/pos' }
            }
          />
        ) : (
          <>
            <Table>
              <Thead>
                <Tr>
                  <Th>Receipt</Th>
                  <Th>When</Th>
                  <Th>Customer</Th>
                  <Th>Cashier</Th>
                  <Th>Status</Th>
                  <Th className="table__num">Total</Th>
                  <Th className="table__actions">Actions</Th>
                </Tr>
              </Thead>
              <Tbody>
                {result.content.map((sale) => (
                  <Tr key={sale.id}>
                    <Td>
                      <span className="mono table__primary">{sale.receiptNumber}</span>
                    </Td>
                    <Td>{formatDateTime(sale.createdAt)}</Td>
                    <Td>{sale.customerName ?? <span className="text-muted">Walk-in</span>}</Td>
                    <Td>{sale.cashierName ?? <span className="text-muted">—</span>}</Td>
                    <Td>
                      <StatusBadge kind="sale" status={sale.status} />
                    </Td>
                    <Td className="table__num">
                      <span className="money">{formatMoney(sale.grandTotal)}</span>
                    </Td>
                    <Td className="table__actions">
                      {canReadReceipt && (
                        <Button
                          variant="secondary"
                          size="sm"
                          isLoading={busyId === sale.id}
                          onClick={() => void openReceipt(sale.id, false)}
                        >
                          Receipt
                        </Button>
                      )}
                      {canReprint && (
                        <Button variant="ghost" size="sm" icon="print" onClick={() => void openReceipt(sale.id, true)}>
                          Reprint
                        </Button>
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

      <ReceiptDialog receipt={receipt} changeDue={null} onClose={() => setReceipt(null)} />
    </div>
  );
}
