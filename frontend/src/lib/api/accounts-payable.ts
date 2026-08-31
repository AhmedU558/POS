import { Page, get, patch, post, query } from './http';

export type SupplierInvoiceStatus = 'OPEN' | 'PAID' | 'CANCELLED';
export type SupplierPaymentMethod = 'CASH' | 'BANK_TRANSFER' | 'CHEQUE' | 'OTHER';

export const PAYMENT_METHOD_LABELS: Record<SupplierPaymentMethod, string> = {
  CASH: 'Cash',
  BANK_TRANSFER: 'Bank transfer',
  CHEQUE: 'Cheque',
  OTHER: 'Other',
};

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

export type SupplierInvoiceUpdateRequest = Omit<SupplierInvoiceCreateRequest, 'supplierId'>;

export interface SupplierPayment {
  id: string;
  invoiceId: string;
  invoiceNumber: string;
  amount: number;
  paymentDate: string;
  method: SupplierPaymentMethod;
  reference: string | null;
  createdAt: string;
}

export interface SupplierPaymentCreateRequest {
  invoiceId: string;
  amount: number;
  paymentDate: string;
  method: SupplierPaymentMethod;
  reference?: string | null;
}

export interface PayablesSummary {
  totalInvoiced: number;
  paid: number;
  outstanding: number;
  overdue: number;
}

export const accountsPayableApi = {
  listInvoices: (params: { query?: string; status?: string; page?: number; size?: number; sort?: string } = {}) =>
    get<Page<SupplierInvoice>>(`/accounts-payable/invoices${query({ ...params })}`),
  getInvoice: (id: string) => get<SupplierInvoice>(`/accounts-payable/invoices/${id}`),
  createInvoice: (body: SupplierInvoiceCreateRequest) =>
    post<SupplierInvoice>('/accounts-payable/invoices', body),
  updateInvoice: (id: string, body: SupplierInvoiceUpdateRequest) =>
    patch<SupplierInvoice>(`/accounts-payable/invoices/${id}`, body),

  listPayments: (params: { invoiceId?: string; page?: number; size?: number } = {}) =>
    get<Page<SupplierPayment>>(`/accounts-payable/payments${query({ ...params })}`),
  createPayment: (body: SupplierPaymentCreateRequest) =>
    post<SupplierPayment>('/accounts-payable/payments', body),

  overdue: (params: { page?: number; size?: number } = {}) =>
    get<Page<SupplierInvoice>>(`/accounts-payable/overdue${query({ ...params })}`),
  summary: () => get<PayablesSummary>('/accounts-payable/summary'),
};
