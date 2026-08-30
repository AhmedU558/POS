'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { invoicesApi, SupplierInvoice } from '@/lib/api/accounts-payable';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
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
  const [invoices, setInvoices] = useState<SupplierInvoice[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!canRead) {
      return;
    }
    setIsLoading(true);
    invoicesApi.list(query || undefined, status || undefined)
      .then((res) => {
        setInvoices(res.content ?? []);
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load invoices');
      })
      .finally(() => setIsLoading(false));
  }, [canRead, query, status]);

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
