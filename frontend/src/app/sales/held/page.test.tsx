import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup, fireEvent } from '@testing-library/react';
import HeldSalesPage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { paymentMethodsApi, salesApi } from '@/lib/api/sales';

vi.mock('@/lib/api/sales', () => ({
  salesApi: { list: vi.fn(), resume: vi.fn() },
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
  return render(<HeldSalesPage />);
}

describe('HeldSalesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(salesApi.list).mockResolvedValue({
      content: [{
        id: 'h1',
        receiptNumber: 'R-2026-000009',
        status: 'HELD',
        grandTotal: 2.2,
        createdAt: '2026-08-31T00:00:00Z',
        customerName: null,
        cashierName: 'C User',
      }],
    });
    vi.mocked(paymentMethodsApi.list).mockResolvedValue([
      { id: 'cash-1', code: 'CASH', name: 'Cash', type: 'CASH', active: true },
    ]);
    vi.mocked(salesApi.resume).mockResolvedValue({
      id: 'h1',
      receiptNumber: 'R-2026-000009',
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

  it('lists held sales and resumes using API totals', async () => {
    renderWithAuth(['SALE_CREATE']);

    await waitFor(() => {
      expect(salesApi.list).toHaveBeenCalledWith({ status: 'HELD' });
      expect(screen.getByText('R-2026-000009')).toBeTruthy();
    });

    fireEvent.click(screen.getByText('Resume'));
    fireEvent.change(screen.getByLabelText('Register session'), { target: { value: 'sess-1' } });
    fireEvent.click(screen.getByText('Complete held sale'));

    await waitFor(() => {
      expect(salesApi.resume).toHaveBeenCalled();
      expect(screen.getByText('Total: 2.2')).toBeTruthy();
    });
  });

  it('hides held sales without SALE_CREATE', () => {
    renderWithAuth(['SALE_READ']);
    expect(screen.getByText('Access is restricted. You do not have permission to resume sales.')).toBeTruthy();
  });
});
