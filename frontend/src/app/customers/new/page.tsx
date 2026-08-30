'use client';

import { FormEvent, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { customersApi } from '@/lib/api/customers';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Checkbox } from '@/components/ui/Checkbox';

export default function NewCustomerPage() {
  const router = useRouter();
  const { user } = useAuth();
  const canWrite = user?.permissions?.includes('CUSTOMER_WRITE') ?? false;

  const [customerCode, setCustomerCode] = useState('');
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [address, setAddress] = useState('');
  const [creditLimit, setCreditLimit] = useState('0');
  const [isActive, setIsActive] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!canWrite) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Create Customer</h1>
        <p role="status">Access is restricted. You do not have permission to create customers.</p>
      </div>
    );
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setIsSubmitting(true);
    setError(null);
    try {
      const created = await customersApi.create({
        customerCode,
        name,
        phone: phone || null,
        email: email || null,
        address: address || null,
        creditLimit: Number(creditLimit),
        isActive,
      });
      router.push('/customers/' + created.id);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to create customer');
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <h1>Create Customer</h1>
      {error && (
        <div role="alert" style={{ margin: 'var(--space-4) 0', padding: 'var(--space-4)', background: 'var(--color-error-surface)', color: 'var(--color-error)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}
      <form onSubmit={onSubmit}>
        <Input id="customer-code" label="Customer code" value={customerCode} onChange={(e) => setCustomerCode(e.target.value)} required />
        <Input id="customer-name" label="Name" value={name} onChange={(e) => setName(e.target.value)} required />
        <Input id="customer-phone" label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} />
        <Input id="customer-email" label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        <Input id="customer-address" label="Address" value={address} onChange={(e) => setAddress(e.target.value)} />
        <Input id="customer-credit-limit" label="Credit limit" type="number" min="0" step="0.01" value={creditLimit} onChange={(e) => setCreditLimit(e.target.value)} required />
        <Checkbox id="customer-active" label="Active" checked={isActive} onChange={(e) => setIsActive(e.target.checked)} />
        <div style={{ display: 'flex', gap: 'var(--space-3)' }}>
          <Button type="submit" isLoading={isSubmitting} disabled={isSubmitting}>Save</Button>
          <Button type="button" variant="secondary" onClick={() => router.push('/customers')}>Cancel</Button>
        </div>
      </form>
    </div>
  );
}
