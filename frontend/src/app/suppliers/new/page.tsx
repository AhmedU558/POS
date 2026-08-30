'use client';

import { FormEvent, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { suppliersApi } from '@/lib/api/suppliers';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Checkbox } from '@/components/ui/Checkbox';

export default function NewSupplierPage() {
  const router = useRouter();
  const { user } = useAuth();
  const canWrite = user?.permissions?.includes('SUPPLIER_WRITE') ?? false;

  const [supplierCode, setSupplierCode] = useState('');
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [address, setAddress] = useState('');
  const [isActive, setIsActive] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!canWrite) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Create Supplier</h1>
        <p role="status">Access is restricted. You do not have permission to create suppliers.</p>
      </div>
    );
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setIsSubmitting(true);
    setError(null);
    try {
      const created = await suppliersApi.create({
        supplierCode,
        name,
        phone: phone || null,
        email: email || null,
        address: address || null,
        isActive,
      });
      router.push('/suppliers/' + created.id);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to create supplier');
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <h1>Create Supplier</h1>
      {error && (
        <div role="alert" style={{ margin: 'var(--space-4) 0', padding: 'var(--space-4)', background: 'var(--color-error-surface)', color: 'var(--color-error)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}
      <form onSubmit={onSubmit}>
        <Input id="supplier-code" label="Supplier code" value={supplierCode} onChange={(e) => setSupplierCode(e.target.value)} required />
        <Input id="supplier-name" label="Name" value={name} onChange={(e) => setName(e.target.value)} required />
        <Input id="supplier-phone" label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} />
        <Input id="supplier-email" label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        <Input id="supplier-address" label="Address" value={address} onChange={(e) => setAddress(e.target.value)} />
        <Checkbox id="supplier-active" label="Active" checked={isActive} onChange={(e) => setIsActive(e.target.checked)} />
        <div style={{ display: 'flex', gap: 'var(--space-3)' }}>
          <Button type="submit" isLoading={isSubmitting} disabled={isSubmitting}>Save</Button>
          <Button type="button" variant="secondary" onClick={() => router.push('/suppliers')}>Cancel</Button>
        </div>
      </form>
    </div>
  );
}
