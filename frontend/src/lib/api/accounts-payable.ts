import { apiClient } from '../apiClient';

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export type SupplierInvoiceStatus = 'OPEN' | 'PAID' | 'CANCELLED';

export interface SupplierInvoice {
  id: string;
  invoiceNumber: string;
  supplierId: string;
  supplierName: string;
  invoiceDate: string;
  dueDate: string;
  totalAmount: number;
  paidAmount: number;
  remainingAmount: number;
  status: SupplierInvoiceStatus;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SupplierInvoiceCreateRequest {
  invoiceNumber: string;
  supplierId: string;
  invoiceDate: string;
  dueDate: string;
  totalAmount: number;
  notes?: string | null;
}

export interface SupplierInvoiceUpdateRequest {
  invoiceNumber: string;
  invoiceDate: string;
  dueDate: string;
  totalAmount: number;
  notes?: string | null;
}

async function handleResponse<T>(res: Response): Promise<T> {
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(body.error?.message || 'An unexpected error occurred');
  }
  return body.data;
}

export const invoicesApi = {
  list: async (query?: string, status?: string, page = 0, size = 50) => {
    const params = new URLSearchParams({ page: page.toString(), size: size.toString() });
    if (query) params.append('query', query);
    if (status) params.append('status', status);
    const res = await apiClient('/accounts-payable/invoices?' + params.toString(), { method: 'GET' });
    return handleResponse<PaginatedResponse<SupplierInvoice>>(res);
  },

  get: async (id: string) => {
    const res = await apiClient('/accounts-payable/invoices/' + id, { method: 'GET' });
    return handleResponse<SupplierInvoice>(res);
  },

  create: async (body: SupplierInvoiceCreateRequest) => {
    const res = await apiClient('/accounts-payable/invoices', { method: 'POST', body: JSON.stringify(body) });
    return handleResponse<SupplierInvoice>(res);
  },

  update: async (id: string, body: SupplierInvoiceUpdateRequest) => {
    const res = await apiClient('/accounts-payable/invoices/' + id, { method: 'PATCH', body: JSON.stringify(body) });
    return handleResponse<SupplierInvoice>(res);
  },
};
