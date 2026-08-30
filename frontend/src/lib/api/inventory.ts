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

export interface InventoryReceiptRequest {
  storeId: string;
  productId: string;
  quantity: number;
  batchNumber?: string;
  expirationDate?: string;
  manufacturingDate?: string;
}

export type InventoryBatchStatus = 'EXPIRED' | 'EXPIRING_TODAY' | 'APPROACHING' | 'OK';

export interface InventoryBatch {
  id: string;
  productId: string;
  productName: string;
  sku: string;
  storeId: string;
  storeName: string;
  batchNumber: string;
  quantity: number;
  expirationDate: string | null;
  manufacturingDate: string | null;
  status: InventoryBatchStatus;
  daysRemaining: number | null;
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
  },

  receiveStock: async (request: InventoryReceiptRequest) => {
    const res = await apiClient('/inventory/receipts', { method: 'POST', body: JSON.stringify(request) });
    return handleResponse<InventoryBalance>(res);
  },

  getBatches: async (storeId: string, page = 0, size = 50, days?: number, productId?: string) => {
    const params = new URLSearchParams({ storeId, page: page.toString(), size: size.toString() });
    if (days !== undefined) params.append('days', days.toString());
    if (productId) params.append('productId', productId);
    const res = await apiClient('/inventory/batches?' + params.toString(), { method: 'GET' });
    return handleResponse<PaginatedResponse<InventoryBatch>>(res);
  },

  getExpiry: async (storeId: string, page = 0, size = 50, days?: number) => {
    const params = new URLSearchParams({ storeId, page: page.toString(), size: size.toString() });
    if (days !== undefined) params.append('days', days.toString());
    const res = await apiClient('/inventory/expiry?' + params.toString(), { method: 'GET' });
    return handleResponse<PaginatedResponse<InventoryBatch>>(res);
  }
};