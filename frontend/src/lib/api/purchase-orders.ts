import { apiClient } from '../apiClient';

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export type PurchaseOrderStatus = 'DRAFT' | 'SUBMITTED' | 'CANCELLED';

export interface PurchaseOrderItem {
  id: string;
  productId: string;
  sku: string;
  name: string;
  quantity: number;
}

export interface PurchaseOrder {
  id: string;
  poNumber: string;
  supplierId: string;
  supplierName: string;
  status: PurchaseOrderStatus;
  notes: string | null;
  items: PurchaseOrderItem[];
  createdAt: string;
  updatedAt: string;
}

export interface PurchaseOrderRequest {
  poNumber: string;
  supplierId: string;
  notes?: string | null;
  items: { productId: string; quantity: number }[];
}

async function handleResponse<T>(res: Response): Promise<T> {
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(body.error?.message || 'An unexpected error occurred');
  }
  return body.data;
}

export const purchaseOrdersApi = {
  list: async (query?: string, status?: string, page = 0, size = 50) => {
    const params = new URLSearchParams({ page: page.toString(), size: size.toString() });
    if (query) params.append('query', query);
    if (status) params.append('status', status);
    const res = await apiClient('/purchase-orders?' + params.toString(), { method: 'GET' });
    return handleResponse<PaginatedResponse<PurchaseOrder>>(res);
  },

  get: async (id: string) => {
    const res = await apiClient('/purchase-orders/' + id, { method: 'GET' });
    return handleResponse<PurchaseOrder>(res);
  },

  create: async (body: PurchaseOrderRequest) => {
    const res = await apiClient('/purchase-orders', { method: 'POST', body: JSON.stringify(body) });
    return handleResponse<PurchaseOrder>(res);
  },

  update: async (id: string, body: PurchaseOrderRequest) => {
    const res = await apiClient('/purchase-orders/' + id, { method: 'PATCH', body: JSON.stringify(body) });
    return handleResponse<PurchaseOrder>(res);
  },

  submit: async (id: string) => {
    const res = await apiClient('/purchase-orders/' + id + '/submit', { method: 'POST' });
    return handleResponse<PurchaseOrder>(res);
  },

  cancel: async (id: string) => {
    const res = await apiClient('/purchase-orders/' + id + '/cancel', { method: 'POST' });
    return handleResponse<PurchaseOrder>(res);
  },
};
