import { apiClient } from '../apiClient';
export interface PaginatedResponse<T> { content: T[]; totalElements: number; totalPages: number; size: number; number: number; }

export interface InventoryBalance {
  productId: string;
  productName: string;
  sku: string;
  storeId: string;
  storeName: string;
  quantity: number;
  lastUpdatedAt: string;
}

export interface InventoryTransaction {
  id: string;
  productId: string;
  productName: string;
  storeId: string;
  transactionType: string;
  quantity: number;
  reason: string;
  createdByUsername: string;
  createdAt: string;
}

export interface InventoryAdjustmentRequest {
  storeId: string;
  productId: string;
  quantity: number;
  reason: string;
}

async function handleResponse<T>(res: Response): Promise<T> {
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(body.error?.message || body.message || 'An unexpected error occurred');
  }
  return body.data;
}

export const inventoryApi = {
  getBalances: async (storeId: string, page = 0, size = 10, categoryId?: string, query?: string) => {
    const params = new URLSearchParams({ storeId, page: page.toString(), size: size.toString() });
    if (categoryId) params.append('categoryId', categoryId);
    if (query) params.append('query', query);
    const res = await apiClient('/inventory?' + params.toString(), { method: 'GET' });
    return handleResponse<PaginatedResponse<InventoryBalance>>(res);
  },

  getBalance: async (productId: string, storeId: string) => {
    const res = await apiClient('/inventory/' + productId + '?storeId=' + storeId, { method: 'GET' });
    return handleResponse<InventoryBalance>(res);
  },

  getMovements: async (productId: string, storeId: string, page = 0, size = 10) => {
    const params = new URLSearchParams({ storeId, page: page.toString(), size: size.toString() });
    const res = await apiClient('/inventory/' + productId + '/movements?' + params.toString(), { method: 'GET' });
    return handleResponse<PaginatedResponse<InventoryTransaction>>(res);
  },

  adjustStock: async (request: InventoryAdjustmentRequest) => {
    const res = await apiClient('/inventory/adjustments', { method: 'POST', body: JSON.stringify(request) });
    return handleResponse<InventoryBalance>(res);
  }
};