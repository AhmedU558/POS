import { Page, get, patch, post, query } from './http';

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
  transactions: Page<CreditTransaction>;
}

export interface CreditTransactionRequest {
  transactionType: CreditTransactionType;
  amount: number;
  currencyCode?: string | null;
  referenceType?: string | null;
  referenceId?: string | null;
}

export interface CustomerSearchParams {
  query?: string;
  isActive?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export const customersApi = {
  list: (params: CustomerSearchParams = {}) => get<Page<Customer>>(`/customers${query({ ...params })}`),
  get: (id: string) => get<Customer>(`/customers/${id}`),
  create: (body: CustomerRequest) => post<Customer>('/customers', body),
  update: (id: string, body: CustomerRequest) => patch<Customer>(`/customers/${id}`, body),
  getCredit: (id: string, page = 0, size = 20) =>
    get<CustomerCredit>(`/customers/${id}/credit${query({ page, size })}`),
  listSales: (id: string, page = 0, size = 20) =>
    get<Page<{ id: string; receiptNumber: string; status: string; grandTotal: number; createdAt: string }>>(
      `/customers/${id}/sales${query({ page, size })}`
    ),
  postCredit: (id: string, body: CreditTransactionRequest) =>
    post<CustomerCredit>(`/customers/${id}/credit/transactions`, body),
};
