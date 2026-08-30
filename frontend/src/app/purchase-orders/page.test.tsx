import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import PurchaseOrdersPage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { purchaseOrdersApi } from '@/lib/api/purchase-orders';

vi.mock('@/lib/api/purchase-orders', () => ({
  purchaseOrdersApi: {
    list: vi.fn(),
  },
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
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
  return render(<PurchaseOrdersPage />);
}

describe('PurchaseOrdersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(purchaseOrdersApi.list).mockResolvedValue({
      content: [{
        id: 'po1',
        poNumber: 'PO-100',
        supplierId: 's1',
        supplierName: 'Acme Supply',
        status: 'DRAFT',
        notes: null,
        items: [],
        createdAt: '2026-08-31T00:00:00Z',
        updatedAt: '2026-08-31T00:00:00Z',
      }],
      totalElements: 1,
      totalPages: 1,
      size: 50,
      number: 0,
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('renders search, status, PO number, and no payables', async () => {
    renderWithAuth(['PURCHASE_READ', 'PURCHASE_WRITE']);

    expect(screen.getByText('Purchase Orders')).toBeTruthy();
    expect(screen.getByText('Create Purchase Order')).toBeTruthy();

    await waitFor(() => {
      expect(purchaseOrdersApi.list).toHaveBeenCalled();
      expect(screen.getByText('PO-100')).toBeTruthy();
      expect(screen.getByText('Acme Supply')).toBeTruthy();
      expect(screen.getByText('DRAFT')).toBeTruthy();
    });

    expect(screen.queryByText(/invoice/i)).toBeNull();
    expect(screen.queryByText(/outstanding/i)).toBeNull();
  });

  it('hides the table without PURCHASE_READ', () => {
    renderWithAuth(['SUPPLIER_READ']);

    expect(screen.getByText('Access is restricted. You do not have permission to view purchase orders.')).toBeTruthy();
    expect(purchaseOrdersApi.list).not.toHaveBeenCalled();
  });
});
