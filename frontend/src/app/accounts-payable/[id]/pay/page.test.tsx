import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup, fireEvent } from '@testing-library/react';
import SupplierPaymentPage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { invoicesApi, paymentsApi } from '@/lib/api/accounts-payable';

const push = vi.fn();

vi.mock('@/lib/api/accounts-payable', () => ({
  invoicesApi: { get: vi.fn() },
  paymentsApi: { create: vi.fn() },
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push }),
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
  return render(<SupplierPaymentPage />);
}

describe('SupplierPaymentPage', () => {
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

  it('confirms a payment against the open invoice', async () => {
    vi.mocked(paymentsApi.create).mockResolvedValue({
      id: 'p1',
      invoiceId: 'i1',
      invoiceNumber: 'INV-100',
      amount: 100,
      paymentDate: '2026-08-31',
      method: 'CASH',
      reference: null,
      createdAt: '2026-08-31T00:00:00Z',
    });
    renderWithAuth(['AP_PAYMENT_CREATE']);

    await waitFor(() => {
      expect(invoicesApi.get).toHaveBeenCalledWith('i1');
      expect(screen.getByText(/outstanding 100/i)).toBeTruthy();
    });

    fireEvent.change(screen.getByLabelText('Method'), { target: { value: 'CASH' } });
    fireEvent.click(screen.getByText('Confirm payment'));

    await waitFor(() => {
      expect(paymentsApi.create).toHaveBeenCalled();
      expect(push).toHaveBeenCalledWith('/accounts-payable/i1');
    });
  });

  it('hides the form without AP_PAYMENT_CREATE', () => {
    renderWithAuth(['AP_READ']);
    expect(screen.getByText('Access is restricted. You do not have permission to record payments.')).toBeTruthy();
    expect(invoicesApi.get).not.toHaveBeenCalled();
  });
});
