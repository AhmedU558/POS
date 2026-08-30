import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import StoreCreditPage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { customersApi } from '@/lib/api/customers';

vi.mock('@/lib/api/customers', () => ({
  customersApi: {
    getCredit: vi.fn(),
    postCredit: vi.fn(),
  },
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useParams: () => ({ id: 'c1' }),
}));

function renderWithAuth(permissions: string[]) {
  vi.spyOn(AuthContext, 'useAuth').mockReturnValue({
    user: {
      id: '1',
      username: 'cashier',
      email: 'c@test.com',
      firstName: 'C',
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
  return render(<StoreCreditPage />);
}

describe('StoreCreditPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(customersApi.getCredit).mockResolvedValue({
      customerId: 'c1',
      customerCode: 'C-100',
      name: 'Ada Lovelace',
      creditLimit: 25,
      balance: 30,
      currencyCode: 'USD',
      status: 'ACTIVE',
      transactions: {
        content: [{
          id: 't1',
          transactionType: 'ISSUE',
          amount: 30,
          referenceType: null,
          referenceId: null,
          balanceAfter: 30,
          createdAt: '2026-08-31T00:00:00Z',
        }],
        totalElements: 1,
        totalPages: 1,
        size: 50,
        number: 0,
      },
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('renders balance, profile limit, ledger, and post form without statement', async () => {
    renderWithAuth(['CREDIT_READ', 'CREDIT_WRITE']);

    await waitFor(() => {
      expect(customersApi.getCredit).toHaveBeenCalledWith('c1');
      expect(screen.getByText('Ada Lovelace', { exact: false })).toBeTruthy();
      expect(screen.getAllByText('30').length).toBeGreaterThan(0);
      expect(screen.getByText('25')).toBeTruthy();
      expect(screen.getByText('ISSUE')).toBeTruthy();
      expect(screen.getByText('Post transaction')).toBeTruthy();
    });

    expect(screen.getByText('Ledger')).toBeTruthy();
    expect(screen.getByText('Display only — not enforced')).toBeTruthy();
    expect(screen.queryByText(/statement/i)).toBeNull();
    expect(screen.queryByText(/purchase history/i)).toBeNull();
  });

  it('hides the post form without CREDIT_WRITE', async () => {
    renderWithAuth(['CREDIT_READ']);

    await waitFor(() => {
      expect(screen.getByText('Ledger')).toBeTruthy();
    });
    expect(screen.queryByText('Post transaction')).toBeNull();
  });

  it('hides credit without CREDIT_READ', () => {
    renderWithAuth(['CUSTOMER_READ']);

    expect(screen.getByText('Access is restricted. You do not have permission to view store credit.')).toBeTruthy();
    expect(customersApi.getCredit).not.toHaveBeenCalled();
  });
});
