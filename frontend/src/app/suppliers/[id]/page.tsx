'use client';

import { FormEvent, useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { suppliersApi, SupplierProduct } from '@/lib/api/suppliers';
import { getProducts } from '@/lib/api/catalog';
import { Product } from '@/types/catalog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Checkbox } from '@/components/ui/Checkbox';
import { Badge } from '@/components/ui/Badge';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/Table';

export default function SupplierProfilePage() {
  const { id } = useParams() as { id: string };
  const router = useRouter();
  const { user } = useAuth();
  const canRead = user?.permissions?.includes('SUPPLIER_READ') ?? false;
  const canWrite = user?.permissions?.includes('SUPPLIER_WRITE') ?? false;
  const canReadProducts = user?.permissions?.includes('PRODUCT_READ') ?? false;
  const canReadStatement = user?.permissions?.includes('AP_READ') ?? false;

  const [supplierCode, setSupplierCode] = useState('');
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [address, setAddress] = useState('');
  const [isActive, setIsActive] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [associations, setAssociations] = useState<SupplierProduct[]>([]);
  const [catalog, setCatalog] = useState<Product[]>([]);
  const [selectedProductIds, setSelectedProductIds] = useState<string[]>([]);
  const [isSavingProducts, setIsSavingProducts] = useState(false);

  useEffect(() => {
    if (!canRead || !id) {
      setIsLoading(false);
      return;
    }
    Promise.all([
      suppliersApi.get(id),
      suppliersApi.listProducts(id),
      canReadProducts ? getProducts({ size: 100 }) : Promise.resolve([] as Product[]),
    ])
      .then(([supplier, products, catalogResult]) => {
        setSupplierCode(supplier.supplierCode);
        setName(supplier.name);
        setPhone(supplier.phone ?? '');
        setEmail(supplier.email ?? '');
        setAddress(supplier.address ?? '');
        setIsActive(supplier.active);
        setAssociations(products);
        setSelectedProductIds(products.map((row) => row.productId));
        const list = Array.isArray(catalogResult)
          ? catalogResult
          : ((catalogResult as { content?: Product[] }).content ?? []);
        setCatalog(list);
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load supplier');
      })
      .finally(() => setIsLoading(false));
  }, [canRead, canReadProducts, id]);

  if (!canRead) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>Supplier Profile</h1>
        <p role="status">Access is restricted. You do not have permission to view suppliers.</p>
      </div>
    );
  }

  const saveAssociatedProducts = async (productIds: string[]) => {
    setIsSavingProducts(true);
    setError(null);
    try {
      const updated = await suppliersApi.replaceProducts(id, productIds);
      setAssociations(updated);
      setSelectedProductIds(updated.map((row) => row.productId));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to update associated products');
    } finally {
      setIsSavingProducts(false);
    }
  };

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
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-3)', marginBottom: 'var(--space-4)' }}>
        <Button type="button" variant="secondary" onClick={() => router.push('/suppliers')}>
          Back to suppliers
        </Button>
        {canReadStatement && (
          <Button type="button" variant="secondary" onClick={() => router.push('/suppliers/' + id + '/statement')}>
            Statement
          </Button>
        )}
      </div>
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

      {!isLoading && (
        <section style={{ marginTop: 'var(--space-8)' }}>
          <h2 style={{ fontSize: 'var(--font-size-heading-sm)', marginBottom: 'var(--space-4)' }}>Associated products</h2>
          {associations.length === 0 ? (
            <div style={{ padding: 'var(--space-6)', textAlign: 'center', backgroundColor: 'var(--color-surface-sunken)', borderRadius: 'var(--radius-md)' }}>
              No associated products.
            </div>
          ) : (
            <Table>
              <Thead>
                <Tr>
                  <Th>SKU</Th>
                  <Th>Name</Th>
                  <Th>Status</Th>
                  {canWrite && <Th>Actions</Th>}
                </Tr>
              </Thead>
              <Tbody>
                {associations.map((row) => (
                  <Tr key={row.id}>
                    <Td>{row.sku}</Td>
                    <Td>{row.name}</Td>
                    <Td>
                      <Badge variant={row.active ? 'success' : 'error'}>
                        {row.active ? 'Active' : 'Inactive'}
                      </Badge>
                    </Td>
                    {canWrite && (
                      <Td>
                        <Button
                          type="button"
                          variant="secondary"
                          disabled={isSavingProducts}
                          onClick={() => saveAssociatedProducts(
                            associations
                              .map((item) => item.productId)
                              .filter((productId) => productId !== row.productId)
                          )}
                        >
                          Remove
                        </Button>
                      </Td>
                    )}
                  </Tr>
                ))}
              </Tbody>
            </Table>
          )}

          {canWrite && canReadProducts && (
            <form
              onSubmit={(event) => {
                event.preventDefault();
                void saveAssociatedProducts(selectedProductIds);
              }}
              style={{ marginTop: 'var(--space-6)' }}
            >
              {catalog.map((product) => (
                <Checkbox
                  key={product.id}
                  id={'assoc-' + product.id}
                  label={product.sku + ' — ' + product.name}
                  checked={selectedProductIds.includes(product.id)}
                  onChange={(e) => {
                    setSelectedProductIds((current) =>
                      e.target.checked
                        ? [...current, product.id]
                        : current.filter((item) => item !== product.id)
                    );
                  }}
                />
              ))}
              <Button type="submit" isLoading={isSavingProducts} disabled={isSavingProducts}>
                Save associated products
              </Button>
            </form>
          )}
        </section>
      )}
    </div>
  );
}
