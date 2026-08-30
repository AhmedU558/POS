import { apiClient } from '../apiClient';

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

async function handleResponse<T>(res: Response): Promise<T> {
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(body.error?.message || 'An unexpected error occurred');
  }
  return body.data;
}

export const goodsReceiptsApi = {
  get: async (id: string) => {
    const res = await apiClient('/goods-receipts/' + id, { method: 'GET' });
    return handleResponse<GoodsReceipt>(res);
  },

  create: async (body: GoodsReceiptRequest) => {
    const res = await apiClient('/goods-receipts', { method: 'POST', body: JSON.stringify(body) });
    return handleResponse<GoodsReceipt>(res);
  },
};
