"use client";

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { getProducts } from '@/lib/api/catalog';
import { Product } from '@/types/catalog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/Table';

export default function ProductsPage() {
  const router = useRouter();
  const { user } = useAuth();
  const hasWritePermission = user?.permissions?.includes('PRODUCT_WRITE');

  const [products, setProducts] = useState<Product[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    fetchProducts();
  }, [searchQuery]);

  const fetchProducts = async () => {
    try {
      setIsLoading(true);
      setError(null);
      // Pass pagination params as needed. For simplicity we assume size 50.
      const res = await getProducts({ query: searchQuery, size: 50 });
      setProducts(res || []);
    } catch (err: any) {
      setError(err.message || 'Failed to load products');
    } finally {
      setIsLoading(false);
    }
  };

  const handleCreate = () => {
    router.push('/products/new');
  };

  const handleEdit = (id: string) => {
    router.push(`/products/${id}`);
  };

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-6)' }}>
        <h1 style={{ fontSize: 'var(--font-size-heading)' }}>Products</h1>
        {hasWritePermission && (
          <Button onClick={handleCreate}>Create Product</Button>
        )}
      </div>

      <div style={{ marginBottom: 'var(--space-6)' }}>
        <Input
          id="search"
          placeholder="Search products by SKU or Name..."
          value={searchQuery}
          onChange={e => setSearchQuery(e.target.value)}
        />
      </div>

      {error && <div style={{ color: 'var(--color-error)', marginBottom: 'var(--space-4)' }}>{error}</div>}

      {isLoading ? (
        <div style={{ padding: 'var(--space-6)' }}>Loading products...</div>
      ) : products.length === 0 ? (
        <div style={{ padding: 'var(--space-8)', textAlign: 'center', backgroundColor: 'var(--color-surface-sunken)', borderRadius: 'var(--radius-md)' }}>
          No products found.
        </div>
      ) : (
        <Table>
          <Thead>
            <Tr>
              <Th>SKU</Th>
              <Th>Name</Th>
              <Th>Price</Th>
              <Th>Stock</Th>
              <Th>Status</Th>
              <Th style={{ textAlign: 'right' }}>Actions</Th>
            </Tr>
          </Thead>
          <Tbody>
            {products.map(product => (
              <Tr key={product.id}>
                <Td style={{ fontWeight: 'var(--font-weight-medium)' }}>{product.sku}</Td>
                <Td>{product.name}</Td>
                <Td>${product.sellingPrice.toFixed(2)}</Td>
                <Td>
                   Min: {product.minStock} {product.maxStock ? `/ Max: ${product.maxStock}` : ''}
                </Td>
                <Td>
                  {product.active ? <Badge variant="success">Active</Badge> : <Badge variant="error">Inactive</Badge>}
                </Td>
                <Td style={{ textAlign: 'right' }}>
                  <Button variant="secondary" onClick={() => handleEdit(product.id)}>
                    {hasWritePermission ? 'Manage' : 'View'}
                  </Button>
                </Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      )}
    </div>
  );
}
