"use client";

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { createProduct, getCategories, getBrands, getUnits } from '@/lib/api/catalog';
import { Category, Brand, Unit, ProductRequest } from '@/types/catalog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Checkbox } from '@/components/ui/Checkbox';

export default function NewProductPage() {
  const router = useRouter();
  
  const [categories, setCategories] = useState<Category[]>([]);
  const [brands, setBrands] = useState<Brand[]>([]);
  const [units, setUnits] = useState<Unit[]>([]);
  
  const [formData, setFormData] = useState<ProductRequest>({
    sku: '',
    name: '',
    description: '',
    categoryId: '',
    brandId: '',
    unitId: '',
    purchasePrice: 0,
    sellingPrice: 0,
    wholesalePrice: 0,
    taxRate: 0,
    minStock: 0,
    maxStock: 0,
    trackBatch: false,
    trackExpiry: false,
    isActive: true
  });
  
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    Promise.all([
      getCategories(),
      getBrands(),
      getUnits()
    ]).then(([catData, brandData, unitData]) => {
      setCategories(catData.filter(c => c.active));
      setBrands(brandData.filter(b => b.active));
      setUnits(unitData.filter(u => u.active));
    }).catch(err => {
      setError('Failed to load reference data.');
    });
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsSubmitting(true);
    
    try {
      const payload: ProductRequest = {
        ...formData,
        categoryId: formData.categoryId || null,
        brandId: formData.brandId || null,
        unitId: formData.unitId || null,
        wholesalePrice: formData.wholesalePrice || null,
        maxStock: formData.maxStock || null,
      };
      const res = await createProduct(payload);
      router.push(`/products/${res.id}`);
    } catch (err: any) {
      setError(err.message || 'Validation failed');
      setIsSubmitting(false);
    }
  };

  const handleChange = (field: keyof ProductRequest, value: any) => {
    setFormData(prev => ({ ...prev, [field]: value }));
  };

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: '800px', margin: '0 auto' }}>
      <h1 style={{ fontSize: 'var(--font-size-heading)', marginBottom: 'var(--space-6)' }}>Create Product</h1>
      
      {error && (
        <div style={{
          backgroundColor: 'var(--color-error-surface)',
          color: 'var(--color-error)',
          padding: 'var(--space-3)',
          borderRadius: 'var(--radius-sm)',
          marginBottom: 'var(--space-4)'
        }}>{error}</div>
      )}
      
      <form onSubmit={handleSubmit} style={{ display: 'grid', gap: 'var(--space-4)' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-4)' }}>
          <Input id="sku" label="SKU" value={formData.sku} onChange={e => handleChange('sku', e.target.value)} required />
          <Input id="name" label="Name" value={formData.name} onChange={e => handleChange('name', e.target.value)} required />
        </div>
        
        <Input id="description" label="Description" value={formData.description || ''} onChange={e => handleChange('description', e.target.value)} />
        
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 'var(--space-4)' }}>
          <Select 
            id="categoryId" label="Category" 
            options={categories.map(c => ({ label: c.name, value: c.id }))} 
            value={formData.categoryId || ''} 
            onChange={e => handleChange('categoryId', e.target.value)} 
          />
          <Select 
            id="brandId" label="Brand" 
            options={brands.map(b => ({ label: b.name, value: b.id }))} 
            value={formData.brandId || ''} 
            onChange={e => handleChange('brandId', e.target.value)} 
          />
          <Select 
            id="unitId" label="Unit" 
            options={units.map(u => ({ label: `${u.name} (${u.abbreviation})`, value: u.id }))} 
            value={formData.unitId || ''} 
            onChange={e => handleChange('unitId', e.target.value)} 
          />
        </div>
        
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 'var(--space-4)' }}>
          <Input id="purchasePrice" type="number" step="0.01" min="0" label="Purchase Price" value={formData.purchasePrice} onChange={e => handleChange('purchasePrice', parseFloat(e.target.value) || 0)} required />
          <Input id="sellingPrice" type="number" step="0.01" min="0" label="Selling Price" value={formData.sellingPrice} onChange={e => handleChange('sellingPrice', parseFloat(e.target.value) || 0)} required />
          <Input id="wholesalePrice" type="number" step="0.01" min="0" label="Wholesale Price (Optional)" value={formData.wholesalePrice || ''} onChange={e => handleChange('wholesalePrice', e.target.value ? parseFloat(e.target.value) : null)} />
        </div>
        
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 'var(--space-4)' }}>
          <Input id="taxRate" type="number" step="0.01" min="0" max="1" label="Tax Rate (0-1)" value={formData.taxRate} onChange={e => handleChange('taxRate', parseFloat(e.target.value) || 0)} required />
          <Input id="minStock" type="number" min="0" label="Min Stock" value={formData.minStock} onChange={e => handleChange('minStock', parseFloat(e.target.value) || 0)} required />
          <Input id="maxStock" type="number" min="0" label="Max Stock (Optional)" value={formData.maxStock || ''} onChange={e => handleChange('maxStock', e.target.value ? parseFloat(e.target.value) : null)} />
        </div>
        
        <div style={{ display: 'flex', gap: 'var(--space-4)' }}>
          <Checkbox id="trackBatch" label="Track Batch" checked={formData.trackBatch} onChange={e => handleChange('trackBatch', e.target.checked)} />
          <Checkbox id="trackExpiry" label="Track Expiry" checked={formData.trackExpiry} onChange={e => handleChange('trackExpiry', e.target.checked)} />
          <Checkbox id="isActive" label="Active" checked={formData.isActive} onChange={e => handleChange('isActive', e.target.checked)} />
        </div>
        
        <div style={{ display: 'flex', gap: 'var(--space-4)', marginTop: 'var(--space-4)' }}>
          <Button type="button" variant="secondary" onClick={() => router.push('/products')} disabled={isSubmitting}>Cancel</Button>
          <Button type="submit" isLoading={isSubmitting}>Create Product</Button>
        </div>
      </form>
    </div>
  );
}
