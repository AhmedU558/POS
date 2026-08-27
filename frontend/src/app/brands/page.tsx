"use client";

import React, { useState, useEffect } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { getBrands, createBrand, updateBrand, ApiError } from '@/lib/api/catalog';
import { Brand, BrandRequest } from '@/types/catalog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Checkbox } from '@/components/ui/Checkbox';
import { Badge } from '@/components/ui/Badge';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/Table';

export default function BrandsPage() {
  const { user } = useAuth();
  const hasWritePermission = user?.permissions?.includes('PRODUCT_WRITE');

  const [brands, setBrands] = useState<Brand[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [formData, setFormData] = useState<BrandRequest>({ name: '', description: '', isActive: true });
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    fetchBrands();
  }, []);

  const fetchBrands = async () => {
    try {
      setIsLoading(true);
      setError(null);
      const data = await getBrands();
      setBrands(data);
    } catch (err: any) {
      setError(err.message || 'Failed to load brands');
    } finally {
      setIsLoading(false);
    }
  };

  const handleOpenCreate = () => {
    setEditingId(null);
    setFormData({ name: '', description: '', isActive: true });
    setFormError(null);
    setIsFormOpen(true);
  };

  const handleOpenEdit = (brand: Brand) => {
    setEditingId(brand.id);
    setFormData({
      name: brand.name,
      description: brand.description || '',
      isActive: brand.active
    });
    setFormError(null);
    setIsFormOpen(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    setIsSubmitting(true);

    try {
      const request: BrandRequest = {
        name: formData.name,
        description: formData.description || null,
        isActive: formData.isActive
      };

      if (editingId) {
        await updateBrand(editingId, request);
      } else {
        await createBrand(request);
      }
      setIsFormOpen(false);
      fetchBrands();
    } catch (err: any) {
      setFormError(err.message || 'Validation failed');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) return <div style={{ padding: 'var(--space-6)' }}>Loading brands...</div>;
  if (error) return <div style={{ padding: 'var(--space-6)', color: 'var(--color-error)' }}>{error}</div>;

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-6)' }}>
        <h1 style={{ fontSize: 'var(--font-size-heading)' }}>Brands</h1>
        {hasWritePermission && (
          <Button onClick={handleOpenCreate}>Create Brand</Button>
        )}
      </div>

      {isFormOpen && (
        <div style={{
          backgroundColor: 'var(--color-surface)',
          padding: 'var(--space-6)',
          borderRadius: 'var(--radius-lg)',
          boxShadow: 'var(--shadow-md)',
          marginBottom: 'var(--space-6)'
        }}>
          <h2 style={{ marginBottom: 'var(--space-4)' }}>{editingId ? 'Edit Brand' : 'New Brand'}</h2>
          
          {formError && (
            <div style={{
              backgroundColor: 'var(--color-error-surface)',
              color: 'var(--color-error)',
              padding: 'var(--space-3)',
              borderRadius: 'var(--radius-sm)',
              marginBottom: 'var(--space-4)'
            }}>{formError}</div>
          )}

          <form onSubmit={handleSubmit}>
            <Input 
              id="name"
              label="Brand Name" 
              value={formData.name} 
              onChange={e => setFormData({ ...formData, name: e.target.value })} 
              required 
            />
            <Input 
              id="description"
              label="Description" 
              value={formData.description || ''} 
              onChange={e => setFormData({ ...formData, description: e.target.value })} 
            />
            {editingId && (
              <Checkbox 
                id="isActive"
                label="Active"
                checked={formData.isActive}
                onChange={e => setFormData({ ...formData, isActive: e.target.checked })}
              />
            )}
            
            <div style={{ display: 'flex', gap: 'var(--space-4)', marginTop: 'var(--space-6)' }}>
              <Button type="button" variant="secondary" onClick={() => setIsFormOpen(false)} disabled={isSubmitting}>Cancel</Button>
              <Button type="submit" isLoading={isSubmitting}>Save</Button>
            </div>
          </form>
        </div>
      )}

      {brands.length === 0 ? (
        <div style={{ padding: 'var(--space-8)', textAlign: 'center', backgroundColor: 'var(--color-surface-sunken)', borderRadius: 'var(--radius-md)' }}>
          No brands found.
        </div>
      ) : (
        <Table>
          <Thead>
            <Tr>
              <Th>Name</Th>
              <Th>Description</Th>
              <Th>Status</Th>
              <Th style={{ textAlign: 'right' }}>Actions</Th>
            </Tr>
          </Thead>
          <Tbody>
            {brands.map(brand => (
              <Tr key={brand.id}>
                <Td style={{ fontWeight: 'var(--font-weight-medium)' }}>{brand.name}</Td>
                <Td>{brand.description || '-'}</Td>
                <Td>
                  {brand.active ? <Badge variant="success">Active</Badge> : <Badge variant="error">Inactive</Badge>}
                </Td>
                <Td style={{ textAlign: 'right' }}>
                  {hasWritePermission && (
                    <Button variant="secondary" onClick={() => handleOpenEdit(brand)}>Edit</Button>
                  )}
                </Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      )}
    </div>
  );
}
