'use client';

import { FormEvent, useState } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { registerSessionsApi, RegisterSession, RegisterSessionSummary } from '@/lib/api/register-sessions';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

export default function RegisterOpenPage() {
  const { user } = useAuth();
  const canOpen = user?.permissions?.includes('REGISTER_OPEN') ?? false;
  const canCash = user?.permissions?.includes('REGISTER_CASH') ?? false;

  const [registerId, setRegisterId] = useState('');
  const [openingCash, setOpeningCash] = useState('0');
  const [session, setSession] = useState<RegisterSession | null>(null);
  const [summary, setSummary] = useState<RegisterSessionSummary | null>(null);
  const [cashAmount, setCashAmount] = useState('');
  const [cashReason, setCashReason] = useState('');
  const [lastMovement, setLastMovement] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!canOpen) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Open Register</h1>
        <p role="status">Access is restricted. You do not have permission to open a register.</p>
      </div>
    );
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setIsSubmitting(true);
    setError(null);
    try {
      const opened = await registerSessionsApi.open(registerId, Number(openingCash));
      setSession(opened);
      setSummary(await registerSessionsApi.summary(opened.id));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to open register');
    } finally {
      setIsSubmitting(false);
    }
  };

  const moveCash = async (direction: 'in' | 'out') => {
    if (!session) {
      return;
    }
    setIsSubmitting(true);
    setError(null);
    try {
      const movement = direction === 'in'
        ? await registerSessionsApi.cashIn(session.id, Number(cashAmount), cashReason)
        : await registerSessionsApi.cashOut(session.id, Number(cashAmount), cashReason);
      setLastMovement(movement.transactionType + ': ' + movement.amount);
      setSummary(await registerSessionsApi.summary(session.id));
      setCashAmount('');
      setCashReason('');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to record cash movement');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <h1>Open Register</h1>
      <p>Register is closed. Enter opening cash to start a session.</p>

      {error && (
        <div role="alert" style={{ margin: 'var(--space-4) 0', padding: 'var(--space-4)', background: 'var(--color-error-surface)', color: 'var(--color-error)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}

      <form onSubmit={onSubmit}>
        <Input id="reg-id" label="Register" value={registerId} onChange={(e) => setRegisterId(e.target.value)} required />
        <Input
          id="reg-opening-cash"
          label="Opening cash"
          type="number"
          min="0"
          step="any"
          value={openingCash}
          onChange={(e) => setOpeningCash(e.target.value)}
          required
        />
        <Button type="submit" isLoading={isSubmitting} disabled={isSubmitting}>
          Open register
        </Button>
      </form>

      {session && (
        <section style={{ marginTop: 'var(--space-8)' }}>
          <h2 style={{ fontSize: 'var(--font-size-heading-sm)' }}>Session open</h2>
          <p>Session {session.id}</p>
          <p>Status: {session.status}</p>
          <p>Opening cash: {session.openingCash}</p>
          {summary && (
            <>
              <p>Cash in: {summary.cashInTotal}</p>
              <p>Cash out: {summary.cashOutTotal}</p>
              <p>Cash sales: {summary.cashSalesTotal}</p>
              <p>Expected cash: {summary.expectedCash}</p>
            </>
          )}
          {canCash && (
            <form onSubmit={(e) => e.preventDefault()} style={{ marginTop: 'var(--space-4)' }}>
              <Input id="reg-cash-amount" label="Cash amount" type="number" min="0" step="any" value={cashAmount} onChange={(e) => setCashAmount(e.target.value)} required />
              <Input id="reg-cash-reason" label="Reason" value={cashReason} onChange={(e) => setCashReason(e.target.value)} />
              <div style={{ display: 'flex', gap: 'var(--space-4)' }}>
                <Button type="button" onClick={() => void moveCash('in')} isLoading={isSubmitting} disabled={isSubmitting}>
                  Cash in
                </Button>
                <Button type="button" variant="secondary" onClick={() => void moveCash('out')} isLoading={isSubmitting} disabled={isSubmitting}>
                  Cash out
                </Button>
              </div>
            </form>
          )}
          {lastMovement && <p>{lastMovement}</p>}
        </section>
      )}
    </div>
  );
}
