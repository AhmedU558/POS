import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup, fireEvent } from '@testing-library/react';
import PosCheckoutPage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { getProducts } from '@/lib/api/catalog';
import { paymentMethodsApi, salesApi } from '@/lib/api/sales';

vi.mock('@/lib/api/catalog', () => ({
  getProducts: vi.fn(),
}));

vi.mock('@/lib/api/sales', () => ({
  salesApi: { create: vi.fn(), get: vi.fn() },
  paymentMethodsApi: { list: vi.fn() },
}));

function renderWithAuth(permissions: string[]) {
  vi.spyOn(AuthContext, 'useAuth').mockReturnValue({
    user: {
      id: '1',
      username: 'cash',
      email: 'c@test.com',
      firstName: 'C',
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
  return render(<PosCheckoutPage />);
}

describe('PosCheckoutPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(paymentMethodsApi.list).mockResolvedValue([
      { id: 'cash-1', code: 'CASH', name: 'Cash', type: 'CASH', active: true },
      { id: 'card-1', code: 'CARD', name: 'Card', type: 'CARD', active: true },
    ]);
    vi.mocked(getProducts).mockResolvedValue([{
      id: 'p1',
      sku: 'SKU-A',
      name: 'Apple',
      description: null,
      categoryId: null,
      brandId: null,
      unitId: null,
      purchasePrice: 1,
      sellingPrice: 2,
      wholesalePrice: null,
      taxRate: 0.1,
      minStock: 0,
      maxStock: null,
      trackBatch: false,
      trackExpiry: false,
      active: true,
      createdAt: '2026-08-31T00:00:00Z',
      updatedAt: '2026-08-31T00:00:00Z',
    }]);
  });

  afterEach(() => {
    cleanup();
  });

  it('searches, carts, and completes a sale using API totals', async () => {
    vi.mocked(salesApi.create).mockResolvedValue({
      id: 's1',
      receiptNumber: 'R-2026-000001',
      status: 'COMPLETED',
      subtotal: 2,
      discountTotal: 0,
      taxTotal: 0.2,
      grandTotal: 2.2,
      payments: [{ paymentMethod: 'CASH', amount: 2.2 }],
      items: [],
    });
    renderWithAuth(['SALE_CREATE']);

    fireEvent.change(screen.getByLabelText('Product / barcode'), { target: { value: 'SKU-A' } });
    fireEvent.click(screen.getByText('Search'));
    await waitFor(() => {
      expect(getProducts).toHaveBeenCalled();
      expect(screen.getByText('SKU-A — Apple')).toBeTruthy();
    });
    fireEvent.click(screen.getByText('SKU-A — Apple'));
    fireEvent.change(screen.getByLabelText('Terminal'), { target: { value: 't1' } });
    fireEvent.change(screen.getByLabelText('Register'), { target: { value: 'r1' } });
    fireEvent.change(screen.getByLabelText('Register session'), { target: { value: 'sess-1' } });
    await waitFor(() => {
      expect(paymentMethodsApi.list).toHaveBeenCalled();
    });
    fireEvent.click(screen.getByText('Complete sale'));

    await waitFor(() => {
      expect(salesApi.create).toHaveBeenCalled();
      expect(screen.getByText('Receipt R-2026-000001')).toBeTruthy();
      expect(screen.getByText('Total: 2.2')).toBeTruthy();
    });
  });

  it('hides checkout without SALE_CREATE', () => {
    renderWithAuth(['PRODUCT_READ']);
    expect(screen.getByText('Access is restricted. You do not have permission to complete sales.')).toBeTruthy();
  });
});
