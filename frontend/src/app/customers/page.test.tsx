import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import CustomersPage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { customersApi } from '@/lib/api/customers';

vi.mock('@/lib/api/customers', () => ({
  customersApi: {
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
  return render(<CustomersPage />);
}

describe('CustomersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(customersApi.list).mockResolvedValue({
      content: [{
        id: 'c1',
        customerCode: 'C-100',
        name: 'Ada Lovelace',
        phone: '555-0100',
        email: 'ada@example.com',
        address: null,
        creditLimit: 25,
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
    renderWithAuth(['CUSTOMER_READ', 'CUSTOMER_WRITE']);

    expect(screen.getByText('Customers')).toBeTruthy();
    expect(screen.getByText('Create Customer')).toBeTruthy();

    await waitFor(() => {
      expect(customersApi.list).toHaveBeenCalled();
      expect(screen.getByText('C-100')).toBeTruthy();
      expect(screen.getByText('Ada Lovelace')).toBeTruthy();
      expect(screen.getByText('555-0100')).toBeTruthy();
      expect(screen.getByText('25')).toBeTruthy();
      expect(screen.getAllByText('Active').length).toBeGreaterThan(0);
    });
  });

  it('hides the table without CUSTOMER_READ', () => {
    renderWithAuth(['PRODUCT_READ']);

    expect(screen.getByText('Access is restricted. You do not have permission to view customers.')).toBeTruthy();
    expect(customersApi.list).not.toHaveBeenCalled();
  });
});
