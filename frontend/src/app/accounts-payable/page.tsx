'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { invoicesApi, paymentsApi, PayablesSummary, SupplierInvoice } from '@/lib/api/accounts-payable';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Checkbox } from '@/components/ui/Checkbox';
import { Badge } from '@/components/ui/Badge';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/Table';

function statusVariant(status: string) {
  if (status === 'PAID') return 'success';
  if (status === 'CANCELLED') return 'error';
  return 'pending';
}

export default function AccountsPayablePage() {
  const router = useRouter();
  const { user } = useAuth();
  const canRead = user?.permissions?.includes('AP_READ') ?? false;
  const canWrite = user?.permissions?.includes('AP_WRITE') ?? false;

  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [overdueOnly, setOverdueOnly] = useState(false);
  const [invoices, setInvoices] = useState<SupplierInvoice[]>([]);
  const [summary, setSummary] = useState<PayablesSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!canRead) {
      return;
    }
    setIsLoading(true);
    const invoicesRequest = overdueOnly
      ? paymentsApi.overdue()
      : invoicesApi.list(query || undefined, status || undefined);
    Promise.all([invoicesRequest, paymentsApi.summary()])
      .then(([res, totals]) => {
        setInvoices(res.content ?? []);
        setSummary(totals);
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load invoices');
      })
      .finally(() => setIsLoading(false));
  }, [canRead, query, status, overdueOnly]);

  if (!canRead) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Accounts Payable</h1>
        <p role="status">Access is restricted. You do not have permission to view invoices.</p>
      </div>
    );
  }

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-6)' }}>
        <h1>Accounts Payable</h1>
        {canWrite && (
          <Button type="button" onClick={() => router.push('/accounts-payable/new')}>Create Invoice</Button>
        )}
      </div>

      {error && (
        <div role="alert" style={{ marginBottom: 'var(--space-4)', padding: 'var(--space-4)', background: 'var(--color-error-surface)', color: 'var(--color-error)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}

      {summary && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-4)', marginBottom: 'var(--space-6)' }}>
          <p>Invoiced: {summary.totalInvoiced}</p>
          <p>Paid: {summary.paid}</p>
          <p>Outstanding: {summary.outstanding}</p>
          <p>Overdue: {summary.overdue}</p>
        </div>
      )}

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-4)' }}>
        <div style={{ flex: '1 1 16rem' }}>
          <Input id="inv-search" label="Search" placeholder="Invoice number" value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
        <div style={{ flex: '0 1 12rem' }}>
          <Select
            id="inv-status"
            label="Status"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            options={[
              { value: 'OPEN', label: 'Open' },
              { value: 'PAID', label: 'Paid' },
              { value: 'CANCELLED', label: 'Cancelled' },
            ]}
          />
        </div>
        <Checkbox
          id="inv-overdue"
          label="Overdue only"
          checked={overdueOnly}
          onChange={(e) => setOverdueOnly(e.target.checked)}
        />
      </div>

      {isLoading ? (
        <p>Loading invoices...</p>
      ) : invoices.length === 0 ? (
        <div style={{ padding: 'var(--space-8)', textAlign: 'center', backgroundColor: 'var(--color-surface-sunken)', borderRadius: 'var(--radius-md)' }}>
          No invoices found.
        </div>
      ) : (
        <Table>
          <Thead>
            <Tr>
              <Th>Invoice</Th>
              <Th>Supplier</Th>
              <Th>Due date</Th>
              <Th>Outstanding</Th>
              <Th>Status</Th>
              <Th> </Th>
            </Tr>
          </Thead>
          <Tbody>
            {invoices.map((invoice) => (
              <Tr key={invoice.id}>
                <Td>{invoice.invoiceNumber}</Td>
                <Td>{invoice.supplierName}</Td>
                <Td>{invoice.dueDate}</Td>
                <Td>{invoice.remainingAmount}</Td>
                <Td>
                  <Badge variant={statusVariant(invoice.status)}>{invoice.status}</Badge>
                </Td>
                <Td>
                  <Button type="button" variant="secondary" onClick={() => router.push('/accounts-payable/' + invoice.id)}>
                    {canWrite ? 'Manage' : 'View'}
                  </Button>
                </Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      )}
    </div>
  );
}
