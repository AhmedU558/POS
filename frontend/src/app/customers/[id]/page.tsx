'use client';

import { FormEvent, useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { customersApi } from '@/lib/api/customers';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Checkbox } from '@/components/ui/Checkbox';
import { Badge } from '@/components/ui/Badge';

export default function CustomerProfilePage() {
  const { id } = useParams() as { id: string };
  const router = useRouter();
  const { user } = useAuth();
  const canRead = user?.permissions?.includes('CUSTOMER_READ') ?? false;
  const canWrite = user?.permissions?.includes('CUSTOMER_WRITE') ?? false;

  const [customerCode, setCustomerCode] = useState('');
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [address, setAddress] = useState('');
  const [creditLimit, setCreditLimit] = useState('0');
  const [isActive, setIsActive] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!canRead || !id) {
      setIsLoading(false);
      return;
    }
    customersApi.get(id)
      .then((customer) => {
        setCustomerCode(customer.customerCode);
        setName(customer.name);
        setPhone(customer.phone ?? '');
        setEmail(customer.email ?? '');
        setAddress(customer.address ?? '');
        setCreditLimit(String(customer.creditLimit));
        setIsActive(customer.active);
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load customer');
      })
      .finally(() => setIsLoading(false));
  }, [canRead, id]);

  if (!canRead) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Customer Profile</h1>
        <p role="status">Access is restricted. You do not have permission to view customers.</p>
      </div>
    );
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setIsSubmitting(true);
    setError(null);
    try {
      const updated = await customersApi.update(id, {
        customerCode,
        name,
        phone: phone || null,
        email: email || null,
        address: address || null,
        creditLimit: Number(creditLimit),
        isActive,
      });
      setIsActive(updated.active);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to update customer');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <Button type="button" variant="secondary" onClick={() => router.push('/customers')} style={{ marginBottom: 'var(--space-4)' }}>
        Back to customers
      </Button>
      <h1>Customer Profile</h1>
      {isActive ? <Badge variant="success">Active</Badge> : <Badge variant="error">Inactive</Badge>}

      {error && (
        <div role="alert" style={{ margin: 'var(--space-4) 0', padding: 'var(--space-4)', background: 'var(--color-error-surface)', color: 'var(--color-error)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}

      {isLoading ? (
        <p>Loading customer...</p>
      ) : (
        <form onSubmit={onSubmit}>
          <Input id="profile-code" label="Customer code" value={customerCode} onChange={(e) => setCustomerCode(e.target.value)} required disabled={!canWrite} />
          <Input id="profile-name" label="Name" value={name} onChange={(e) => setName(e.target.value)} required disabled={!canWrite} />
          <Input id="profile-phone" label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} disabled={!canWrite} />
          <Input id="profile-email" label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} disabled={!canWrite} />
          <Input id="profile-address" label="Address" value={address} onChange={(e) => setAddress(e.target.value)} disabled={!canWrite} />
          <Input id="profile-credit-limit" label="Credit limit" type="number" min="0" step="0.01" value={creditLimit} onChange={(e) => setCreditLimit(e.target.value)} required disabled={!canWrite} />
          <Checkbox id="profile-active" label="Active" checked={isActive} onChange={(e) => setIsActive(e.target.checked)} disabled={!canWrite} />
          {canWrite && (
            <Button type="submit" isLoading={isSubmitting} disabled={isSubmitting}>Save</Button>
          )}
        </form>
      )}
    </div>
  );
}
