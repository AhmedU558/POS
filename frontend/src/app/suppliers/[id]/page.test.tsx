import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import SupplierProfilePage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { suppliersApi } from '@/lib/api/suppliers';

vi.mock('@/lib/api/suppliers', () => ({
  suppliersApi: {
    get: vi.fn(),
    update: vi.fn(),
  },
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
  });

  afterEach(() => {
    cleanup();
  });

  it('loads contact details and status without products or payables', async () => {
    renderWithAuth(['SUPPLIER_READ', 'SUPPLIER_WRITE']);

    await waitFor(() => {
      expect(suppliersApi.get).toHaveBeenCalledWith('s1');
      expect(screen.getByDisplayValue('Acme Supply')).toBeTruthy();
      expect(screen.getByDisplayValue('555-0200')).toBeTruthy();
      expect(screen.getByDisplayValue('Karachi')).toBeTruthy();
    });

    expect(screen.queryByText(/associated products/i)).toBeNull();
    expect(screen.queryByText(/statement/i)).toBeNull();
    expect(screen.queryByText(/outstanding/i)).toBeNull();
    expect(screen.getByText('Save')).toBeTruthy();
  });

  it('hides save without SUPPLIER_WRITE', async () => {
    renderWithAuth(['SUPPLIER_READ']);

    await waitFor(() => {
      expect(screen.getByDisplayValue('Acme Supply')).toBeTruthy();
    });
    expect(screen.queryByText('Save')).toBeNull();
  });
});
