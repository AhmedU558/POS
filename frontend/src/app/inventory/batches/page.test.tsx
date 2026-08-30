import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react';
import BatchesExpiryPage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { inventoryApi } from '@/lib/api/inventory';

vi.mock('@/lib/api/inventory', () => ({
  inventoryApi: {
    getBatches: vi.fn(),
    getExpiry: vi.fn(),
  },
}));

function renderWithAuth(permissions: string[]) {
  vi.spyOn(AuthContext, 'useAuth').mockReturnValue({
    user: {
      id: '1',
      username: 'inv',
      email: 'inv@test.com',
      firstName: 'Inv',
      lastName: 'Mgr',
      permissions,
      storeIds: ['store-1'],
    },
    token: 'valid',
    isAuthenticated: true,
    isLoading: false,
    login: vi.fn(),
    logout: vi.fn(),
  } as never);

  return render(<BatchesExpiryPage />);
}

const expiredBatch = {
  id: 'b1',
  productId: 'p1',
  productName: 'Milk',
  sku: 'MLK',
  storeId: 'store-1',
  storeName: 'Main',
  batchNumber: 'LOT-1',
  quantity: 8,
  expirationDate: '2026-08-01',
  manufacturingDate: null,
  status: 'EXPIRED' as const,
  daysRemaining: -29,
};

describe('BatchesExpiryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(inventoryApi.getBatches).mockResolvedValue({
      content: [expiredBatch],
      totalElements: 1,
      totalPages: 1,
      size: 50,
      number: 0,
    });
    vi.mocked(inventoryApi.getExpiry).mockResolvedValue({
      content: [expiredBatch],
      totalElements: 1,
      totalPages: 1,
      size: 50,
      number: 0,
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('renders product, batch, quantity, expiry, store, and labelled status', async () => {
    renderWithAuth(['INVENTORY_READ']);

    expect(screen.getByText('Batches & Expiry')).toBeTruthy();

    await waitFor(() => {
      expect(inventoryApi.getBatches).toHaveBeenCalledWith('store-1', 0, 50, 7);
      expect(screen.getByText('Milk (MLK)')).toBeTruthy();
      expect(screen.getByText('LOT-1')).toBeTruthy();
      expect(screen.getByText('8')).toBeTruthy();
      expect(screen.getByText('2026-08-01')).toBeTruthy();
      expect(screen.getByText('Main')).toBeTruthy();
      expect(screen.getByText('Expired')).toBeTruthy();
    });
  });

  it('hides the table without INVENTORY_READ', () => {
    renderWithAuth(['INVENTORY_RECEIVE']);

    expect(screen.getByText('Access is restricted. You do not have permission to view batches.')).toBeTruthy();
    expect(inventoryApi.getBatches).not.toHaveBeenCalled();
    expect(screen.queryByText('Batch')).toBeNull();
  });

  it('requests the expiry list when the filter and 30-day window are selected', async () => {
    renderWithAuth(['INVENTORY_READ']);

    await waitFor(() => {
      expect(inventoryApi.getBatches).toHaveBeenCalled();
    });

    fireEvent.change(screen.getByLabelText('View'), { target: { value: 'expiry' } });
    fireEvent.change(screen.getByLabelText('Window (days)'), { target: { value: '30' } });

    await waitFor(() => {
      expect(inventoryApi.getExpiry).toHaveBeenCalledWith('store-1', 0, 50, 30);
    });
  });
});
