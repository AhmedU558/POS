'use client';

import { useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { accountsPayableApi } from '@/lib/api/accounts-payable';
import { Supplier, suppliersApi } from '@/lib/api/suppliers';
import { ApiError } from '@/lib/api/http';
import { errorMessage } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardFooter } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input, Select, Textarea } from '@/components/ui/Field';
import { Alert, EmptyState, LoadingState, PermissionRequired } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';

export default function NewSupplierInvoicePage() {
  const router = useRouter();
  const params = useSearchParams();
  const toast = useToast();
  const { user } = useAuth();
  const canWrite = hasPermission(user?.permissions, P.AP_WRITE);

  const [suppliers, setSuppliers] = useState<Supplier[] | null>(null);
  const [supplierId, setSupplierId] = useState(params?.get('supplierId') ?? '');
  const [invoiceNumber, setInvoiceNumber] = useState('');
  const [invoiceDate, setInvoiceDate] = useState(today());
  const [dueDate, setDueDate] = useState(inDays(30));
  const [totalAmount, setTotalAmount] = useState('');
  const [notes, setNotes] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!canWrite) return;
    suppliersApi
      .list({ isActive: true, size: 200, sort: 'name,asc' })
      .then((page) => setSuppliers(page.content))
      .catch(() => setSuppliers([]));
  }, [canWrite]);

  if (!canWrite) {
    return (
      <div className="page">
        <PermissionRequired permission={P.AP_WRITE} action="Recording supplier bills" />
      </div>
    );
  }

  if (suppliers === null) {
    return (
      <div className="page">
        <LoadingState label="Loading suppliers…" />
      </div>
    );
  }

  if (suppliers.length === 0) {
    return (
      <div className="page page-narrow">
        <PageHeader
          title="Record a bill"
          breadcrumbs={[{ label: 'Bills to pay', href: '/accounts-payable' }, { label: 'Record a bill' }]}
        />
        <Card>
          <CardBody>
            <EmptyState
              icon="suppliers"
              title="You need a supplier first"
              body="A bill always belongs to a supplier. Add one, then come back."
              action={{ label: 'Add supplier', href: '/suppliers/new' }}
            />
          </CardBody>
        </Card>
      </div>
    );
  }

  const submit = async () => {
    const found: Record<string, string> = {};
    const amount = Number(totalAmount);
    if (!supplierId) found.supplierId = 'Choose the supplier who sent the bill.';
    if (!invoiceNumber.trim()) found.invoiceNumber = 'Enter the invoice number from the bill.';
    if (!invoiceDate) found.invoiceDate = 'Enter the date on the invoice.';
    if (!dueDate) found.dueDate = 'Enter when the bill is due.';
    else if (invoiceDate && dueDate < invoiceDate) found.dueDate = 'The due date cannot be before the invoice date.';
    if (totalAmount.trim() === '' || !Number.isFinite(amount) || amount <= 0) {
      found.totalAmount = 'Enter the amount on the bill.';
    }
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const created = await accountsPayableApi.createInvoice({
        invoiceNumber: invoiceNumber.trim(),
        supplierId,
        invoiceDate,
        dueDate,
        totalAmount: amount,
        notes: notes.trim() || null,
      });
      toast.success(`Bill ${created.invoiceNumber} recorded.`);
      router.push(`/accounts-payable/${created.id}`);
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'CONFLICT') {
        setErrors({ invoiceNumber: 'A bill with this number already exists for this supplier.' });
        setSubmitError('That invoice number is already recorded.');
      } else {
        setSubmitError(errorMessage(caught));
      }
      setIsSubmitting(false);
    }
  };

  return (
    <div className="page page-narrow">
      <PageHeader
        title="Record a bill"
        breadcrumbs={[{ label: 'Bills to pay', href: '/accounts-payable' }, { label: 'Record a bill' }]}
        description="Enter the invoice exactly as the supplier sent it. Payments are recorded against it afterwards."
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
            <div className="form-grid form-grid--2">
              <Select
                id="invoice-supplier"
                label="Supplier"
                required
                placeholder="Choose a supplier"
                value={supplierId}
                error={errors.supplierId}
                onChange={(event) => setSupplierId(event.target.value)}
                options={suppliers.map((supplier) => ({ value: supplier.id, label: supplier.name }))}
              />
              <Input
                id="invoice-number"
                label="Invoice number"
                required
                value={invoiceNumber}
                error={errors.invoiceNumber}
                hint="As printed on the supplier's invoice."
                onChange={(event) => setInvoiceNumber(event.target.value)}
              />
            </div>
            <div className="form-grid">
              <Input
                id="invoice-date"
                label="Invoice date"
                required
                type="date"
                value={invoiceDate}
                error={errors.invoiceDate}
                onChange={(event) => setInvoiceDate(event.target.value)}
              />
              <Input
                id="invoice-due"
                label="Due date"
                required
                type="date"
                value={dueDate}
                error={errors.dueDate}
                hint="Overdue bills are flagged from this date."
                onChange={(event) => setDueDate(event.target.value)}
              />
              <Input
                id="invoice-total"
                label="Amount"
                required
                type="number"
                min="0"
                step="0.01"
                inputMode="decimal"
                value={totalAmount}
                error={errors.totalAmount}
                onChange={(event) => setTotalAmount(event.target.value)}
              />
            </div>
            <Textarea
              id="invoice-notes"
              label="Notes"
              rows={2}
              value={notes}
              onChange={(event) => setNotes(event.target.value)}
            />
          </CardBody>
          <CardFooter>
            <Button variant="secondary" onClick={() => router.push('/accounts-payable')} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" isLoading={isSubmitting}>
              Record bill
            </Button>
          </CardFooter>
        </Card>
      </form>
    </div>
  );
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

function inDays(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
}
