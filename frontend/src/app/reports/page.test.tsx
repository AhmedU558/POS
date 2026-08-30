import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react';
import InventoryReportsPage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { inventoryApi } from '@/lib/api/inventory';

vi.mock('@/lib/api/inventory', () => ({
  inventoryApi: {
    getInventoryReport: vi.fn(),
    getMovementReport: vi.fn(),
    getExpiryReport: vi.fn(),
  },
}));

function renderWithAuth(permissions: string[]) {
  vi.spyOn(AuthContext, 'useAuth').mockReturnValue({
    user: {
      id: '1',
      username: 'mgr',
      email: 'mgr@test.com',
      firstName: 'Store',
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

  return render(<InventoryReportsPage />);
}

describe('InventoryReportsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(inventoryApi.getInventoryReport).mockResolvedValue({
      content: [{
        productId: 'p1',
        productName: 'Milk',
        sku: 'MLK',
        storeId: 'store-1',
        storeName: 'Main',
        quantity: 2,
        minStock: 5,
        belowMinimum: true,
        lastUpdatedAt: '2026-08-30T10:00:00Z',
      }],
      totalElements: 1,
      totalPages: 1,
      size: 50,
      number: 0,
    });
    vi.mocked(inventoryApi.getMovementReport).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 50,
      number: 0,
    });
    vi.mocked(inventoryApi.getExpiryReport).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 50,
      number: 0,
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('renders the inventory report for authorized users', async () => {
    renderWithAuth(['REPORT_INVENTORY']);

    expect(screen.getByText('Inventory Reports')).toBeTruthy();

    await waitFor(() => {
      expect(inventoryApi.getInventoryReport).toHaveBeenCalledWith('store-1', 0, 50, false);
      expect(screen.getByText('Milk (MLK)')).toBeTruthy();
      expect(screen.getByText('Yes')).toBeTruthy();
    });
  });

  it('hides reports without REPORT_INVENTORY', () => {
    renderWithAuth(['INVENTORY_READ']);

    expect(screen.getByText('Access is restricted. You do not have permission to view inventory reports.')).toBeTruthy();
    expect(inventoryApi.getInventoryReport).not.toHaveBeenCalled();
  });

  it('requests the expiry report when selected', async () => {
    renderWithAuth(['REPORT_INVENTORY']);

    await waitFor(() => {
      expect(inventoryApi.getInventoryReport).toHaveBeenCalled();
    });

    fireEvent.change(screen.getByLabelText('Report'), { target: { value: 'expiry' } });

    await waitFor(() => {
      expect(inventoryApi.getExpiryReport).toHaveBeenCalledWith('store-1', 0, 50, 7);
    });
  });
});
