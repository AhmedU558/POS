'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { customersApi, Customer } from '@/lib/api/customers';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Badge } from '@/components/ui/Badge';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/Table';

export default function CustomersPage() {
  const router = useRouter();
  const { user } = useAuth();
  const canRead = user?.permissions?.includes('CUSTOMER_READ') ?? false;
  const canWrite = user?.permissions?.includes('CUSTOMER_WRITE') ?? false;

  const [query, setQuery] = useState('');
  const [isActive, setIsActive] = useState('');
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!canRead) {
      return;
    }
    setIsLoading(true);
    customersApi.list(query || undefined, isActive || undefined)
      .then((res) => {
        setCustomers(res.content ?? []);
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load customers');
      })
      .finally(() => setIsLoading(false));
  }, [canRead, query, isActive]);

  if (!canRead) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Customers</h1>
        <p role="status">Access is restricted. You do not have permission to view customers.</p>
      </div>
    );
  }

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-6)' }}>
        <h1>Customers</h1>
        {canWrite && (
          <Button type="button" onClick={() => router.push('/customers/new')}>Create Customer</Button>
        )}
      </div>

      {error && (
        <div role="alert" style={{ marginBottom: 'var(--space-4)', padding: 'var(--space-4)', background: 'var(--color-error-surface)', color: 'var(--color-error)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-4)' }}>
        <div style={{ flex: '1 1 16rem' }}>
          <Input
            id="customer-search"
            label="Search"
            placeholder="Code, name, phone, or email"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        <div style={{ flex: '0 1 12rem' }}>
          <Select
            id="customer-status"
            label="Status"
            value={isActive}
            onChange={(e) => setIsActive(e.target.value)}
            options={[
              { value: 'true', label: 'Active' },
              { value: 'false', label: 'Inactive' },
            ]}
          />
        </div>
      </div>

      {isLoading ? (
        <p>Loading customers...</p>
      ) : customers.length === 0 ? (
        <div style={{ padding: 'var(--space-8)', textAlign: 'center', backgroundColor: 'var(--color-surface-sunken)', borderRadius: 'var(--radius-md)' }}>
          No customers found.
        </div>
      ) : (
        <Table>
          <Thead>
            <Tr>
              <Th>Code</Th>
              <Th>Name</Th>
              <Th>Phone</Th>
              <Th>Email</Th>
              <Th>Credit limit</Th>
              <Th>Status</Th>
              <Th> </Th>
            </Tr>
          </Thead>
          <Tbody>
            {customers.map((customer) => (
              <Tr key={customer.id}>
                <Td>{customer.customerCode}</Td>
                <Td>{customer.name}</Td>
                <Td>{customer.phone ?? '—'}</Td>
                <Td>{customer.email ?? '—'}</Td>
                <Td style={{ fontVariantNumeric: 'tabular-nums' }}>{customer.creditLimit}</Td>
                <Td>
                  <Badge variant={customer.active ? 'success' : 'error'}>
                    {customer.active ? 'Active' : 'Inactive'}
                  </Badge>
                </Td>
                <Td>
                  <Button type="button" variant="secondary" onClick={() => router.push('/customers/' + customer.id)}>
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
