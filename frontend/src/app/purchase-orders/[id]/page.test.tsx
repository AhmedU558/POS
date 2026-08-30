import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import PurchaseOrderDetailPage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { purchaseOrdersApi } from '@/lib/api/purchase-orders';

vi.mock('@/lib/api/purchase-orders', () => ({
  purchaseOrdersApi: {
    get: vi.fn(),
    update: vi.fn(),
    submit: vi.fn(),
    cancel: vi.fn(),
  },
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
  return render(<PurchaseOrderDetailPage />);
}

describe('PurchaseOrderDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(purchaseOrdersApi.get).mockResolvedValue({
      id: 'po1',
      poNumber: 'PO-100',
      supplierId: 's1',
      supplierName: 'Acme Supply',
      status: 'DRAFT',
      notes: null,
      items: [{
        id: 'i1',
        productId: 'p1',
        sku: 'SKU-A',
        name: 'Alpha',
        quantity: 2,
      }],
      createdAt: '2026-08-31T00:00:00Z',
      updatedAt: '2026-08-31T00:00:00Z',
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('loads draft lines and actions without invoices', async () => {
    renderWithAuth(['PURCHASE_READ', 'PURCHASE_WRITE', 'PURCHASE_APPROVE']);

    await waitFor(() => {
      expect(purchaseOrdersApi.get).toHaveBeenCalledWith('po1');
      expect(screen.getByDisplayValue('PO-100')).toBeTruthy();
      expect(screen.getByText('SKU-A')).toBeTruthy();
      expect(screen.getByText('Submit')).toBeTruthy();
      expect(screen.getByText('Cancel order')).toBeTruthy();
    });

    expect(screen.queryByText(/invoice/i)).toBeNull();
    expect(screen.queryByText(/goods receipt/i)).toBeNull();
  });

  it('hides draft actions without write or approve', async () => {
    renderWithAuth(['PURCHASE_READ']);

    await waitFor(() => {
      expect(screen.getByDisplayValue('PO-100')).toBeTruthy();
    });
    expect(screen.queryByText('Save')).toBeNull();
    expect(screen.queryByText('Submit')).toBeNull();
  });
});
