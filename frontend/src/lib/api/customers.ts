import { apiClient } from '../apiClient';

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface Customer {
  id: string;
  customerCode: string;
  name: string;
  phone: string | null;
  email: string | null;
  address: string | null;
  creditLimit: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerRequest {
  customerCode: string;
  name: string;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  creditLimit: number;
  isActive: boolean;
}

async function handleResponse<T>(res: Response): Promise<T> {
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(body.error?.message || 'An unexpected error occurred');
  }
  return body.data;
}

export const customersApi = {
  list: async (query?: string, isActive?: string, page = 0, size = 50) => {
    const params = new URLSearchParams({ page: page.toString(), size: size.toString() });
    if (query) params.append('query', query);
    if (isActive === 'true' || isActive === 'false') params.append('isActive', isActive);
    const res = await apiClient('/customers?' + params.toString(), { method: 'GET' });
    return handleResponse<PaginatedResponse<Customer>>(res);
  },

  get: async (id: string) => {
    const res = await apiClient('/customers/' + id, { method: 'GET' });
    return handleResponse<Customer>(res);
  },

  create: async (body: CustomerRequest) => {
    const res = await apiClient('/customers', { method: 'POST', body: JSON.stringify(body) });
    return handleResponse<Customer>(res);
  },

  update: async (id: string, body: CustomerRequest) => {
    const res = await apiClient('/customers/' + id, { method: 'PATCH', body: JSON.stringify(body) });
    return handleResponse<Customer>(res);
  },
};
