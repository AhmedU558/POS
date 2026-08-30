'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { suppliersApi, Supplier } from '@/lib/api/suppliers';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Badge } from '@/components/ui/Badge';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/Table';

export default function SuppliersPage() {
  const router = useRouter();
  const { user } = useAuth();
  const canRead = user?.permissions?.includes('SUPPLIER_READ') ?? false;
  const canWrite = user?.permissions?.includes('SUPPLIER_WRITE') ?? false;

  const [query, setQuery] = useState('');
  const [isActive, setIsActive] = useState('');
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!canRead) {
      return;
    }
    setIsLoading(true);
    suppliersApi.list(query || undefined, isActive || undefined)
      .then((res) => {
        setSuppliers(res.content ?? []);
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load suppliers');
      })
      .finally(() => setIsLoading(false));
  }, [canRead, query, isActive]);

  if (!canRead) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Suppliers</h1>
        <p role="status">Access is restricted. You do not have permission to view suppliers.</p>
      </div>
    );
  }

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-6)' }}>
        <h1>Suppliers</h1>
        {canWrite && (
          <Button type="button" onClick={() => router.push('/suppliers/new')}>Create Supplier</Button>
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
            id="supplier-search"
            label="Search"
            placeholder="Code, name, phone, or email"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        <div style={{ flex: '0 1 12rem' }}>
          <Select
            id="supplier-status"
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
        <p>Loading suppliers...</p>
      ) : suppliers.length === 0 ? (
        <div style={{ padding: 'var(--space-8)', textAlign: 'center', backgroundColor: 'var(--color-surface-sunken)', borderRadius: 'var(--radius-md)' }}>
          No suppliers found.
        </div>
      ) : (
        <Table>
          <Thead>
            <Tr>
              <Th>Code</Th>
              <Th>Name</Th>
              <Th>Phone</Th>
              <Th>Email</Th>
              <Th>Status</Th>
              <Th> </Th>
            </Tr>
          </Thead>
          <Tbody>
            {suppliers.map((supplier) => (
              <Tr key={supplier.id}>
                <Td>{supplier.supplierCode}</Td>
                <Td>{supplier.name}</Td>
                <Td>{supplier.phone ?? '—'}</Td>
                <Td>{supplier.email ?? '—'}</Td>
                <Td>
                  <Badge variant={supplier.active ? 'success' : 'error'}>
                    {supplier.active ? 'Active' : 'Inactive'}
                  </Badge>
                </Td>
                <Td>
                  <Button type="button" variant="secondary" onClick={() => router.push('/suppliers/' + supplier.id)}>
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
