import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react';
import StockAlertsPage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { inventoryApi } from '@/lib/api/inventory';

vi.mock('@/lib/api/inventory', () => ({
  inventoryApi: {
    getAlerts: vi.fn(),
    acknowledgeAlert: vi.fn(),
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

  return render(<StockAlertsPage />);
}

const openAlert = {
  id: 'a1',
  storeId: 'store-1',
  storeName: 'Main',
  productId: 'p1',
  productName: 'Milk',
  sku: 'MLK',
  batchId: null,
  batchNumber: null,
  alertType: 'LOW_STOCK' as const,
  quantity: 2,
  minimumLevel: 5,
  expirationDate: null,
  status: 'OPEN' as const,
  suggestedAction: 'Reorder',
  daysRemaining: null,
  createdAt: '2026-08-30T10:00:00Z',
  acknowledgedAt: null,
};

describe('StockAlertsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(inventoryApi.getAlerts).mockResolvedValue({
      content: [openAlert],
      totalElements: 1,
      totalPages: 1,
      size: 50,
      number: 0,
    });
    vi.mocked(inventoryApi.acknowledgeAlert).mockResolvedValue({
      ...openAlert,
      status: 'ACKNOWLEDGED',
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('renders quantity, minimum, suggested action, and labelled status', async () => {
    renderWithAuth(['INVENTORY_READ']);

    expect(screen.getByText('Stock Alerts')).toBeTruthy();

    await waitFor(() => {
      expect(inventoryApi.getAlerts).toHaveBeenCalledWith('store-1', 0, 50, undefined, undefined, 7);
      expect(screen.getByText('Milk (MLK)')).toBeTruthy();
      expect(screen.getByText('2')).toBeTruthy();
      expect(screen.getByText('5')).toBeTruthy();
      expect(screen.getByText('Reorder')).toBeTruthy();
      expect(screen.getByRole('status').textContent).toBe('Open');
    });
  });

  it('hides the table without INVENTORY_READ', () => {
    renderWithAuth(['REPORT_INVENTORY']);

    expect(screen.getByText('Access is restricted. You do not have permission to view stock alerts.')).toBeTruthy();
    expect(inventoryApi.getAlerts).not.toHaveBeenCalled();
  });

  it('acknowledges an open alert', async () => {
    renderWithAuth(['INVENTORY_READ']);

    await waitFor(() => {
      expect(screen.getByText('Acknowledge')).toBeTruthy();
    });

    fireEvent.click(screen.getByText('Acknowledge'));

    await waitFor(() => {
      expect(inventoryApi.acknowledgeAlert).toHaveBeenCalledWith('a1');
    });
  });
});
