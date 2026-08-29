'use client';

import { useState, useEffect } from 'react';
import { inventoryApi, InventoryBalance } from '@/lib/api/inventory';
import { useAuth } from '@/features/auth/AuthContext';
import Link from 'next/link';

export default function InventoryOverviewPage() {
  const [balances, setBalances] = useState<InventoryBalance[]>([]);
  const { user } = useAuth();
  
  // For MVP, we will use the user's first permitted store.
  const storeId = user?.storeIds?.[0];

  useEffect(() => {
    if (storeId) {
      inventoryApi.getBalances(storeId, 0, 50).then(res => {
        if (res.content) setBalances(res.content);
      });
    }
  }, [storeId]);

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">Inventory Overview</h1>
        <Link 
          href="/inventory/adjust" 
          className="bg-blue-600 text-white px-4 py-2 rounded shadow hover:bg-blue-700"
        >
          Adjust Stock
        </Link>
      </div>

      {!storeId ? (
        <p>No store selected or available.</p>
      ) : (
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <table className="min-w-full">
            <thead className="bg-gray-50 border-b">
              <tr>
                <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">SKU</th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">Product Name</th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">Store</th>
                <th className="px-6 py-3 text-right text-sm font-semibold text-gray-900">Quantity</th>
                <th className="px-6 py-3 text-right text-sm font-semibold text-gray-900">Last Updated</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {balances.map(balance => (
                <tr key={balance.productId}>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">{balance.sku}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">{balance.productName}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{balance.storeName}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-right font-medium text-gray-900">{balance.quantity}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-500">
                    {new Date(balance.lastUpdatedAt).toLocaleString()}
                  </td>
                </tr>
              ))}
              {balances.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-6 py-4 text-center text-sm text-gray-500">
                    No inventory balances found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}