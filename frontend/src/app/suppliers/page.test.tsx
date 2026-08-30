import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import SuppliersPage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { suppliersApi } from '@/lib/api/suppliers';

vi.mock('@/lib/api/suppliers', () => ({
  suppliersApi: {
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
  return render(<SuppliersPage />);
}

describe('SuppliersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(suppliersApi.list).mockResolvedValue({
      content: [{
        id: 's1',
        supplierCode: 'S-100',
        name: 'Acme Supply',
        phone: '555-0200',
        email: 'ap@acme.test',
        address: null,
        active: true,
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

  it('renders search, status, contact fields, and labelled status', async () => {
    renderWithAuth(['SUPPLIER_READ', 'SUPPLIER_WRITE']);

    expect(screen.getByText('Suppliers')).toBeTruthy();
    expect(screen.getByText('Create Supplier')).toBeTruthy();

    await waitFor(() => {
      expect(suppliersApi.list).toHaveBeenCalled();
      expect(screen.getByText('S-100')).toBeTruthy();
      expect(screen.getByText('Acme Supply')).toBeTruthy();
      expect(screen.getByText('555-0200')).toBeTruthy();
      expect(screen.getAllByText('Active').length).toBeGreaterThan(0);
    });

    expect(screen.queryByText(/outstanding/i)).toBeNull();
    expect(screen.queryByText(/statement/i)).toBeNull();
  });

  it('hides the table without SUPPLIER_READ', () => {
    renderWithAuth(['CUSTOMER_READ']);

    expect(screen.getByText('Access is restricted. You do not have permission to view suppliers.')).toBeTruthy();
    expect(suppliersApi.list).not.toHaveBeenCalled();
  });
});
