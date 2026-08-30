import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup, fireEvent } from '@testing-library/react';
import SalesHistoryPage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { salesApi } from '@/lib/api/sales';

vi.mock('@/lib/api/sales', () => ({
  salesApi: { list: vi.fn(), receipt: vi.fn(), reprint: vi.fn() },
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
  return render(<SalesHistoryPage />);
}

describe('SalesHistoryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(salesApi.list).mockResolvedValue({
      content: [{
        id: 's1',
        receiptNumber: 'R-2026-000001',
        status: 'COMPLETED',
        grandTotal: 2.2,
        createdAt: '2026-08-31T00:00:00Z',
        customerName: null,
        cashierName: 'C User',
      }],
    });
    vi.mocked(salesApi.receipt).mockResolvedValue({
      saleId: 's1',
      receiptNumber: 'R-2026-000001',
      createdAt: '2026-08-31T00:00:00Z',
      storeName: 'Main',
      cashierName: 'C User',
      customerName: null,
      status: 'COMPLETED',
      subtotal: 2,
      discountTotal: 0,
      taxTotal: 0.2,
      grandTotal: 2.2,
      payments: [{ paymentMethod: 'CASH', amount: 2.2 }],
      items: [],
    });
    vi.mocked(salesApi.reprint).mockResolvedValue({
      saleId: 's1',
      receiptNumber: 'R-2026-000001',
      createdAt: '2026-08-31T00:00:00Z',
      storeName: 'Main',
      cashierName: 'C User',
      customerName: null,
      status: 'COMPLETED',
      subtotal: 2,
      discountTotal: 0,
      taxTotal: 0.2,
      grandTotal: 2.2,
      payments: [{ paymentMethod: 'CASH', amount: 2.2 }],
      items: [],
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('lists sales and shows receipt totals from the API', async () => {
    renderWithAuth(['SALE_READ', 'RECEIPT_READ', 'RECEIPT_REPRINT']);

    await waitFor(() => {
      expect(salesApi.list).toHaveBeenCalled();
      expect(screen.getByText('R-2026-000001')).toBeTruthy();
    });

    fireEvent.click(screen.getByText('View receipt'));
    await waitFor(() => {
      expect(salesApi.receipt).toHaveBeenCalledWith('s1');
      expect(screen.getByText('Receipt R-2026-000001')).toBeTruthy();
      expect(screen.getByText('Total: 2.2')).toBeTruthy();
    });

    fireEvent.click(screen.getByText('Reprint'));
    await waitFor(() => {
      expect(salesApi.reprint).toHaveBeenCalledWith('s1');
    });
  });

  it('hides history without SALE_READ', () => {
    renderWithAuth(['PRODUCT_READ']);
    expect(screen.getByText('Access is restricted. You do not have permission to view sales.')).toBeTruthy();
  });
});
