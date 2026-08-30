'use client';

import { FormEvent, useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { customersApi, CreditTransactionType, CustomerCredit } from '@/lib/api/customers';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Badge } from '@/components/ui/Badge';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/Table';

export default function StoreCreditPage() {
  const { id } = useParams() as { id: string };
  const router = useRouter();
  const { user } = useAuth();
  const canRead = user?.permissions?.includes('CREDIT_READ') ?? false;
  const canWrite = user?.permissions?.includes('CREDIT_WRITE') ?? false;

  const [credit, setCredit] = useState<CustomerCredit | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [transactionType, setTransactionType] = useState<CreditTransactionType>('ISSUE');
  const [amount, setAmount] = useState('');
  const [currencyCode, setCurrencyCode] = useState('');

  useEffect(() => {
    if (!canRead || !id) {
      setIsLoading(false);
      return;
    }
    customersApi.getCredit(id)
      .then((data) => {
        setCredit(data);
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load store credit');
      })
      .finally(() => setIsLoading(false));
  }, [canRead, id]);

  if (!canRead) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Store Credit</h1>
        <p role="status">Access is restricted. You do not have permission to view store credit.</p>
      </div>
    );
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setIsSubmitting(true);
    setError(null);
    try {
      const updated = await customersApi.postCredit(id, {
        transactionType,
        amount: Number(amount),
        currencyCode: credit?.currencyCode ? null : currencyCode || null,
      });
      setCredit(updated);
      setAmount('');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to post store credit');
    } finally {
      setIsSubmitting(false);
    }
  };

  const accountStatus = credit?.status
    ? credit.status
    : 'No account';

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <Button type="button" variant="secondary" onClick={() => router.push('/customers/' + id)} style={{ marginBottom: 'var(--space-4)' }}>
        Back to profile
      </Button>
      <h1>Store Credit</h1>
      {credit && (
        <p style={{ color: 'var(--color-foreground-muted)' }}>
          {credit.customerCode} — {credit.name}
        </p>
      )}

      {error && (
        <div role="alert" style={{ margin: 'var(--space-4) 0', padding: 'var(--space-4)', background: 'var(--color-error-surface)', color: 'var(--color-error)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}

      {isLoading ? (
        <p>Loading store credit...</p>
      ) : credit && (
        <>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-6)', margin: 'var(--space-6) 0' }}>
            <div>
              <div style={{ fontSize: 'var(--font-size-small)', color: 'var(--color-foreground-muted)' }}>Balance</div>
              <div style={{ fontVariantNumeric: 'tabular-nums', fontSize: 'var(--font-size-heading-sm)' }}>
                {credit.balance} {credit.currencyCode ?? ''}
              </div>
            </div>
            <div>
              <div style={{ fontSize: 'var(--font-size-small)', color: 'var(--color-foreground-muted)' }}>Profile credit limit</div>
              <div style={{ fontVariantNumeric: 'tabular-nums' }}>{credit.creditLimit}</div>
              <div style={{ fontSize: 'var(--font-size-small)', color: 'var(--color-foreground-muted)' }}>Display only — not enforced</div>
            </div>
            <div>
              <div style={{ fontSize: 'var(--font-size-small)', color: 'var(--color-foreground-muted)' }}>Account status</div>
              <Badge variant={credit.status === 'ACTIVE' ? 'success' : 'info'}>{accountStatus}</Badge>
            </div>
          </div>

          {canWrite && (
            <form onSubmit={onSubmit} style={{ maxWidth: '24rem', marginBottom: 'var(--space-8)' }}>
              <Select
                id="credit-type"
                label="Transaction type"
                value={transactionType}
                onChange={(e) => setTransactionType(e.target.value as CreditTransactionType)}
                options={[
                  { value: 'ISSUE', label: 'Issue' },
                  { value: 'REDEEM', label: 'Redeem' },
                  { value: 'ADJUST', label: 'Adjust' },
                ]}
                required
              />
              <Input
                id="credit-amount"
                label={transactionType === 'ADJUST' ? 'Amount (signed)' : 'Amount'}
                type="number"
                step="0.01"
                min={transactionType === 'ADJUST' ? undefined : '0.01'}
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                required
              />
              {!credit.currencyCode && (
                <Input
                  id="credit-currency"
                  label="Currency"
                  value={currencyCode}
                  onChange={(e) => setCurrencyCode(e.target.value.toUpperCase())}
                  maxLength={3}
                  required
                />
              )}
              <Button type="submit" isLoading={isSubmitting} disabled={isSubmitting}>Post transaction</Button>
            </form>
          )}

          <h2 style={{ fontSize: 'var(--font-size-heading-sm)', marginBottom: 'var(--space-4)' }}>Ledger</h2>
          {(credit.transactions.content?.length ?? 0) === 0 ? (
            <div style={{ padding: 'var(--space-8)', textAlign: 'center', backgroundColor: 'var(--color-surface-sunken)', borderRadius: 'var(--radius-md)' }}>
              No credit transactions.
            </div>
          ) : (
            <Table>
              <Thead>
                <Tr>
                  <Th>When</Th>
                  <Th>Type</Th>
                  <Th>Amount</Th>
                  <Th>Balance after</Th>
                </Tr>
              </Thead>
              <Tbody>
                {credit.transactions.content.map((row) => (
                  <Tr key={row.id}>
                    <Td>{row.createdAt}</Td>
                    <Td>{row.transactionType}</Td>
                    <Td style={{ fontVariantNumeric: 'tabular-nums' }}>{row.amount}</Td>
                    <Td style={{ fontVariantNumeric: 'tabular-nums' }}>{row.balanceAfter}</Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
          )}
        </>
      )}
    </div>
  );
}
