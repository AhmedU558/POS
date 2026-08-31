import { Page, get, patch, post, query } from './http';

export interface InventoryBalance {
  productId: string;
  productName: string;
  sku: string;
  storeId: string;
  storeName: string;
  quantity: number;
  lastUpdatedAt: string;
}

export interface InventoryTransaction {
  id: string;
  productId: string;
  productName: string;
  storeId: string;
  transactionType: string;
  quantity: number;
  reason: string | null;
  createdByUsername: string | null;
  createdAt: string;
}

export interface InventoryAdjustmentRequest {
  storeId: string;
  productId: string;
  /** Signed: the difference to apply, not the resulting quantity. */
  quantity: number;
  reason: string;
}

export interface InventoryReceiptRequest {
  storeId: string;
  productId: string;
  quantity: number;
  batchNumber?: string | null;
  expirationDate?: string | null;
  manufacturingDate?: string | null;
}

export type InventoryBatchStatus = 'EXPIRED' | 'EXPIRING_TODAY' | 'APPROACHING' | 'OK';

export interface InventoryBatch {
  id: string;
  productId: string;
  productName: string;
  sku: string;
  storeId: string;
  storeName: string;
  batchNumber: string;
  quantity: number;
  expirationDate: string | null;
  manufacturingDate: string | null;
  status: InventoryBatchStatus;
  daysRemaining: number | null;
}

export interface StockAlert {
  id: string;
  storeId: string;
  storeName: string;
  productId: string;
  productName: string;
  sku: string;
  batchId: string | null;
  batchNumber: string | null;
  alertType: 'LOW_STOCK' | 'EXPIRY';
  quantity: number;
  minimumLevel: number | null;
  expirationDate: string | null;
  status: 'OPEN' | 'ACKNOWLEDGED';
  suggestedAction: string;
  daysRemaining: number | null;
  createdAt: string;
  acknowledgedAt: string | null;
}

export interface InventoryReportRow {
  productId: string;
  productName: string;
  sku: string;
  storeId: string;
  storeName: string;
  quantity: number;
  minStock: number;
  belowMinimum: boolean;
  lastUpdatedAt: string;
}

export const inventoryApi = {
  getBalances: (params: { storeId: string; page?: number; size?: number; categoryId?: string; query?: string; sort?: string }) =>
    get<Page<InventoryBalance>>(`/inventory${query({ ...params })}`),

  getBalance: (productId: string, storeId: string) =>
    get<InventoryBalance>(`/inventory/${productId}${query({ storeId })}`),

  getMovements: (productId: string, storeId: string, page = 0, size = 20) =>
    get<Page<InventoryTransaction>>(`/inventory/${productId}/movements${query({ storeId, page, size })}`),

  adjustStock: (body: InventoryAdjustmentRequest) => post<InventoryBalance>('/inventory/adjustments', body),

  receiveStock: (body: InventoryReceiptRequest) => post<InventoryBalance>('/inventory/receipts', body),

  getBatches: (params: { storeId: string; page?: number; size?: number; days?: number; productId?: string }) =>
    get<Page<InventoryBatch>>(`/inventory/batches${query({ ...params })}`),

  getExpiry: (params: { storeId: string; page?: number; size?: number; days?: number }) =>
    get<Page<InventoryBatch>>(`/inventory/expiry${query({ ...params })}`),

  getAlerts: (params: {
    storeId: string;
    page?: number;
    size?: number;
    alertType?: string;
    status?: string;
    days?: number;
  }) => get<Page<StockAlert>>(`/inventory/alerts${query({ ...params })}`),

  acknowledgeAlert: (id: string) => patch<StockAlert>(`/inventory/alerts/${id}/acknowledge`),

  getInventoryReport: (params: { storeId: string; page?: number; size?: number; lowStockOnly?: boolean }) =>
    get<Page<InventoryReportRow>>(`/reports/inventory${query({ ...params })}`),

  getMovementReport: (params: { storeId: string; page?: number; size?: number; productId?: string }) =>
    get<Page<InventoryTransaction>>(`/reports/inventory/movements${query({ ...params })}`),

  getExpiryReport: (params: { storeId: string; page?: number; size?: number; days?: number }) =>
    get<Page<InventoryBatch>>(`/reports/inventory/expiry${query({ ...params })}`),
};
