'use client';

import { useCallback, useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import {
  PAYMENT_METHOD_LABELS,
  SupplierInvoice,
  SupplierPaymentMethod,
  accountsPayableApi,
} from '@/lib/api/accounts-payable';
import { errorMessage, formatDate, formatMoney } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardFooter } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input, Select } from '@/components/ui/Field';
import { Alert, EmptyState, ErrorState, LoadingState, PermissionRequired } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';

export default function PaySupplierInvoicePage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const toast = useToast();
  const { user } = useAuth();
  const canPay = hasPermission(user?.permissions, P.AP_PAYMENT_CREATE);

  const [invoice, setInvoice] = useState<SupplierInvoice | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [amount, setAmount] = useState('');
  const [paymentDate, setPaymentDate] = useState(new Date().toISOString().slice(0, 10));
  const [method, setMethod] = useState<SupplierPaymentMethod>('BANK_TRANSFER');
  const [reference, setReference] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const load = useCallback(async () => {
    setIsLoading(true);
    setLoadError(null);
    try {
      const loaded = await accountsPayableApi.getInvoice(id);
      setInvoice(loaded);
      setAmount(String(loaded.remainingAmount ?? ''));
    } catch (caught) {
      setLoadError(errorMessage(caught));
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    if (canPay) void load();
  }, [canPay, load]);

  if (!canPay) {
    return (
      <div className="page">
        <PermissionRequired permission={P.AP_PAYMENT_CREATE} action="Paying supplier bills" />
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

  if (loadError || !invoice) {
    return (
      <div className="page">
        <ErrorState message={loadError ?? 'Bill not found.'} onRetry={() => void load()} />
      </div>
    );
  }

  if (invoice.status !== 'OPEN') {
    return (
      <div className="page page-narrow">
        <PageHeader
          title="Record a payment"
          breadcrumbs={[
            { label: 'Bills to pay', href: '/accounts-payable' },
            { label: invoice.invoiceNumber, href: `/accounts-payable/${id}` },
            { label: 'Pay' },
          ]}
        />
        <Card>
          <CardBody>
            <EmptyState
              icon="check-circle"
              title={invoice.status === 'PAID' ? 'This bill is already settled' : 'This bill was cancelled'}
              body="No further payment can be recorded against it."
              action={{ label: 'Back to the bill', href: `/accounts-payable/${id}` }}
            />
          </CardBody>
        </Card>
      </div>
    );
  }

  const value = Number(amount);
  const overpaying = Number.isFinite(value) && value > invoice.remainingAmount + 0.005;

  const submit = async () => {
    const found: Record<string, string> = {};
    if (amount.trim() === '' || !Number.isFinite(value) || value <= 0) {
      found.amount = 'Enter the amount you paid.';
    } else if (overpaying) {
      found.amount = `That is more than the ${formatMoney(invoice.remainingAmount)} still owed on this bill.`;
    }
    if (!paymentDate) found.paymentDate = 'Enter the date the payment was made.';
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    setIsSubmitting(true);
    setSubmitError(null);
    try {
      await accountsPayableApi.createPayment({
        invoiceId: id,
        amount: value,
        paymentDate,
        method,
        reference: reference.trim() || null,
      });
      toast.success(`${formatMoney(value)} recorded against ${invoice.invoiceNumber}.`);
      router.push(`/accounts-payable/${id}`);
    } catch (caught) {
      setSubmitError(errorMessage(caught));
      setIsSubmitting(false);
    }
  };

  return (
    <div className="page page-narrow">
      <PageHeader
        title="Record a payment"
        breadcrumbs={[
          { label: 'Bills to pay', href: '/accounts-payable' },
          { label: invoice.invoiceNumber, href: `/accounts-payable/${id}` },
          { label: 'Pay' },
        ]}
        description={`${invoice.supplierName} · ${formatMoney(invoice.remainingAmount)} still owed, due ${formatDate(invoice.dueDate)}`}
      />

      <form
        className="stack"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
        noValidate
      >
        {submitError && <Alert tone="error">{submitError}</Alert>}
        <Card>
          <CardBody className="stack">
            <div className="total-row">
              <span className="total-row__label">Still owed on this bill</span>
              <span className="total-row__value money">{formatMoney(invoice.remainingAmount)}</span>
            </div>
            <Input
              id="payment-amount"
              label="Amount paid"
              required
              type="number"
              min="0"
              step="0.01"
              inputMode="decimal"
              inputSize="lg"
              value={amount}
              error={errors.amount}
              hint="Pre-filled with the full remaining balance. Change it for a part payment."
              onChange={(event) => setAmount(event.target.value)}
              autoFocus
            />
            <div className="form-grid form-grid--2">
              <Input
                id="payment-date"
                label="Payment date"
                required
                type="date"
                value={paymentDate}
                error={errors.paymentDate}
                onChange={(event) => setPaymentDate(event.target.value)}
              />
              <Select
                id="payment-method"
                label="Method"
                required
                placeholder={null}
                value={method}
                onChange={(event) => setMethod(event.target.value as SupplierPaymentMethod)}
                options={Object.entries(PAYMENT_METHOD_LABELS).map(([code, label]) => ({ value: code, label }))}
              />
            </div>
            <Input
              id="payment-reference"
              label="Reference"
              value={reference}
              hint="Optional. Cheque number, transfer reference, anything that identifies the payment."
              onChange={(event) => setReference(event.target.value)}
            />
          </CardBody>
          <CardFooter>
            <Button variant="secondary" onClick={() => router.push(`/accounts-payable/${id}`)} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" isLoading={isSubmitting}>
              Record payment
            </Button>
          </CardFooter>
        </Card>
      </form>
    </div>
  );
}
