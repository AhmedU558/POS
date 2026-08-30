import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import ReceivePurchaseOrderPage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { purchaseOrdersApi } from '@/lib/api/purchase-orders';

vi.mock('@/lib/api/purchase-orders', () => ({
  purchaseOrdersApi: { get: vi.fn() },
}));

vi.mock('@/lib/api/goods-receipts', () => ({
  goodsReceiptsApi: { create: vi.fn() },
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useParams: () => ({ id: 'po1' }),
}));

function renderWithAuth(permissions: string[]) {
  vi.spyOn(AuthContext, 'useAuth').mockReturnValue({
    user: {
      id: '1',
      username: 'inv',
      email: 'i@test.com',
      firstName: 'I',
      lastName: 'User',
      permissions,
      storeIds: ['store-1'],
    },
    token: 'valid',
    isAuthenticated: true,
    isLoading: false,
    login: vi.fn(),
    logout: vi.fn(),
  } as never);
  return render(<ReceivePurchaseOrderPage />);
}

describe('ReceivePurchaseOrderPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(purchaseOrdersApi.get).mockResolvedValue({
      id: 'po1',
      poNumber: 'PO-100',
      supplierId: 's1',
      supplierName: 'Acme',
      status: 'SUBMITTED',
      notes: null,
      items: [{ id: 'i1', productId: 'p1', sku: 'SKU-A', name: 'Alpha', quantity: 5 }],
      createdAt: '2026-08-31T00:00:00Z',
      updatedAt: '2026-08-31T00:00:00Z',
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('shows ordered quantity and confirm without invoices', async () => {
    renderWithAuth(['INVENTORY_RECEIVE', 'PURCHASE_READ']);

    await waitFor(() => {
      expect(purchaseOrdersApi.get).toHaveBeenCalledWith('po1');
      expect(screen.getByText(/ordered 5/)).toBeTruthy();
      expect(screen.getByText('Confirm receipt')).toBeTruthy();
    });
    expect(screen.queryByText(/invoice/i)).toBeNull();
  });

  it('hides the form without INVENTORY_RECEIVE', () => {
    renderWithAuth(['PURCHASE_READ']);
    expect(screen.getByText('Access is restricted. You do not have permission to receive stock.')).toBeTruthy();
    expect(purchaseOrdersApi.get).not.toHaveBeenCalled();
  });
});
