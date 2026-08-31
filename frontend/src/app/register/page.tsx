'use client';

import { FormEvent, useState } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { registerSessionsApi, RegisterSession } from '@/lib/api/register-sessions';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

export default function RegisterOpenPage() {
  const { user } = useAuth();
  const canOpen = user?.permissions?.includes('REGISTER_OPEN') ?? false;

  const [registerId, setRegisterId] = useState('');
  const [openingCash, setOpeningCash] = useState('0');
  const [session, setSession] = useState<RegisterSession | null>(null);
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
      setSession(await registerSessionsApi.open(registerId, Number(openingCash)));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to open register');
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
        </section>
      )}
    </div>
  );
}
