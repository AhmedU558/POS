"use client";

import React, { useState, useEffect } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { getCategories, createCategory, updateCategory, ApiError } from '@/lib/api/catalog';
import { Category, CategoryRequest } from '@/types/catalog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Checkbox } from '@/components/ui/Checkbox';
import { Badge } from '@/components/ui/Badge';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/Table';

export default function CategoriesPage() {
  const { user } = useAuth();
  const hasWritePermission = user?.permissions?.includes('PRODUCT_WRITE');

  const [categories, setCategories] = useState<Category[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [formData, setFormData] = useState<CategoryRequest>({ name: '', description: '', parentId: '', isActive: true });
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    fetchCategories();
  }, []);

  const fetchCategories = async () => {
    try {
      setIsLoading(true);
      setError(null);
      const data = await getCategories();
      setCategories(data);
    } catch (err: any) {
      setError(err.message || 'Failed to load categories');
    } finally {
      setIsLoading(false);
    }
  };

  const handleOpenCreate = () => {
    setEditingId(null);
    setFormData({ name: '', description: '', parentId: '', isActive: true });
    setFormError(null);
    setIsFormOpen(true);
  };

  const handleOpenEdit = (category: Category) => {
    setEditingId(category.id);
    setFormData({
      name: category.name,
      description: category.description || '',
      parentId: category.parentId || '',
      isActive: category.active
    });
    setFormError(null);
    setIsFormOpen(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    setIsSubmitting(true);

    try {
      const request: CategoryRequest = {
        name: formData.name,
        description: formData.description || null,
        parentId: formData.parentId || null,
        isActive: formData.isActive
      };

      if (editingId) {
        await updateCategory(editingId, request);
      } else {
        await createCategory(request);
      }
      setIsFormOpen(false);
      fetchCategories();
    } catch (err: any) {
      setFormError(err.message || 'Validation failed');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) return <div style={{ padding: 'var(--space-6)' }}>Loading categories...</div>;
  if (error) return <div style={{ padding: 'var(--space-6)', color: 'var(--color-error)' }}>{error}</div>;

  const categoryOptions = categories.map(c => ({ label: c.name, value: c.id }));

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-6)' }}>
        <h1 style={{ fontSize: 'var(--font-size-heading)' }}>Categories</h1>
        {hasWritePermission && (
          <Button onClick={handleOpenCreate}>Create Category</Button>
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
          <h2 style={{ marginBottom: 'var(--space-4)' }}>{editingId ? 'Edit Category' : 'New Category'}</h2>
          
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
              label="Category Name" 
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
            <Select 
              id="parentId"
              label="Parent Category"
              options={categoryOptions.filter(opt => opt.value !== editingId)} // prevent setting self as parent
              value={formData.parentId || ''}
              onChange={e => setFormData({ ...formData, parentId: e.target.value })}
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

      {categories.length === 0 ? (
        <div style={{ padding: 'var(--space-8)', textAlign: 'center', backgroundColor: 'var(--color-surface-sunken)', borderRadius: 'var(--radius-md)' }}>
          No categories found.
        </div>
      ) : (
        <Table>
          <Thead>
            <Tr>
              <Th>Name</Th>
              <Th>Description</Th>
              <Th>Parent</Th>
              <Th>Status</Th>
              <Th style={{ textAlign: 'right' }}>Actions</Th>
            </Tr>
          </Thead>
          <Tbody>
            {categories.map(category => (
              <Tr key={category.id}>
                <Td style={{ fontWeight: 'var(--font-weight-medium)' }}>{category.name}</Td>
                <Td>{category.description || '-'}</Td>
                <Td>{category.parentId ? categories.find(c => c.id === category.parentId)?.name || category.parentId : '-'}</Td>
                <Td>
                  {category.active ? <Badge variant="success">Active</Badge> : <Badge variant="error">Inactive</Badge>}
                </Td>
                <Td style={{ textAlign: 'right' }}>
                  {hasWritePermission && (
                    <Button variant="secondary" onClick={() => handleOpenEdit(category)}>Edit</Button>
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
