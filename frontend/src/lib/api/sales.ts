import { Page, get, post, query } from './http';

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
  /** The payment method's code, e.g. CASH — not its display name. */
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

export const CASH = 'CASH';
export const STORE_CREDIT = 'STORE_CREDIT';

export interface SaleItemRequest {
  productId: string;
  quantity: number;
  /** Requires SALE_DISCOUNT. Omitted entirely when zero so the server applies promotions instead. */
  discountAmount?: number;
}

export interface SalePaymentRequest {
  paymentMethodId: string;
  amount: number;
}

export interface SaleCreateRequest {
  storeId: string;
  terminalId: string;
  registerId: string;
  registerSessionId: string;
  customerId?: string | null;
  items: SaleItemRequest[];
  /** Empty or omitted creates the sale as HELD; otherwise it is settled immediately. */
  payments?: SalePaymentRequest[];
}

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
  storeAddress?: string | null;
  storeContact?: string | null;
  terminalName?: string | null;
  cashierName: string | null;
  customerName: string | null;
  status: string;
  subtotal: number;
  discountTotal: number;
  taxTotal: number;
  grandTotal: number;
  tenderedAmount?: number;
  changeAmount?: number;
  fbrStatus?: string;
  fbrStatusLabel?: string;
  fbrInvoiceNumber?: string | null;
  fbrQrCode?: string | null;
  payments: SalePayment[];
  items: SaleItem[];
}

export interface SaleSearchParams {
  query?: string;
  status?: string;
  customerId?: string;
  cashierId?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const paymentMethodsApi = {
  list: () => get<PaymentMethod[]>('/payment-methods'),
};

export const salesApi = {
  /**
   * The Idempotency-Key is required by the API and is what stops a double-click, a flaky
   * connection or a retry from charging the customer twice.
   */
  create: (body: SaleCreateRequest, idempotencyKey: string) =>
    post<Sale>('/sales', body, { 'Idempotency-Key': idempotencyKey }),

  get: (id: string) => get<Sale>(`/sales/${id}`),

  search: (params: SaleSearchParams = {}) => get<Page<SaleSummary>>(`/sales${query({ ...params })}`),

  receipt: (id: string) => get<SaleReceipt>(`/sales/${id}/receipt`),

  reprint: (id: string) => post<SaleReceipt>(`/sales/${id}/receipt/reprint`),

  hold: (id: string) => post<Sale>(`/sales/${id}/hold`),

  /** Settles a held sale. Payments must total the sale's grand total. */
  resume: (id: string, body: { registerSessionId: string; payments: SalePaymentRequest[] }) =>
    post<Sale>(`/sales/${id}/resume`, body),
};
