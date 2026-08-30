import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import InvoiceDetailPage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { invoicesApi } from '@/lib/api/accounts-payable';

vi.mock('@/lib/api/accounts-payable', () => ({
  invoicesApi: { get: vi.fn(), update: vi.fn() },
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useParams: () => ({ id: 'i1' }),
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
  return render(<InvoiceDetailPage />);
}

describe('InvoiceDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(invoicesApi.get).mockResolvedValue({
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
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('loads open invoice fields without payment actions', async () => {
    renderWithAuth(['AP_READ', 'AP_WRITE']);
    await waitFor(() => {
      expect(invoicesApi.get).toHaveBeenCalledWith('i1');
      expect(screen.getByDisplayValue('INV-100')).toBeTruthy();
      expect(screen.getByText(/Outstanding: 100/)).toBeTruthy();
      expect(screen.getByText('Save')).toBeTruthy();
    });
    expect(screen.queryByText(/record payment/i)).toBeNull();
  });
});
