'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { suppliersApi, Supplier, SupplierStatementLine } from '@/lib/api/suppliers';
import { Button } from '@/components/ui/Button';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/Table';

export default function SupplierStatementPage() {
  const { id } = useParams() as { id: string };
  const router = useRouter();
  const { user } = useAuth();
  const canRead = user?.permissions?.includes('AP_READ') ?? false;

  const [supplier, setSupplier] = useState<Supplier | null>(null);
  const [lines, setLines] = useState<SupplierStatementLine[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!canRead || !id) {
      setIsLoading(false);
      return;
    }
    Promise.all([suppliersApi.get(id), suppliersApi.statement(id)])
      .then(([profile, statement]) => {
        setSupplier(profile);
        setLines(statement.content ?? []);
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load statement');
      })
      .finally(() => setIsLoading(false));
  }, [canRead, id]);

  if (!canRead) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Supplier Statement</h1>
        <p role="status">Access is restricted. You do not have permission to view statements.</p>
      </div>
    );
  }

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <Button type="button" variant="secondary" onClick={() => router.push('/suppliers/' + id)} style={{ marginBottom: 'var(--space-4)' }}>
        Back to supplier
      </Button>
      <h1>Supplier Statement</h1>
      {supplier && <p>{supplier.name}</p>}

      {error && (
        <div role="alert" style={{ margin: 'var(--space-4) 0', padding: 'var(--space-4)', background: 'var(--color-error-surface)', color: 'var(--color-error)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}

      {isLoading ? (
        <p>Loading statement...</p>
      ) : lines.length === 0 ? (
        <div style={{ padding: 'var(--space-8)', textAlign: 'center', backgroundColor: 'var(--color-surface-sunken)', borderRadius: 'var(--radius-md)' }}>
          No statement lines.
        </div>
      ) : (
        <Table>
          <Thead>
            <Tr>
              <Th>Date</Th>
              <Th>Type</Th>
              <Th>Invoice</Th>
              <Th>Debit</Th>
              <Th>Credit</Th>
              <Th>Balance</Th>
            </Tr>
          </Thead>
          <Tbody>
            {lines.map((line, index) => (
              <Tr key={(line.paymentId ?? line.invoiceId) + '-' + index}>
                <Td>{line.date}</Td>
                <Td>{line.type}</Td>
                <Td>{line.invoiceNumber}</Td>
                <Td>{line.debit}</Td>
                <Td>{line.credit}</Td>
                <Td>{line.runningBalance}</Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      )}
    </div>
  );
}
