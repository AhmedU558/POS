import { apiClient } from '../apiClient';

export interface SaleItem {
  productId: string;
  sku: string;
  name: string;
  quantity: number;
  unitPrice: number;
  discountAmount: number;
  taxAmount: number;
  lineTotal: number;
}

export interface SalePayment {
  paymentMethod: string;
  amount: number;
}

export interface Sale {
  id: string;
  receiptNumber: string;
  status: string;
  subtotal: number;
  discountTotal: number;
  taxTotal: number;
  grandTotal: number;
  payments: SalePayment[];
  items: SaleItem[];
}

export interface PaymentMethod {
  id: string;
  code: string;
  name: string;
  type: string;
  active: boolean;
}

export interface SaleCreateRequest {
  storeId: string;
  terminalId: string;
  registerId: string;
  registerSessionId: string;
  customerId?: string | null;
  items: { productId: string; quantity: number }[];
  payments: { paymentMethodId: string; amount: number }[];
}

async function handleResponse<T>(res: Response): Promise<T> {
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(body.error?.message || 'An unexpected error occurred');
  }
  return body.data;
}

export const paymentMethodsApi = {
  list: async () => {
    const res = await apiClient('/payment-methods', { method: 'GET' });
    return handleResponse<PaymentMethod[]>(res);
  },
};

export interface SaleSummary {
  id: string;
  receiptNumber: string;
  status: string;
  grandTotal: number;
  createdAt: string;
  customerName: string | null;
  cashierName: string | null;
}

export interface SaleReceipt {
  saleId: string;
  receiptNumber: string;
  createdAt: string;
  storeName: string;
  cashierName: string | null;
  customerName: string | null;
  status: string;
  subtotal: number;
  discountTotal: number;
  taxTotal: number;
  grandTotal: number;
  payments: SalePayment[];
  items: SaleItem[];
}

export interface PaginatedSales {
  content: SaleSummary[];
}

export const salesApi = {
  create: async (body: SaleCreateRequest, idempotencyKey: string) => {
    const res = await apiClient('/sales', {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify(body),
    });
    return handleResponse<Sale>(res);
  },

  get: async (id: string) => {
    const res = await apiClient('/sales/' + id, { method: 'GET' });
    return handleResponse<Sale>(res);
  },

  list: async (filters: { query?: string; status?: string; customerId?: string } = {}) => {
    const params = new URLSearchParams();
    if (filters.query) params.set('query', filters.query);
    if (filters.status) params.set('status', filters.status);
    if (filters.customerId) params.set('customerId', filters.customerId);
    const res = await apiClient('/sales?' + params.toString(), { method: 'GET' });
    return handleResponse<PaginatedSales>(res);
  },

  receipt: async (id: string) => {
    const res = await apiClient('/sales/' + id + '/receipt', { method: 'GET' });
    return handleResponse<SaleReceipt>(res);
  },

  reprint: async (id: string) => {
    const res = await apiClient('/sales/' + id + '/receipt/reprint', { method: 'POST' });
    return handleResponse<SaleReceipt>(res);
  },
};
