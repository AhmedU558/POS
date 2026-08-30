'use client';

import { FormEvent, useEffect, useState } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { paymentMethodsApi, salesApi, PaymentMethod, SaleSummary } from '@/lib/api/sales';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/Table';

export default function HeldSalesPage() {
  const { user } = useAuth();
  const canCreate = user?.permissions?.includes('SALE_CREATE') ?? false;

  const [sales, setSales] = useState<SaleSummary[]>([]);
  const [methods, setMethods] = useState<PaymentMethod[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [registerSessionId, setRegisterSessionId] = useState('');
  const [paymentMethodId, setPaymentMethodId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [resumedTotal, setResumedTotal] = useState<string | null>(null);

  useEffect(() => {
    if (!canCreate) {
      return;
    }
    setIsLoading(true);
    Promise.all([
      salesApi.list({ status: 'HELD' }),
      paymentMethodsApi.list(),
    ])
      .then(([list, methodList]) => {
        setSales(list.content ?? []);
        setMethods(methodList);
        const cash = methodList.find((method) => method.code === 'CASH');
        if (cash) {
          setPaymentMethodId(cash.id);
        }
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load held sales');
      })
      .finally(() => setIsLoading(false));
  }, [canCreate]);

  if (!canCreate) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Held Sales</h1>
        <p role="status">Access is restricted. You do not have permission to resume sales.</p>
      </div>
    );
  }

  const onResume = async (event: FormEvent) => {
    event.preventDefault();
    if (!selectedId) {
      return;
    }
    setIsSubmitting(true);
    setError(null);
    try {
      const sale = await salesApi.resume(selectedId, {
        registerSessionId,
        payments: [{ paymentMethodId, amount: 1 }],
      });
      setResumedTotal(String(sale.grandTotal));
      setSales((current) => current.filter((item) => item.id !== selectedId));
      setSelectedId(null);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to resume sale');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <h1>Held Sales</h1>

      {error && (
        <div role="alert" style={{ margin: 'var(--space-4) 0', padding: 'var(--space-4)', background: 'var(--color-error-surface)', color: 'var(--color-error)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}

      {isLoading ? (
        <p>Loading held sales...</p>
      ) : sales.length === 0 ? (
        <div style={{ padding: 'var(--space-6)', textAlign: 'center', backgroundColor: 'var(--color-surface-sunken)', borderRadius: 'var(--radius-md)' }}>
          No held sales.
        </div>
      ) : (
        <Table>
          <Thead>
            <Tr>
              <Th>Receipt</Th>
              <Th>Total</Th>
              <Th> </Th>
            </Tr>
          </Thead>
          <Tbody>
            {sales.map((sale) => (
              <Tr key={sale.id}>
                <Td>{sale.receiptNumber}</Td>
                <Td>{sale.grandTotal}</Td>
                <Td>
                  <Button type="button" variant="secondary" onClick={() => setSelectedId(sale.id)}>
                    Resume
                  </Button>
                </Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      )}

      {selectedId && (
        <form onSubmit={onResume} style={{ marginTop: 'var(--space-6)' }}>
          <Input id="held-session" label="Register session" value={registerSessionId} onChange={(e) => setRegisterSessionId(e.target.value)} required />
          <Select
            id="held-method"
            label="Payment method"
            options={methods.map((method) => ({ value: method.id, label: method.name }))}
            value={paymentMethodId}
            onChange={(e) => setPaymentMethodId(e.target.value)}
            required
          />
          <Button type="submit" isLoading={isSubmitting} disabled={isSubmitting}>
            Complete held sale
          </Button>
        </form>
      )}

      {resumedTotal && <p>Total: {resumedTotal}</p>}
    </div>
  );
}
