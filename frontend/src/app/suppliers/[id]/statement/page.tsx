'use client';

import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { Supplier, SupplierStatementLine, suppliersApi } from '@/lib/api/suppliers';
import { Page, emptyPage } from '@/lib/api/http';
import { errorMessage, formatDate, formatMoney } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Pagination, Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { EmptyState, ErrorState, PermissionRequired, TableSkeleton } from '@/components/ui/States';

const PAGE_SIZE = 25;

/** What has been invoiced and what has been paid, in date order, with the running balance. */
export default function SupplierStatementPage() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const canRead = hasPermission(user?.permissions, P.AP_READ);

  const [supplier, setSupplier] = useState<Supplier | null>(null);
  const [page, setPage] = useState(0);
  const [lines, setLines] = useState<Page<SupplierStatementLine>>(emptyPage(PAGE_SIZE));
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      setLines(await suppliersApi.statement(id, page, PAGE_SIZE));
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setIsLoading(false);
    }
  }, [id, page]);

  useEffect(() => {
    if (canRead) void load();
  }, [canRead, load]);

  useEffect(() => {
    suppliersApi
      .get(id)
      .then(setSupplier)
      .catch(() => setSupplier(null));
  }, [id]);

  if (!canRead) {
    return (
      <div className="page">
        <PermissionRequired permission={P.AP_READ} action="Viewing supplier statements" />
      </div>
    );
  }

  return (
    <div className="page">
      <PageHeader
        title="Statement"
        breadcrumbs={[
          { label: 'Suppliers', href: '/suppliers' },
          { label: supplier?.name ?? 'Supplier', href: `/suppliers/${id}` },
          { label: 'Statement' },
        ]}
        description={`Invoices raised by ${supplier?.name ?? 'this supplier'} and the payments made against them.`}
      />

      <Card flush>
        {error ? (
          <ErrorState message={error} onRetry={() => void load()} />
        ) : isLoading && lines.content.length === 0 ? (
          <TableSkeleton rows={6} columns={5} />
        ) : lines.content.length === 0 ? (
          <EmptyState
            icon="payables"
            title="Nothing on this account"
            body="Invoices appear here once they are recorded under Bills to pay."
            action={{ label: 'Bills to pay', href: '/accounts-payable' }}
          />
        ) : (
          <>
            <Table>
              <Thead>
                <Tr>
                  <Th>Date</Th>
                  <Th>Reference</Th>
                  <Th>Type</Th>
                  <Th className="table__num">Charged</Th>
                  <Th className="table__num">Paid</Th>
                  <Th className="table__num">Balance</Th>
                </Tr>
              </Thead>
              <Tbody>
                {lines.content.map((line, index) => (
                  <Tr key={`${line.invoiceId}-${line.paymentId ?? index}`}>
                    <Td>{formatDate(line.date)}</Td>
                    <Td>
                      <span className="mono">{line.invoiceNumber}</span>
                    </Td>
                    <Td>
                      <Badge variant={line.type === 'PAYMENT' ? 'success' : 'info'}>
                        {line.type === 'PAYMENT' ? 'Payment' : 'Invoice'}
                      </Badge>
                    </Td>
                    <Td className="table__num">{line.debit > 0 ? formatMoney(line.debit) : <span className="text-muted">—</span>}</Td>
                    <Td className="table__num">{line.credit > 0 ? formatMoney(line.credit) : <span className="text-muted">—</span>}</Td>
                    <Td className="table__num">
                      <span className="money">{formatMoney(line.runningBalance)}</span>
                    </Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
            <Pagination
              page={lines.number}
              totalPages={lines.totalPages}
              totalElements={lines.totalElements}
              pageSize={lines.size || PAGE_SIZE}
              onPageChange={setPage}
              isLoading={isLoading}
            />
          </>
        )}
      </Card>
    </div>
  );
}
