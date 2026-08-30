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
};
