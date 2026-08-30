import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import CustomerProfilePage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { customersApi } from '@/lib/api/customers';

vi.mock('@/lib/api/customers', () => ({
  customersApi: {
    get: vi.fn(),
    update: vi.fn(),
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
  return render(<CustomerProfilePage />);
}

describe('CustomerProfilePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(customersApi.get).mockResolvedValue({
      id: 'c1',
      customerCode: 'C-100',
      name: 'Ada Lovelace',
      phone: '555-0100',
      email: null,
      address: 'London',
      creditLimit: 25,
      active: true,
      createdAt: '2026-08-31T00:00:00Z',
      updatedAt: '2026-08-31T00:00:00Z',
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('loads contact details and status without sales or credit sections', async () => {
    renderWithAuth(['CUSTOMER_READ', 'CUSTOMER_WRITE']);

    await waitFor(() => {
      expect(customersApi.get).toHaveBeenCalledWith('c1');
      expect(screen.getByDisplayValue('Ada Lovelace')).toBeTruthy();
      expect(screen.getByDisplayValue('555-0100')).toBeTruthy();
      expect(screen.getByDisplayValue('London')).toBeTruthy();
    });

    expect(screen.queryByText(/purchase history/i)).toBeNull();
    expect(screen.queryByText(/statement/i)).toBeNull();
    expect(screen.queryByText(/store credit/i)).toBeNull();
    expect(screen.getByText('Save')).toBeTruthy();
  });

  it('hides save without CUSTOMER_WRITE', async () => {
    renderWithAuth(['CUSTOMER_READ']);

    await waitFor(() => {
      expect(screen.getByDisplayValue('Ada Lovelace')).toBeTruthy();
    });
    expect(screen.queryByText('Save')).toBeNull();
  });
});
