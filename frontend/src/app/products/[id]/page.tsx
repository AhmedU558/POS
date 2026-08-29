"use client";

import React, { useState, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { 
  getProduct, updateProduct, updateProductStatus, 
  getProductBarcodes, addProductBarcode, removeProductBarcode,
  getProductPrices, addProductPrice,
  getCategories, getBrands, getUnits
} from '@/lib/api/catalog';
import { 
  Product, ProductBarcode, ProductPrice, 
  Category, Brand, Unit, ProductRequest 
} from '@/types/catalog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Checkbox } from '@/components/ui/Checkbox';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/Table';

export default function ProductDetailPage() {
  const { id } = useParams() as { id: string };
  const router = useRouter();
  const { user } = useAuth();
  const hasWritePerm = user?.permissions?.includes('PRODUCT_WRITE');
  const hasPriceWritePerm = user?.permissions?.includes('PRODUCT_PRICE_WRITE');
  
  const [product, setProduct] = useState<Product | null>(null);
  const [barcodes, setBarcodes] = useState<ProductBarcode[]>([]);
  const [prices, setPrices] = useState<ProductPrice[]>([]);
  
  const [categories, setCategories] = useState<Category[]>([]);
  const [brands, setBrands] = useState<Brand[]>([]);
  const [units, setUnits] = useState<Unit[]>([]);
  
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [formData, setFormData] = useState<ProductRequest | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  
  const [newBarcode, setNewBarcode] = useState('');
  const [isPrimaryBarcode, setIsPrimaryBarcode] = useState(false);
  
  const [newPriceAmount, setNewPriceAmount] = useState(0);
  const [newPriceType, setNewPriceType] = useState<'REGULAR' | 'PROMOTIONAL' | 'WHOLESALE'>('REGULAR');
  const [newPriceEffective, setNewPriceEffective] = useState('');

  useEffect(() => {
    loadData();
  }, [id]);

  const loadData = async () => {
    try {
      setIsLoading(true);
      const [prodRes, barcodeRes, priceRes, catData, brandData, unitData] = await Promise.all([
        getProduct(id),
        getProductBarcodes(id),
        getProductPrices(id),
        getCategories(),
        getBrands(),
        getUnits()
      ]);
      setProduct(prodRes);
      setBarcodes(barcodeRes);
      setPrices(priceRes);
      
      setCategories(catData.filter((c: Category) => c.active || c.id === prodRes.categoryId));
      setBrands(brandData.filter((b: Brand) => b.active || b.id === prodRes.brandId));
      setUnits(unitData.filter((u: Unit) => u.active || u.id === prodRes.unitId));
      
      setFormData({
        sku: prodRes.sku,
        name: prodRes.name,
        description: prodRes.description,
        categoryId: prodRes.categoryId,
        brandId: prodRes.brandId,
        unitId: prodRes.unitId,
        purchasePrice: prodRes.purchasePrice,
        sellingPrice: prodRes.sellingPrice,
        wholesalePrice: prodRes.wholesalePrice,
        taxRate: prodRes.taxRate,
        minStock: prodRes.minStock,
        maxStock: prodRes.maxStock,
        trackBatch: prodRes.trackBatch,
        trackExpiry: prodRes.trackExpiry,
        isActive: prodRes.active
      });
      setError(null);
    } catch (err: any) {
      setError(err.message || 'Failed to load product details');
    } finally {
      setIsLoading(false);
    }
  };

  const handleUpdateProduct = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData) return;
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
      await updateProduct(id, payload);
      if (formData.isActive !== product?.active) {
        await updateProductStatus(id, formData.isActive);
      }
      await loadData();
    } catch (err: any) {
      setError(err.message || 'Failed to update product');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleAddBarcode = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newBarcode) return;
    try {
      await addProductBarcode(id, { barcode: newBarcode, isPrimary: isPrimaryBarcode });
      setNewBarcode('');
      setIsPrimaryBarcode(false);
      const res = await getProductBarcodes(id);
      setBarcodes(res);
    } catch (err: any) {
      setError(err.message || 'Failed to add barcode');
    }
  };
  
  const handleRemoveBarcode = async (barcodeId: string) => {
    try {
      await removeProductBarcode(id, barcodeId);
      const res = await getProductBarcodes(id);
      setBarcodes(res);
    } catch (err: any) {
      setError(err.message || 'Failed to remove barcode');
    }
  };

  const handleAddPrice = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newPriceAmount <= 0 || !newPriceEffective) return;
    try {
      await addProductPrice(id, {
        priceType: newPriceType,
        amount: newPriceAmount,
        effectiveFrom: new Date(newPriceEffective).toISOString()
      });
      setNewPriceAmount(0);
      setNewPriceEffective('');
      const res = await getProductPrices(id);
      setPrices(res);
    } catch (err: any) {
      setError(err.message || 'Failed to add price');
    }
  };

  const handleChange = (field: keyof ProductRequest, value: any) => {
    if (formData) {
      setFormData({ ...formData, [field]: value });
    }
  };

  if (isLoading) return <div style={{ padding: 'var(--space-6)' }}>Loading...</div>;
  if (!product || !formData) return <div style={{ padding: 'var(--space-6)' }}>Product not found</div>;

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: '1000px', margin: '0 auto' }}>
      <Button variant="secondary" onClick={() => router.push('/products')} style={{ marginBottom: 'var(--space-4)' }}>
        &larr; Back to Products
      </Button>
      
      <h1 style={{ fontSize: 'var(--font-size-heading)', marginBottom: 'var(--space-6)' }}>
        Manage Product: {product.name}
      </h1>

      {error && (
        <div style={{
          backgroundColor: 'var(--color-error-surface)', color: 'var(--color-error)',
          padding: 'var(--space-3)', borderRadius: 'var(--radius-sm)', marginBottom: 'var(--space-4)'
        }}>{error}</div>
      )}

      <div style={{ display: 'flex', gap: 'var(--space-6)', flexDirection: 'column' }}>
        
        {/* Basic Info */}
        <div style={{ padding: 'var(--space-6)', backgroundColor: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-sm)' }}>
          <h2 style={{ marginBottom: 'var(--space-4)' }}>Product Information</h2>
          <form onSubmit={handleUpdateProduct} style={{ display: 'grid', gap: 'var(--space-4)' }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-4)' }}>
              <Input id="sku" label="SKU" value={formData.sku} onChange={e => handleChange('sku', e.target.value)} required disabled={!hasWritePerm} />
              <Input id="name" label="Name" value={formData.name} onChange={e => handleChange('name', e.target.value)} required disabled={!hasWritePerm} />
            </div>
            
            <Input id="description" label="Description" value={formData.description || ''} onChange={e => handleChange('description', e.target.value)} disabled={!hasWritePerm} />
            
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 'var(--space-4)' }}>
              <Select id="categoryId" label="Category" options={categories.map(c => ({ label: c.name, value: c.id }))} value={formData.categoryId || ''} onChange={e => handleChange('categoryId', e.target.value)} disabled={!hasWritePerm} />
              <Select id="brandId" label="Brand" options={brands.map(b => ({ label: b.name, value: b.id }))} value={formData.brandId || ''} onChange={e => handleChange('brandId', e.target.value)} disabled={!hasWritePerm} />
              <Select id="unitId" label="Unit" options={units.map(u => ({ label: u.name, value: u.id }))} value={formData.unitId || ''} onChange={e => handleChange('unitId', e.target.value)} disabled={!hasWritePerm} />
            </div>
            
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 'var(--space-4)' }}>
              <Input id="purchasePrice" type="number" step="0.01" min="0" label="Purchase Price" value={formData.purchasePrice} onChange={e => handleChange('purchasePrice', parseFloat(e.target.value) || 0)} required disabled={!hasWritePerm} />
              <Input id="sellingPrice" type="number" step="0.01" min="0" label="Selling Price" value={formData.sellingPrice} onChange={e => handleChange('sellingPrice', parseFloat(e.target.value) || 0)} required disabled={!hasWritePerm} />
              <Input id="wholesalePrice" type="number" step="0.01" min="0" label="Wholesale Price" value={formData.wholesalePrice || ''} onChange={e => handleChange('wholesalePrice', e.target.value ? parseFloat(e.target.value) : null)} disabled={!hasWritePerm} />
            </div>
            
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 'var(--space-4)' }}>
              <Input id="taxRate" type="number" step="0.01" min="0" max="1" label="Tax Rate (0-1)" value={formData.taxRate} onChange={e => handleChange('taxRate', parseFloat(e.target.value) || 0)} required disabled={!hasWritePerm} />
              <Input id="minStock" type="number" min="0" label="Min Stock" value={formData.minStock} onChange={e => handleChange('minStock', parseFloat(e.target.value) || 0)} required disabled={!hasWritePerm} />
              <Input id="maxStock" type="number" min="0" label="Max Stock" value={formData.maxStock || ''} onChange={e => handleChange('maxStock', e.target.value ? parseFloat(e.target.value) : null)} disabled={!hasWritePerm} />
            </div>
            
            <div style={{ display: 'flex', gap: 'var(--space-4)' }}>
              <Checkbox id="trackBatch" label="Track Batch" checked={formData.trackBatch} onChange={e => handleChange('trackBatch', e.target.checked)} disabled={!hasWritePerm} />
              <Checkbox id="trackExpiry" label="Track Expiry" checked={formData.trackExpiry} onChange={e => handleChange('trackExpiry', e.target.checked)} disabled={!hasWritePerm} />
              <Checkbox id="isActive" label="Active" checked={formData.isActive} onChange={e => handleChange('isActive', e.target.checked)} disabled={!hasWritePerm} />
            </div>
            
            {hasWritePerm && (
              <div style={{ marginTop: 'var(--space-4)' }}>
                <Button type="submit" isLoading={isSubmitting}>Update Product</Button>
              </div>
            )}
          </form>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-6)' }}>
          {/* Barcodes */}
          <div style={{ padding: 'var(--space-6)', backgroundColor: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-sm)' }}>
            <h2 style={{ marginBottom: 'var(--space-4)' }}>Barcodes</h2>
            {barcodes.length === 0 ? <p>No barcodes.</p> : (
              <Table>
                <Thead><Tr><Th>Barcode</Th><Th>Primary</Th><Th>Actions</Th></Tr></Thead>
                <Tbody>
                  {barcodes.map(b => (
                    <Tr key={b.id}>
                      <Td>{b.barcode}</Td>
                      <Td>{b.isPrimary ? 'Yes' : 'No'}</Td>
                      <Td style={{ textAlign: 'right' }}>
                        {hasWritePerm && <Button variant="secondary" onClick={() => handleRemoveBarcode(b.id)}>Remove</Button>}
                      </Td>
                    </Tr>
                  ))}
                </Tbody>
              </Table>
            )}
            
            {hasWritePerm && (
              <form onSubmit={handleAddBarcode} style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-4)', alignItems: 'flex-end' }}>
                <div style={{ flex: 1 }}><Input id="newBarcode" label="New Barcode" value={newBarcode} onChange={e => setNewBarcode(e.target.value)} required /></div>
                <div style={{ marginBottom: 'var(--space-2)' }}><Checkbox id="isPrimaryBarcode" label="Primary" checked={isPrimaryBarcode} onChange={e => setIsPrimaryBarcode(e.target.checked)} /></div>
                <Button type="submit">Add</Button>
              </form>
            )}
          </div>

          {/* Prices */}
          <div style={{ padding: 'var(--space-6)', backgroundColor: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-sm)' }}>
            <h2 style={{ marginBottom: 'var(--space-4)' }}>Price History</h2>
            {prices.length === 0 ? <p>No price records.</p> : (
              <Table>
                <Thead><Tr><Th>Type</Th><Th>Amount</Th><Th>Effective</Th></Tr></Thead>
                <Tbody>
                  {prices.map(p => (
                    <Tr key={p.id}>
                      <Td>{p.priceType}</Td>
                      <Td>${p.amount.toFixed(2)}</Td>
                      <Td>{new Date(p.effectiveFrom).toLocaleDateString()}</Td>
                    </Tr>
                  ))}
                </Tbody>
              </Table>
            )}
            
            {hasPriceWritePerm && (
              <form onSubmit={handleAddPrice} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', marginTop: 'var(--space-4)' }}>
                <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
                  <div style={{ flex: 1 }}>
                    <Select id="newPriceType" label="Type" options={[{label:'REGULAR', value:'REGULAR'}, {label:'PROMOTIONAL', value:'PROMOTIONAL'}, {label:'WHOLESALE', value:'WHOLESALE'}]} value={newPriceType} onChange={e => setNewPriceType(e.target.value as any)} />
                  </div>
                  <div style={{ flex: 1 }}>
                    <Input id="newPriceAmount" type="number" step="0.01" min="0" label="Amount" value={newPriceAmount} onChange={e => setNewPriceAmount(parseFloat(e.target.value) || 0)} required />
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'flex-end' }}>
                  <div style={{ flex: 1 }}>
                    <Input id="newPriceEffective" type="datetime-local" label="Effective From" value={newPriceEffective} onChange={e => setNewPriceEffective(e.target.value)} required />
                  </div>
                  <Button type="submit">Add Price</Button>
                </div>
              </form>
            )}
          </div>
        </div>
        
      </div>
    </div>
  );
}
