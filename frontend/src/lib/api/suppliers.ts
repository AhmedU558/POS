import { Page, get, patch, post, put, query } from './http';

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

export interface SupplierRequest {
  supplierCode: string;
  name: string;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  isActive: boolean;
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

export const suppliersApi = {
  list: (params: { query?: string; isActive?: boolean; page?: number; size?: number; sort?: string } = {}) =>
    get<Page<Supplier>>(`/suppliers${query({ ...params })}`),
  get: (id: string) => get<Supplier>(`/suppliers/${id}`),
  create: (body: SupplierRequest) => post<Supplier>('/suppliers', body),
  update: (id: string, body: SupplierRequest) => patch<Supplier>(`/suppliers/${id}`, body),
  listProducts: (id: string) => get<SupplierProduct[]>(`/suppliers/${id}/products`),
  /** Replaces the whole set — send every product the supplier should still carry. */
  replaceProducts: (id: string, productIds: string[]) =>
    put<SupplierProduct[]>(`/suppliers/${id}/products`, { productIds }),
  statement: (id: string, page = 0, size = 25) =>
    get<Page<SupplierStatementLine>>(`/suppliers/${id}/statement${query({ page, size })}`),
};
