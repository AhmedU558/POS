'use client';

import { FormEvent, useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { invoicesApi, SupplierInvoice } from '@/lib/api/accounts-payable';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';

function statusVariant(status: string) {
  if (status === 'PAID') return 'success';
  if (status === 'CANCELLED') return 'error';
  return 'pending';
}

export default function InvoiceDetailPage() {
  const { id } = useParams() as { id: string };
  const router = useRouter();
  const { user } = useAuth();
  const canRead = user?.permissions?.includes('AP_READ') ?? false;
  const canWrite = user?.permissions?.includes('AP_WRITE') ?? false;

  const [invoice, setInvoice] = useState<SupplierInvoice | null>(null);
  const [invoiceNumber, setInvoiceNumber] = useState('');
  const [invoiceDate, setInvoiceDate] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [totalAmount, setTotalAmount] = useState('');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!canRead || !id) {
      setIsLoading(false);
      return;
    }
    invoicesApi.get(id)
      .then((loaded) => {
        setInvoice(loaded);
        setInvoiceNumber(loaded.invoiceNumber);
        setInvoiceDate(loaded.invoiceDate);
        setDueDate(loaded.dueDate);
        setTotalAmount(String(loaded.totalAmount));
        setNotes(loaded.notes ?? '');
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load invoice');
      })
      .finally(() => setIsLoading(false));
  }, [canRead, id]);

  if (!canRead) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Invoice</h1>
        <p role="status">Access is restricted. You do not have permission to view invoices.</p>
      </div>
    );
  }

  const isOpen = invoice?.status === 'OPEN';

  const onSave = async (event: FormEvent) => {
    event.preventDefault();
    setIsSubmitting(true);
    setError(null);
    try {
      const updated = await invoicesApi.update(id, {
        invoiceNumber,
        invoiceDate,
        dueDate,
        totalAmount: Number(totalAmount),
        notes: notes || null,
      });
      setInvoice(updated);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to update invoice');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <Button type="button" variant="secondary" onClick={() => router.push('/accounts-payable')} style={{ marginBottom: 'var(--space-4)' }}>
        Back to invoices
      </Button>
      <h1>Invoice</h1>
      {invoice && <Badge variant={statusVariant(invoice.status)}>{invoice.status}</Badge>}

      {error && (
        <div role="alert" style={{ margin: 'var(--space-4) 0', padding: 'var(--space-4)', background: 'var(--color-error-surface)', color: 'var(--color-error)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}

      {isLoading || !invoice ? (
        <p>Loading invoice...</p>
      ) : (
        <>
          <p>Supplier: {invoice.supplierName}</p>
          <p>Paid: {invoice.paidAmount} — Outstanding: {invoice.remainingAmount}</p>
          <form onSubmit={onSave}>
            <Input id="inv-number" label="Invoice number" value={invoiceNumber} onChange={(e) => setInvoiceNumber(e.target.value)} required disabled={!canWrite || !isOpen} />
            <Input id="inv-date" label="Invoice date" type="date" value={invoiceDate} onChange={(e) => setInvoiceDate(e.target.value)} required disabled={!canWrite || !isOpen} />
            <Input id="inv-due" label="Due date" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} required disabled={!canWrite || !isOpen} />
            <Input id="inv-total" label="Total" type="number" min="0.0001" step="any" value={totalAmount} onChange={(e) => setTotalAmount(e.target.value)} required disabled={!canWrite || !isOpen} />
            <Input id="inv-notes" label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} disabled={!canWrite || !isOpen} />
            {canWrite && isOpen && (
              <Button type="submit" isLoading={isSubmitting} disabled={isSubmitting}>Save</Button>
            )}
          </form>
        </>
      )}
    </div>
  );
}
