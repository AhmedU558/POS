import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import SupplierStatementPage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { suppliersApi } from '@/lib/api/suppliers';

vi.mock('@/lib/api/suppliers', () => ({
  suppliersApi: { get: vi.fn(), statement: vi.fn() },
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useParams: () => ({ id: 's1' }),
}));

function renderWithAuth(permissions: string[]) {
  vi.spyOn(AuthContext, 'useAuth').mockReturnValue({
    user: {
      id: '1',
      username: 'acct',
      email: 'a@test.com',
      firstName: 'A',
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
  return render(<SupplierStatementPage />);
}

describe('SupplierStatementPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(suppliersApi.get).mockResolvedValue({
      id: 's1',
      supplierCode: 'S-100',
      name: 'Acme Supply',
      phone: null,
      email: null,
      address: null,
      active: true,
      createdAt: '2026-08-31T00:00:00Z',
      updatedAt: '2026-08-31T00:00:00Z',
    });
    vi.mocked(suppliersApi.statement).mockResolvedValue({
      content: [{
        type: 'INVOICE',
        date: '2026-08-01',
        invoiceId: 'i1',
        invoiceNumber: 'INV-100',
        paymentId: null,
        debit: 100,
        credit: 0,
        runningBalance: 100,
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

  it('renders statement lines with running balance', async () => {
    renderWithAuth(['AP_READ']);
    await waitFor(() => {
      expect(suppliersApi.statement).toHaveBeenCalledWith('s1');
      expect(screen.getByText('Acme Supply')).toBeTruthy();
      expect(screen.getByText('INV-100')).toBeTruthy();
      expect(screen.getByText('INVOICE')).toBeTruthy();
    });
  });

  it('hides the table without AP_READ', () => {
    renderWithAuth(['SUPPLIER_READ']);
    expect(screen.getByText('Access is restricted. You do not have permission to view statements.')).toBeTruthy();
    expect(suppliersApi.statement).not.toHaveBeenCalled();
  });
});
