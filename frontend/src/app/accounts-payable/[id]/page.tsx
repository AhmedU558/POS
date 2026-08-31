'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import {
  PAYMENT_METHOD_LABELS,
  SupplierInvoice,
  SupplierPayment,
  accountsPayableApi,
} from '@/lib/api/accounts-payable';
import { errorMessage, formatDate, formatMoney } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, DetailItem, DetailList, Metric } from '@/components/ui/Card';
import { Badge, StatusBadge } from '@/components/ui/Badge';
import { Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { EmptyState, ErrorState, LoadingState, PermissionRequired } from '@/components/ui/States';

export default function SupplierInvoiceDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const canRead = hasPermission(user?.permissions, P.AP_READ);
  const canPay = hasPermission(user?.permissions, P.AP_PAYMENT_CREATE);

  const [invoice, setInvoice] = useState<SupplierInvoice | null>(null);
  const [payments, setPayments] = useState<SupplierPayment[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [loaded, loadedPayments] = await Promise.all([
        accountsPayableApi.getInvoice(id),
        accountsPayableApi.listPayments({ invoiceId: id, size: 50 }).catch(() => ({ content: [] as SupplierPayment[] })),
      ]);
      setInvoice(loaded);
      setPayments(loadedPayments.content);
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
        <PermissionRequired permission={P.AP_READ} action="Viewing supplier bills" />
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="page">
        <LoadingState label="Loading bill…" />
      </div>
    );
  }

  if (error || !invoice) {
    return (
      <div className="page">
        <ErrorState message={error ?? 'Bill not found.'} onRetry={() => void load()} />
      </div>
    );
  }

  const overdue = invoice.status === 'OPEN' && new Date(invoice.dueDate) < startOfToday();

  return (
    <div className="page">
      <PageHeader
        title={invoice.invoiceNumber}
        breadcrumbs={[{ label: 'Bills to pay', href: '/accounts-payable' }, { label: invoice.invoiceNumber }]}
        description={`From ${invoice.supplierName}`}
        actions={
          <>
            <StatusBadge kind="invoice" status={invoice.status === 'OPEN' ? 'UNPAID' : invoice.status} />
            {invoice.status === 'OPEN' && canPay && (
              <Link className="btn btn--primary" href={`/accounts-payable/${id}/pay`}>
                Record a payment
              </Link>
            )}
          </>
        }
      />

      <div className="metric-grid" style={{ marginBottom: 'var(--space-6)' }}>
        <Metric label="Invoice total" value={formatMoney(invoice.totalAmount)} />
        <Metric label="Paid so far" value={formatMoney(invoice.paidAmount)} />
        <Metric
          label="Still owed"
          value={formatMoney(invoice.remainingAmount)}
          meta={overdue ? <Badge variant="error">Overdue since {formatDate(invoice.dueDate)}</Badge> : `Due ${formatDate(invoice.dueDate)}`}
        />
      </div>

      <div className="stack-lg stack">
        <Card>
          <CardBody>
            <DetailList>
              <DetailItem label="Supplier">
                <Link href={`/suppliers/${invoice.supplierId}`}>{invoice.supplierName}</Link>
              </DetailItem>
              <DetailItem label="Invoice date">{formatDate(invoice.invoiceDate)}</DetailItem>
              <DetailItem label="Due date">{formatDate(invoice.dueDate)}</DetailItem>
              <DetailItem label="Notes">{invoice.notes || <span className="text-muted">None</span>}</DetailItem>
            </DetailList>
          </CardBody>
        </Card>

        <Card flush>
          <CardHeader
            title={`Payments (${payments.length})`}
            actions={
              <Link className="btn btn--ghost btn--sm" href={`/suppliers/${invoice.supplierId}/statement`}>
                Supplier statement
              </Link>
            }
          />
          {payments.length === 0 ? (
            <EmptyState
              icon="cash"
              title="No payments yet"
              body="Record a payment when you pay some or all of this bill. The amount still owed updates automatically."
              action={canPay && invoice.status === 'OPEN' ? { label: 'Record a payment', href: `/accounts-payable/${id}/pay` } : undefined}
            />
          ) : (
            <Table>
              <Thead>
                <Tr>
                  <Th>Paid on</Th>
                  <Th>Method</Th>
                  <Th>Reference</Th>
                  <Th className="table__num">Amount</Th>
                </Tr>
              </Thead>
              <Tbody>
                {payments.map((payment) => (
                  <Tr key={payment.id}>
                    <Td>{formatDate(payment.paymentDate)}</Td>
                    <Td>{PAYMENT_METHOD_LABELS[payment.method] ?? payment.method}</Td>
                    <Td>{payment.reference ?? <span className="text-muted">—</span>}</Td>
                    <Td className="table__num">
                      <span className="money">{formatMoney(payment.amount)}</span>
                    </Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
          )}
        </Card>
      </div>
    </div>
  );
}

function startOfToday(): Date {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return today;
}
