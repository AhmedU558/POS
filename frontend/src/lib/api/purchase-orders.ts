import { Page, get, patch, post, query } from './http';

/**
 * The order lifecycle the API actually supports is DRAFT → SUBMITTED, with CANCELLED as the exit.
 * There is no RECEIVED status: receiving creates a goods receipt and moves stock, and the order
 * itself stays SUBMITTED. The screens say so rather than implying a stage that does not exist.
 */
export type PurchaseOrderStatus = 'DRAFT' | 'SUBMITTED' | 'CANCELLED';

export interface PurchaseOrderItem {
  id: string;
  productId: string;
  sku: string;
  name: string;
  quantity: number;
}

export interface PurchaseOrder {
  id: string;
  poNumber: string;
  supplierId: string;
  supplierName: string;
  status: PurchaseOrderStatus;
  notes: string | null;
  items: PurchaseOrderItem[];
  createdAt: string;
  updatedAt: string;
}

export interface PurchaseOrderRequest {
  poNumber: string;
  supplierId: string;
  notes?: string | null;
  items: { productId: string; quantity: number }[];
}

export const purchaseOrdersApi = {
  list: (params: { query?: string; status?: string; page?: number; size?: number; sort?: string } = {}) =>
    get<Page<PurchaseOrder>>(`/purchase-orders${query({ ...params })}`),
  get: (id: string) => get<PurchaseOrder>(`/purchase-orders/${id}`),
  create: (body: PurchaseOrderRequest) => post<PurchaseOrder>('/purchase-orders', body),
  update: (id: string, body: PurchaseOrderRequest) => patch<PurchaseOrder>(`/purchase-orders/${id}`, body),
  submit: (id: string) => post<PurchaseOrder>(`/purchase-orders/${id}/submit`),
  cancel: (id: string) => post<PurchaseOrder>(`/purchase-orders/${id}/cancel`),
};

export interface GoodsReceiptItem {
  id: string;
  productId: string;
  sku: string;
  name: string;
  quantity: number;
  batchNumber: string | null;
  expirationDate: string | null;
  manufacturingDate: string | null;
}

export interface GoodsReceipt {
  id: string;
  purchaseOrderId: string;
  storeId: string;
  items: GoodsReceiptItem[];
  createdAt: string;
  updatedAt: string;
}

export interface GoodsReceiptRequest {
  purchaseOrderId: string;
  storeId: string;
  items: {
    productId: string;
    quantity: number;
    batchNumber?: string | null;
    expirationDate?: string | null;
    manufacturingDate?: string | null;
  }[];
}

export const goodsReceiptsApi = {
  get: (id: string) => get<GoodsReceipt>(`/goods-receipts/${id}`),
  create: (body: GoodsReceiptRequest) => post<GoodsReceipt>('/goods-receipts', body),
};
