import { apiClient } from '../apiClient';
import { PaginatedSales } from './sales';

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

export type CreditTransactionType = 'ISSUE' | 'REDEEM' | 'ADJUST';

export interface CreditTransaction {
  id: string;
  transactionType: CreditTransactionType;
  amount: number;
  referenceType: string | null;
  referenceId: string | null;
  balanceAfter: number;
  createdAt: string;
}

export interface CustomerCredit {
  customerId: string;
  customerCode: string;
  name: string;
  creditLimit: number;
  balance: number;
  currencyCode: string | null;
  status: string | null;
  transactions: PaginatedResponse<CreditTransaction>;
}

export interface CreditTransactionRequest {
  transactionType: CreditTransactionType;
  amount: number;
  currencyCode?: string | null;
  referenceType?: string | null;
  referenceId?: string | null;
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

  getCredit: async (id: string, page = 0, size = 50) => {
    const params = new URLSearchParams({ page: page.toString(), size: size.toString() });
    const res = await apiClient('/customers/' + id + '/credit?' + params.toString(), { method: 'GET' });
    return handleResponse<CustomerCredit>(res);
  },

  listSales: async (id: string) => {
    const res = await apiClient('/customers/' + id + '/sales', { method: 'GET' });
    return handleResponse<PaginatedSales>(res);
  },

  postCredit: async (id: string, body: CreditTransactionRequest) => {
    const res = await apiClient('/customers/' + id + '/credit/transactions', {
      method: 'POST',
      body: JSON.stringify(body),
    });
    return handleResponse<CustomerCredit>(res);
  },
};
