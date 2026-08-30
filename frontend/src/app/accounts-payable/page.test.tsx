import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import AccountsPayablePage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { invoicesApi } from '@/lib/api/accounts-payable';

vi.mock('@/lib/api/accounts-payable', () => ({
  invoicesApi: { list: vi.fn() },
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
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
  return render(<AccountsPayablePage />);
}

describe('AccountsPayablePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(invoicesApi.list).mockResolvedValue({
      content: [{
        id: 'i1',
        invoiceNumber: 'INV-100',
        supplierId: 's1',
        supplierName: 'Acme',
        invoiceDate: '2026-08-01',
        dueDate: '2026-08-31',
        totalAmount: 100,
        paidAmount: 0,
        remainingAmount: 100,
        status: 'OPEN',
        notes: null,
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

  it('renders invoice list without payments', async () => {
    renderWithAuth(['AP_READ', 'AP_WRITE']);
    expect(screen.getByText('Create Invoice')).toBeTruthy();
    await waitFor(() => {
      expect(invoicesApi.list).toHaveBeenCalled();
      expect(screen.getByText('INV-100')).toBeTruthy();
      expect(screen.getByText('100')).toBeTruthy();
    });
    expect(screen.queryByText(/record payment/i)).toBeNull();
    expect(screen.queryByText(/statement/i)).toBeNull();
  });

  it('hides the table without AP_READ', () => {
    renderWithAuth(['SUPPLIER_READ']);
    expect(screen.getByText('Access is restricted. You do not have permission to view invoices.')).toBeTruthy();
    expect(invoicesApi.list).not.toHaveBeenCalled();
  });
});
