import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react';
import StockReceivingPage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { inventoryApi } from '@/lib/api/inventory';
import { getProducts } from '@/lib/api/catalog';

vi.mock('@/lib/api/inventory', () => ({
  inventoryApi: {
    receiveStock: vi.fn(),
  },
}));

vi.mock('@/lib/api/catalog', () => ({
  getProducts: vi.fn(),
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    back: vi.fn(),
  }),
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

  return render(<StockReceivingPage />);
}

describe('StockReceivingPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getProducts).mockResolvedValue([
      { id: '1', name: 'Apple', sku: 'APL' } as never,
    ]);
  });

  afterEach(() => {
    cleanup();
  });

  it('renders the receiving form when the user can receive', async () => {
    renderWithAuth(['INVENTORY_RECEIVE', 'PRODUCT_READ']);

    expect(screen.getByText('Receive Stock')).toBeTruthy();

    await waitFor(() => {
      expect(screen.getByText('Apple (APL)')).toBeTruthy();
    });

    expect(screen.getByText('Review Receipt')).toBeTruthy();
  });

  it('hides the form without INVENTORY_RECEIVE', () => {
    renderWithAuth(['INVENTORY_READ']);

    expect(screen.getByText('Access is restricted. You do not have permission to receive stock.')).toBeTruthy();
    expect(screen.queryByText('Review Receipt')).toBeNull();
    expect(getProducts).not.toHaveBeenCalled();
  });

  it('submits the confirmed receipt to the API and shows the resulting quantity', async () => {
    vi.mocked(inventoryApi.receiveStock).mockResolvedValue({
      productId: '1',
      productName: 'Apple',
      sku: 'APL',
      storeId: 'store-1',
      storeName: 'Main',
      quantity: 42,
      lastUpdatedAt: '2026-08-30T10:00:00Z',
    });

    renderWithAuth(['INVENTORY_RECEIVE']);

    await waitFor(() => {
      expect(screen.getByText('Apple (APL)')).toBeTruthy();
    });

    fireEvent.change(screen.getByLabelText('Product'), { target: { value: '1' } });
    fireEvent.change(screen.getByLabelText('Quantity'), { target: { value: '7' } });
    fireEvent.click(screen.getByText('Review Receipt'));

    expect(screen.getByText(/Confirm receipt of 7 of Apple \(APL\)/)).toBeTruthy();

    fireEvent.click(screen.getByText('Confirm Receipt'));

    await waitFor(() => {
      expect(inventoryApi.receiveStock).toHaveBeenCalledWith({
        storeId: 'store-1',
        productId: '1',
        quantity: 7,
      });
      expect(screen.getByText('Receipt confirmed. On-hand quantity is now 42.')).toBeTruthy();
    });
  });

  it('collects lot and expiry when the product tracks them', async () => {
    vi.mocked(getProducts).mockResolvedValue([
      { id: '2', name: 'Milk', sku: 'MLK', trackBatch: true, trackExpiry: true } as never,
    ]);
    vi.mocked(inventoryApi.receiveStock).mockResolvedValue({
      productId: '2',
      productName: 'Milk',
      sku: 'MLK',
      storeId: 'store-1',
      storeName: 'Main',
      quantity: 3,
      lastUpdatedAt: '2026-08-30T10:00:00Z',
    });

    renderWithAuth(['INVENTORY_RECEIVE']);

    await waitFor(() => {
      expect(screen.getByText('Milk (MLK)')).toBeTruthy();
    });

    fireEvent.change(screen.getByLabelText('Product'), { target: { value: '2' } });
    fireEvent.change(screen.getByLabelText('Quantity'), { target: { value: '3' } });
    fireEvent.change(screen.getByLabelText('Batch / lot'), { target: { value: 'LOT-9' } });
    fireEvent.change(screen.getByLabelText('Expiration date'), { target: { value: '2026-12-31' } });
    fireEvent.click(screen.getByText('Review Receipt'));
    fireEvent.click(screen.getByText('Confirm Receipt'));

    await waitFor(() => {
      expect(inventoryApi.receiveStock).toHaveBeenCalledWith({
        storeId: 'store-1',
        productId: '2',
        quantity: 3,
        batchNumber: 'LOT-9',
        expirationDate: '2026-12-31',
      });
    });
  });
});
