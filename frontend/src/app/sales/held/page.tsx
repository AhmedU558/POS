'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/features/auth/AuthContext';
import { useStoreContext } from '@/features/session/StoreContext';
import {
  PaymentMethod,
  Sale,
  SalePaymentRequest,
  SaleReceipt,
  SaleSummary,
  paymentMethodsApi,
  salesApi,
} from '@/lib/api/sales';
import { errorMessage, formatDateTime, formatMoney } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { Alert, EmptyState, ErrorState, PermissionRequired, TableSkeleton } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';
import { PaymentDialog } from '@/features/pos/PaymentDialog';
import { ReceiptDialog } from '@/features/pos/ReceiptDialog';

/**
 * Sales parked mid-transaction, waiting to be paid for.
 *
 * Resuming goes through the same payment dialog as the till, tendering against the sale's own
 * total. The previous screen sent a fixed payment of 1, which the server would only ever have
 * accepted as a cash sale, and silently under-recorded any other tender.
 */
export default function HeldSalesPage() {
  const { user } = useAuth();
  const { session } = useStoreContext();
  const toast = useToast();
  const canSell = hasPermission(user?.permissions, P.SALE_CREATE);

  const [sales, setSales] = useState<SaleSummary[] | null>(null);
  const [methods, setMethods] = useState<PaymentMethod[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [resuming, setResuming] = useState<Sale | null>(null);
  const [isLoadingSale, setIsLoadingSale] = useState<string | null>(null);
  const [isSettling, setIsSettling] = useState(false);
  const [paymentError, setPaymentError] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<SaleReceipt | null>(null);
  const [changeDue, setChangeDue] = useState<number | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const page = await salesApi.search({ status: 'HELD', size: 50, sort: 'createdAt,desc' });
      setSales(page.content);
    } catch (caught) {
      setError(errorMessage(caught));
      setSales([]);
    }
  }, []);

  useEffect(() => {
    if (!canSell) return;
    void load();
    paymentMethodsApi
      .list()
      .then((list) => setMethods(list.filter((method) => method.active)))
      .catch(() => setMethods([]));
  }, [canSell, load]);

  if (!canSell) {
    return (
      <div className="page">
        <PermissionRequired permission={P.SALE_CREATE} action="Resuming held sales" />
      </div>
    );
  }

  const beginResume = async (summary: SaleSummary) => {
    setIsLoadingSale(summary.id);
    setPaymentError(null);
    try {
      setResuming(await salesApi.get(summary.id));
    } catch (caught) {
      toast.error(errorMessage(caught));
    } finally {
      setIsLoadingSale(null);
    }
  };

  const settle = async (payments: SalePaymentRequest[], cashTendered: number | null) => {
    if (!resuming || !session) return;
    setIsSettling(true);
    setPaymentError(null);
    try {
      const completed = await salesApi.resume(resuming.id, { registerSessionId: session.id, payments });
      const cashPaid = payments
        .filter((payment) => methods.find((method) => method.id === payment.paymentMethodId)?.code === 'CASH')
        .reduce((sum, payment) => sum + payment.amount, 0);
      setChangeDue(cashTendered === null ? null : Math.max(0, cashTendered - cashPaid));
      setReceipt(await salesApi.receipt(completed.id).catch(() => null));
      setResuming(null);
      await load();
      toast.success(`Sale ${completed.receiptNumber} completed.`);
    } catch (caught) {
      setPaymentError(errorMessage(caught));
    } finally {
      setIsSettling(false);
    }
  };

  return (
    <div className="page">
      <PageHeader
        title="Held sales"
        breadcrumbs={[{ label: 'Sales', href: '/sales' }, { label: 'Held sales' }]}
        description="Sales that were parked before payment. Resume one to take payment and finish it."
        actions={
          <Link className="btn btn--primary" href="/pos">
            Back to the till
          </Link>
        }
      />

      {!session && (
        <div style={{ marginBottom: 'var(--space-4)' }}>
          <Alert tone="warning" title="No till is open">
            A held sale is settled against an open register. Open one before resuming.
            <div style={{ marginTop: 'var(--space-3)' }}>
              <Link className="btn btn--primary btn--sm" href="/register">
                Open a register
              </Link>
            </div>
          </Alert>
        </div>
      )}

      <Card flush>
        {error ? (
          <ErrorState message={error} onRetry={() => void load()} />
        ) : sales === null ? (
          <TableSkeleton rows={4} columns={4} />
        ) : sales.length === 0 ? (
          <EmptyState
            icon="pos"
            title="Nothing on hold"
            body="Sales parked at the till appear here so another cashier — or the same one, later — can finish them."
            action={{ label: 'Go to the till', href: '/pos' }}
          />
        ) : (
          <Table>
            <Thead>
              <Tr>
                <Th>Receipt</Th>
                <Th>Held since</Th>
                <Th>Customer</Th>
                <Th className="table__num">Total</Th>
                <Th className="table__actions">Actions</Th>
              </Tr>
            </Thead>
            <Tbody>
              {sales.map((sale) => (
                <Tr key={sale.id}>
                  <Td>
                    <span className="mono table__primary">{sale.receiptNumber}</span>
                  </Td>
                  <Td>{formatDateTime(sale.createdAt)}</Td>
                  <Td>{sale.customerName ?? <span className="text-muted">Walk-in</span>}</Td>
                  <Td className="table__num">
                    <span className="money">{formatMoney(sale.grandTotal)}</span>
                  </Td>
                  <Td className="table__actions">
                    <Button
                      size="sm"
                      disabled={!session}
                      isLoading={isLoadingSale === sale.id}
                      onClick={() => void beginResume(sale)}
                    >
                      Take payment
                    </Button>
                  </Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        )}
      </Card>

      <PaymentDialog
        open={resuming !== null}
        total={resuming?.grandTotal ?? 0}
        methods={methods}
        // The sale's customer was fixed when it was held and cannot be changed on resume.
        hasCustomer
        isSubmitting={isSettling}
        error={paymentError}
        onCancel={() => setResuming(null)}
        onConfirm={(payments, cashTendered) => void settle(payments, cashTendered)}
      />

      <ReceiptDialog
        receipt={receipt}
        changeDue={changeDue}
        onClose={() => {
          setReceipt(null);
          setChangeDue(null);
        }}
      />
    </div>
  );
}
