'use client';

import { FormEvent, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { invoicesApi } from '@/lib/api/accounts-payable';
import { suppliersApi, Supplier } from '@/lib/api/suppliers';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';

export default function NewInvoicePage() {
  const router = useRouter();
  const { user } = useAuth();
  const canWrite = user?.permissions?.includes('AP_WRITE') ?? false;

  const [invoiceNumber, setInvoiceNumber] = useState('');
  const [supplierId, setSupplierId] = useState('');
  const [invoiceDate, setInvoiceDate] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [totalAmount, setTotalAmount] = useState('');
  const [notes, setNotes] = useState('');
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!canWrite) {
      return;
    }
    suppliersApi.list(undefined, 'true').then((page) => {
      setSuppliers(page.content ?? []);
    }).catch((err: unknown) => {
      setError(err instanceof Error ? err.message : 'Failed to load suppliers');
    });
  }, [canWrite]);

  if (!canWrite) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Create Invoice</h1>
        <p role="status">Access is restricted. You do not have permission to create invoices.</p>
      </div>
    );
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setIsSubmitting(true);
    setError(null);
    try {
      const created = await invoicesApi.create({
        invoiceNumber,
        supplierId,
        invoiceDate,
        dueDate,
        totalAmount: Number(totalAmount),
        notes: notes || null,
      });
      router.push('/accounts-payable/' + created.id);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to create invoice');
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <h1>Create Invoice</h1>
      {error && (
        <div role="alert" style={{ margin: 'var(--space-4) 0', padding: 'var(--space-4)', background: 'var(--color-error-surface)', color: 'var(--color-error)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}
      <form onSubmit={onSubmit}>
        <Input id="inv-number" label="Invoice number" value={invoiceNumber} onChange={(e) => setInvoiceNumber(e.target.value)} required />
        <Select
          id="inv-supplier"
          label="Supplier"
          value={supplierId}
          onChange={(e) => setSupplierId(e.target.value)}
          required
          options={suppliers.map((supplier) => ({ value: supplier.id, label: supplier.name }))}
        />
        <Input id="inv-date" label="Invoice date" type="date" value={invoiceDate} onChange={(e) => setInvoiceDate(e.target.value)} required />
        <Input id="inv-due" label="Due date" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} required />
        <Input id="inv-total" label="Total" type="number" min="0.0001" step="any" value={totalAmount} onChange={(e) => setTotalAmount(e.target.value)} required />
        <Input id="inv-notes" label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} />
        <div style={{ display: 'flex', gap: 'var(--space-3)' }}>
          <Button type="submit" isLoading={isSubmitting} disabled={isSubmitting}>Save</Button>
          <Button type="button" variant="secondary" onClick={() => router.push('/accounts-payable')}>Cancel</Button>
        </div>
      </form>
    </div>
  );
}
