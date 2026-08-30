import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup, fireEvent } from '@testing-library/react';
import SupplierProfilePage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { suppliersApi } from '@/lib/api/suppliers';
import { getProducts } from '@/lib/api/catalog';

vi.mock('@/lib/api/suppliers', () => ({
  suppliersApi: {
    get: vi.fn(),
    update: vi.fn(),
    listProducts: vi.fn(),
    replaceProducts: vi.fn(),
  },
}));

vi.mock('@/lib/api/catalog', () => ({
  getProducts: vi.fn(),
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useParams: () => ({ id: 's1' }),
}));

function renderWithAuth(permissions: string[]) {
  vi.spyOn(AuthContext, 'useAuth').mockReturnValue({
    user: {
      id: '1',
      username: 'mgr',
      email: 'm@test.com',
      firstName: 'M',
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
  return render(<SupplierProfilePage />);
}

describe('SupplierProfilePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(suppliersApi.get).mockResolvedValue({
      id: 's1',
      supplierCode: 'S-100',
      name: 'Acme Supply',
      phone: '555-0200',
      email: null,
      address: 'Karachi',
      active: true,
      createdAt: '2026-08-31T00:00:00Z',
      updatedAt: '2026-08-31T00:00:00Z',
    });
    vi.mocked(suppliersApi.listProducts).mockResolvedValue([{
      id: 'a1',
      productId: 'p1',
      sku: 'SKU-A',
      name: 'Alpha',
      active: true,
    }]);
    vi.mocked(getProducts).mockResolvedValue([{
      id: 'p1',
      sku: 'SKU-A',
      name: 'Alpha',
      description: null,
      categoryId: null,
      brandId: null,
      unitId: null,
      purchasePrice: 10,
      sellingPrice: 20,
      wholesalePrice: null,
      taxRate: 0,
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

  it('loads contact details, associated products, and no payables', async () => {
    renderWithAuth(['SUPPLIER_READ', 'SUPPLIER_WRITE']);

    await waitFor(() => {
      expect(suppliersApi.get).toHaveBeenCalledWith('s1');
      expect(suppliersApi.listProducts).toHaveBeenCalledWith('s1');
      expect(screen.getByDisplayValue('Acme Supply')).toBeTruthy();
      expect(screen.getByDisplayValue('555-0200')).toBeTruthy();
      expect(screen.getByDisplayValue('Karachi')).toBeTruthy();
      expect(screen.getByText('Associated products')).toBeTruthy();
      expect(screen.getByText('SKU-A')).toBeTruthy();
    });

    expect(getProducts).not.toHaveBeenCalled();
    expect(screen.queryByText('Save associated products')).toBeNull();
    expect(screen.queryByText('Statement')).toBeNull();
    expect(screen.queryByText(/outstanding/i)).toBeNull();
    expect(screen.getByText('Save')).toBeTruthy();
  });

  it('shows the statement action with AP_READ', async () => {
    renderWithAuth(['SUPPLIER_READ', 'AP_READ']);
    await waitFor(() => {
      expect(screen.getByText('Statement')).toBeTruthy();
    });
  });

  it('unlinks a product without PRODUCT_READ', async () => {
    vi.mocked(suppliersApi.replaceProducts).mockResolvedValue([]);
    renderWithAuth(['SUPPLIER_READ', 'SUPPLIER_WRITE']);

    await waitFor(() => {
      expect(screen.getByText('SKU-A')).toBeTruthy();
    });
    fireEvent.click(screen.getByText('Remove'));

    await waitFor(() => {
      expect(suppliersApi.replaceProducts).toHaveBeenCalledWith('s1', []);
    });
  });

  it('shows the catalog picker when PRODUCT_READ is present', async () => {
    renderWithAuth(['SUPPLIER_READ', 'SUPPLIER_WRITE', 'PRODUCT_READ']);

    await waitFor(() => {
      expect(getProducts).toHaveBeenCalled();
      expect(screen.getByText('Save associated products')).toBeTruthy();
    });
  });

  it('hides save without SUPPLIER_WRITE', async () => {
    renderWithAuth(['SUPPLIER_READ']);

    await waitFor(() => {
      expect(screen.getByDisplayValue('Acme Supply')).toBeTruthy();
    });
    expect(screen.queryByText('Save')).toBeNull();
  });
});
