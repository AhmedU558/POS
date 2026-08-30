import { apiClient } from '../apiClient';

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface Supplier {
  id: string;
  supplierCode: string;
  name: string;
  phone: string | null;
  email: string | null;
  address: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SupplierProduct {
  id: string;
  productId: string;
  sku: string;
  name: string;
  active: boolean;
}

export interface SupplierStatementLine {
  type: 'INVOICE' | 'PAYMENT';
  date: string;
  invoiceId: string;
  invoiceNumber: string;
  paymentId: string | null;
  debit: number;
  credit: number;
  runningBalance: number;
}

export interface SupplierRequest {
  supplierCode: string;
  name: string;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  isActive: boolean;
}

async function handleResponse<T>(res: Response): Promise<T> {
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(body.error?.message || 'An unexpected error occurred');
  }
  return body.data;
}

export const suppliersApi = {
  list: async (query?: string, isActive?: string, page = 0, size = 50) => {
    const params = new URLSearchParams({ page: page.toString(), size: size.toString() });
    if (query) params.append('query', query);
    if (isActive === 'true' || isActive === 'false') params.append('isActive', isActive);
    const res = await apiClient('/suppliers?' + params.toString(), { method: 'GET' });
    return handleResponse<PaginatedResponse<Supplier>>(res);
  },

  get: async (id: string) => {
    const res = await apiClient('/suppliers/' + id, { method: 'GET' });
    return handleResponse<Supplier>(res);
  },

  create: async (body: SupplierRequest) => {
    const res = await apiClient('/suppliers', { method: 'POST', body: JSON.stringify(body) });
    return handleResponse<Supplier>(res);
  },

  update: async (id: string, body: SupplierRequest) => {
    const res = await apiClient('/suppliers/' + id, { method: 'PATCH', body: JSON.stringify(body) });
    return handleResponse<Supplier>(res);
  },

  listProducts: async (id: string) => {
    const res = await apiClient('/suppliers/' + id + '/products', { method: 'GET' });
    return handleResponse<SupplierProduct[]>(res);
  },

  statement: async (id: string, page = 0, size = 50) => {
    const params = new URLSearchParams({ page: page.toString(), size: size.toString() });
    const res = await apiClient('/suppliers/' + id + '/statement?' + params.toString(), { method: 'GET' });
    return handleResponse<PaginatedResponse<SupplierStatementLine>>(res);
  },

  replaceProducts: async (id: string, productIds: string[]) => {
    const res = await apiClient('/suppliers/' + id + '/products', {
      method: 'PUT',
      body: JSON.stringify({ productIds }),
    });
    return handleResponse<SupplierProduct[]>(res);
  },
};
