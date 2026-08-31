'use client';

import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { CreditTransactionType, CustomerCredit, customersApi } from '@/lib/api/customers';
import { errorMessage, formatDateTime, formatMoney } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, Metric } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input, Select } from '@/components/ui/Field';
import { Badge } from '@/components/ui/Badge';
import { Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { Modal } from '@/components/ui/Modal';
import { Alert, EmptyState, ErrorState, LoadingState, PermissionRequired } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';

/*
 * The credit ledger uses the API's own vocabulary, translated once:
 *   ISSUE  — credit extended to the customer (they now owe more)
 *   REDEEM — the customer paying some of it back
 *   ADJUST — a correction
 */
const TRANSACTION_LABELS: Record<CreditTransactionType, string> = {
  ISSUE: 'Credit issued',
  REDEEM: 'Payment received',
  ADJUST: 'Adjustment',
};

export default function CustomerCreditPage() {
  const { id } = useParams<{ id: string }>();
  const toast = useToast();
  const { user } = useAuth();
  const canRead = hasPermission(user?.permissions, P.CREDIT_READ);
  const canWrite = hasPermission(user?.permissions, P.CREDIT_WRITE);

  const [credit, setCredit] = useState<CustomerCredit | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [type, setType] = useState<CreditTransactionType>('REDEEM');
  const [amount, setAmount] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      setCredit(await customersApi.getCredit(id, 0, 25));
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    if (canRead) void load();
  }, [canRead, load]);

  if (!canRead) {
    return (
      <div className="page">
        <PermissionRequired permission={P.CREDIT_READ} action="Viewing store credit" />
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="page">
        <LoadingState label="Loading credit account…" />
      </div>
    );
  }

  if (error || !credit) {
    return (
      <div className="page">
        <ErrorState message={error ?? 'Credit account not found.'} onRetry={() => void load()} />
      </div>
    );
  }

  const post = async () => {
    const value = Number(amount);
    if (!Number.isFinite(value) || value <= 0) {
      setFormError('Enter an amount greater than zero.');
      return;
    }
    setIsSaving(true);
    setFormError(null);
    try {
      setCredit(await customersApi.postCredit(id, { transactionType: type, amount: value }));
      setDialogOpen(false);
      setAmount('');
      toast.success(`${TRANSACTION_LABELS[type]} recorded.`);
    } catch (caught) {
      setFormError(errorMessage(caught));
    } finally {
      setIsSaving(false);
    }
  };

  const available = credit.creditLimit - credit.balance;

  return (
    <div className="page">
      <PageHeader
        title="Store credit"
        breadcrumbs={[
          { label: 'Customers', href: '/customers' },
          { label: credit.name, href: `/customers/${id}` },
          { label: 'Store credit' },
        ]}
        description={`What ${credit.name} owes, and every movement on their account.`}
        actions={
          canWrite && (
            <Button
              onClick={() => {
                setType('REDEEM');
                setDialogOpen(true);
              }}
            >
              Record a payment
            </Button>
          )
        }
      />

      <div className="metric-grid" style={{ marginBottom: 'var(--space-6)' }}>
        <Metric label="Credit limit" value={formatMoney(credit.creditLimit)} />
        <Metric label="Currently owed" value={formatMoney(credit.balance)} />
        <Metric
          label="Still available"
          value={formatMoney(Math.max(available, 0))}
          meta={
            available <= 0 && credit.creditLimit > 0 ? (
              <Badge variant="warning">At their limit</Badge>
            ) : (
              'Can be spent on credit'
            )
          }
        />
      </div>

      <Card flush>
        <CardHeader
          title="Account history"
          actions={
            canWrite && (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => {
                  setType('ISSUE');
                  setDialogOpen(true);
                }}
              >
                Issue credit
              </Button>
            )
          }
        />
        {credit.transactions.content.length === 0 ? (
          <CardBody>
            <EmptyState
              icon="expenses"
              title="Nothing on this account yet"
              body="Movements appear when the customer buys on credit at the till, or when you record a payment here."
            />
          </CardBody>
        ) : (
          <Table>
            <Thead>
              <Tr>
                <Th>When</Th>
                <Th>Type</Th>
                <Th className="table__num">Amount</Th>
                <Th className="table__num">Balance after</Th>
              </Tr>
            </Thead>
            <Tbody>
              {credit.transactions.content.map((transaction) => (
                <Tr key={transaction.id}>
                  <Td>{formatDateTime(transaction.createdAt)}</Td>
                  <Td>
                    <Badge variant={transaction.transactionType === 'REDEEM' ? 'success' : 'info'}>
                      {TRANSACTION_LABELS[transaction.transactionType] ?? transaction.transactionType}
                    </Badge>
                    {transaction.referenceType && (
                      <div className="table__secondary">{transaction.referenceType.toLowerCase()}</div>
                    )}
                  </Td>
                  <Td className="table__num">{formatMoney(transaction.amount)}</Td>
                  <Td className="table__num">
                    <span className="money">{formatMoney(transaction.balanceAfter)}</span>
                  </Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        )}
      </Card>

      <Modal
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        title={type === 'REDEEM' ? 'Record a payment' : 'Issue credit'}
        description={
          type === 'REDEEM'
            ? 'Money the customer has paid against their account.'
            : 'Credit extended to the customer. Their balance owed goes up.'
        }
        busy={isSaving}
        footer={
          <>
            <Button variant="secondary" onClick={() => setDialogOpen(false)} disabled={isSaving}>
              Cancel
            </Button>
            <Button onClick={() => void post()} isLoading={isSaving}>
              Record
            </Button>
          </>
        }
      >
        <div className="stack">
          {formError && <Alert tone="error">{formError}</Alert>}
          <Select
            id="credit-type"
            label="Type"
            placeholder={null}
            value={type}
            onChange={(event) => setType(event.target.value as CreditTransactionType)}
            options={[
              { value: 'REDEEM', label: TRANSACTION_LABELS.REDEEM },
              { value: 'ISSUE', label: TRANSACTION_LABELS.ISSUE },
              { value: 'ADJUST', label: TRANSACTION_LABELS.ADJUST },
            ]}
          />
          <Input
            id="credit-amount"
            label="Amount"
            required
            type="number"
            min="0"
            step="0.01"
            inputMode="decimal"
            inputSize="lg"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            autoFocus
          />
        </div>
      </Modal>
    </div>
  );
}
