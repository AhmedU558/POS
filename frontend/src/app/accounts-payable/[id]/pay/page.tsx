'use client';

import { FormEvent, useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { invoicesApi, paymentsApi, SupplierInvoice, SupplierPaymentMethod } from '@/lib/api/accounts-payable';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';

export default function SupplierPaymentPage() {
  const { id } = useParams() as { id: string };
  const router = useRouter();
  const { user } = useAuth();
  const canPay = user?.permissions?.includes('AP_PAYMENT_CREATE') ?? false;

  const [invoice, setInvoice] = useState<SupplierInvoice | null>(null);
  const [amount, setAmount] = useState('');
  const [paymentDate, setPaymentDate] = useState('');
  const [method, setMethod] = useState<SupplierPaymentMethod | ''>('');
  const [reference, setReference] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!canPay || !id) {
      setIsLoading(false);
      return;
    }
    invoicesApi.get(id)
      .then((loaded) => {
        setInvoice(loaded);
        setAmount(String(loaded.remainingAmount));
        setPaymentDate(new Date().toISOString().slice(0, 10));
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load invoice');
      })
      .finally(() => setIsLoading(false));
  }, [canPay, id]);

  if (!canPay) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Supplier Payment</h1>
        <p role="status">Access is restricted. You do not have permission to record payments.</p>
      </div>
    );
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!method) {
      setError('A payment method is required.');
      return;
    }
    setIsSubmitting(true);
    setError(null);
    try {
      await paymentsApi.create({
        invoiceId: id,
        amount: Number(amount),
        paymentDate,
        method,
        reference: reference || null,
      });
      router.push('/accounts-payable/' + id);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to record payment');
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <Button type="button" variant="secondary" onClick={() => router.push('/accounts-payable/' + id)} style={{ marginBottom: 'var(--space-4)' }}>
        Back to invoice
      </Button>
      <h1>Supplier Payment</h1>
      {invoice && (
        <p>
          Invoice {invoice.invoiceNumber} — outstanding {invoice.remainingAmount}
        </p>
      )}

      {error && (
        <div role="alert" style={{ margin: 'var(--space-4) 0', padding: 'var(--space-4)', background: 'var(--color-error-surface)', color: 'var(--color-error)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}

      {isLoading || !invoice ? (
        <p>Loading invoice...</p>
      ) : (
        <form onSubmit={onSubmit}>
          <Input
            id="pay-amount"
            label="Amount"
            type="number"
            min="0.0001"
            step="any"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            required
          />
          <Input
            id="pay-date"
            label="Payment date"
            type="date"
            value={paymentDate}
            onChange={(e) => setPaymentDate(e.target.value)}
            required
          />
          <Select
            id="pay-method"
            label="Method"
            value={method}
            onChange={(e) => setMethod(e.target.value as SupplierPaymentMethod)}
            options={[
              { value: 'CASH', label: 'Cash' },
              { value: 'BANK_TRANSFER', label: 'Bank transfer' },
              { value: 'CHEQUE', label: 'Cheque' },
              { value: 'OTHER', label: 'Other' },
            ]}
          />
          <Input id="pay-ref" label="Reference" value={reference} onChange={(e) => setReference(e.target.value)} />
          <Button type="submit" isLoading={isSubmitting} disabled={isSubmitting}>
            Confirm payment
          </Button>
        </form>
      )}
    </div>
  );
}
