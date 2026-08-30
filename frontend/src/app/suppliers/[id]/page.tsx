'use client';

import { FormEvent, useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { suppliersApi } from '@/lib/api/suppliers';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Checkbox } from '@/components/ui/Checkbox';
import { Badge } from '@/components/ui/Badge';

export default function SupplierProfilePage() {
  const { id } = useParams() as { id: string };
  const router = useRouter();
  const { user } = useAuth();
  const canRead = user?.permissions?.includes('SUPPLIER_READ') ?? false;
  const canWrite = user?.permissions?.includes('SUPPLIER_WRITE') ?? false;

  const [supplierCode, setSupplierCode] = useState('');
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [address, setAddress] = useState('');
  const [isActive, setIsActive] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!canRead || !id) {
      setIsLoading(false);
      return;
    }
    suppliersApi.get(id)
      .then((supplier) => {
        setSupplierCode(supplier.supplierCode);
        setName(supplier.name);
        setPhone(supplier.phone ?? '');
        setEmail(supplier.email ?? '');
        setAddress(supplier.address ?? '');
        setIsActive(supplier.active);
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load supplier');
      })
      .finally(() => setIsLoading(false));
  }, [canRead, id]);

  if (!canRead) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Supplier Profile</h1>
        <p role="status">Access is restricted. You do not have permission to view suppliers.</p>
      </div>
    );
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setIsSubmitting(true);
    setError(null);
    try {
      const updated = await suppliersApi.update(id, {
        supplierCode,
        name,
        phone: phone || null,
        email: email || null,
        address: address || null,
        isActive,
      });
      setIsActive(updated.active);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to update supplier');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <Button type="button" variant="secondary" onClick={() => router.push('/suppliers')} style={{ marginBottom: 'var(--space-4)' }}>
        Back to suppliers
      </Button>
      <h1>Supplier Profile</h1>
      {isActive ? <Badge variant="success">Active</Badge> : <Badge variant="error">Inactive</Badge>}

      {error && (
        <div role="alert" style={{ margin: 'var(--space-4) 0', padding: 'var(--space-4)', background: 'var(--color-error-surface)', color: 'var(--color-error)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}

      {isLoading ? (
        <p>Loading supplier...</p>
      ) : (
        <form onSubmit={onSubmit}>
          <Input id="profile-code" label="Supplier code" value={supplierCode} onChange={(e) => setSupplierCode(e.target.value)} required disabled={!canWrite} />
          <Input id="profile-name" label="Name" value={name} onChange={(e) => setName(e.target.value)} required disabled={!canWrite} />
          <Input id="profile-phone" label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} disabled={!canWrite} />
          <Input id="profile-email" label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} disabled={!canWrite} />
          <Input id="profile-address" label="Address" value={address} onChange={(e) => setAddress(e.target.value)} disabled={!canWrite} />
          <Checkbox id="profile-active" label="Active" checked={isActive} onChange={(e) => setIsActive(e.target.checked)} disabled={!canWrite} />
          {canWrite && (
            <Button type="submit" isLoading={isSubmitting} disabled={isSubmitting}>Save</Button>
          )}
        </form>
      )}
    </div>
  );
}
