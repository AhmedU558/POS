'use client';

import { useState, useEffect } from 'react';
import { inventoryApi } from '@/lib/api/inventory';
import { getProducts } from '@/lib/api/catalog';
import { Product } from '@/types/catalog';
import { useAuth } from '@/features/auth/AuthContext';
import { useRouter } from 'next/navigation';

export default function StockAdjustmentPage() {
  const router = useRouter();
  const { user } = useAuth();
  const storeId = user?.storeIds?.[0];

  const [products, setProducts] = useState<Product[]>([]);
  const [selectedProductId, setSelectedProductId] = useState('');
  const [quantity, setQuantity] = useState('');
  const [reason, setReason] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    // Fetch products to populate dropdown
    getProducts().then(res => {
      setProducts(res);
    });
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!storeId || !selectedProductId || !quantity || !reason) {
      setError('All fields are required');
      return;
    }

    setLoading(true);
    setError(null);
    setSuccess(false);

    try {
      await inventoryApi.adjustStock({
        storeId,
        productId: selectedProductId,
        quantity: parseFloat(quantity),
        reason
      });
      setSuccess(true);
      setTimeout(() => router.push('/inventory'), 2000);
    } catch (err: any) {
      setError(err.message || 'Failed to adjust stock');
    } finally {
      setLoading(false);
    }
  };

  if (!storeId) return <div className="p-6">No store context available.</div>;

  return (
    <div className="p-6 max-w-2xl mx-auto">
      <h1 className="text-2xl font-bold mb-6">Adjust Stock</h1>
      
      {error && <div className="bg-red-50 text-red-600 p-4 rounded mb-4">{error}</div>}
      {success && <div className="bg-green-50 text-green-600 p-4 rounded mb-4">Stock adjusted successfully. Redirecting...</div>}
      
      <form onSubmit={handleSubmit} className="bg-white rounded-lg shadow p-6 space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Product</label>
          <select 
            value={selectedProductId}
            onChange={(e) => setSelectedProductId(e.target.value)}
            className="w-full border-gray-300 rounded-md shadow-sm p-2 border"
            required
          >
            <option value="">Select a product...</option>
            {products.map(p => (
              <option key={p.id} value={p.id}>{p.name} ({p.sku})</option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Adjustment Quantity (+/-)</label>
          <input 
            type="number" 
            step="0.01"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            className="w-full border-gray-300 rounded-md shadow-sm p-2 border"
            placeholder="e.g. -2 for damaged, 5 for manual count"
            required
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Reason</label>
          <textarea 
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            className="w-full border-gray-300 rounded-md shadow-sm p-2 border"
            rows={3}
            placeholder="Reason for adjustment"
            required
          />
        </div>

        <div className="flex justify-end gap-3 pt-4">
          <button 
            type="button" 
            onClick={() => router.back()}
            className="px-4 py-2 border rounded-md hover:bg-gray-50"
          >
            Cancel
          </button>
          <button 
            type="submit" 
            disabled={loading}
            className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50"
          >
            {loading ? 'Submitting...' : 'Submit Adjustment'}
          </button>
        </div>
      </form>
    </div>
  );
}