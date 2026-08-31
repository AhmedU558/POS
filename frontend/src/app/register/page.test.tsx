import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup, fireEvent } from '@testing-library/react';
import RegisterOpenPage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { registerSessionsApi } from '@/lib/api/register-sessions';

vi.mock('@/lib/api/register-sessions', () => ({
  registerSessionsApi: { open: vi.fn(), get: vi.fn(), summary: vi.fn(), cashIn: vi.fn(), cashOut: vi.fn() },
}));

function renderWithAuth(permissions: string[]) {
  vi.spyOn(AuthContext, 'useAuth').mockReturnValue({
    user: {
      id: '1',
      username: 'cash',
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
  return render(<RegisterOpenPage />);
}

describe('RegisterOpenPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(registerSessionsApi.open).mockResolvedValue({
      id: 'sess-1',
      registerId: 'reg-1',
      storeId: 'store-1',
      terminalId: 't1',
      cashierId: '1',
      status: 'OPEN',
      openingCash: 150,
      openedAt: '2026-08-31T00:00:00Z',
      closedAt: null,
    });
    vi.mocked(registerSessionsApi.summary).mockResolvedValue({
      id: 'sess-1',
      openingCash: 150,
      cashInTotal: 20,
      cashOutTotal: 0,
      cashSalesTotal: 0,
      expectedCash: 170,
      status: 'OPEN',
    });
    vi.mocked(registerSessionsApi.cashIn).mockResolvedValue({
      id: 'm1',
      registerSessionId: 'sess-1',
      transactionType: 'CASH_IN',
      amount: 20,
      reason: 'Float',
      createdAt: '2026-08-31T00:00:00Z',
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('opens a session and records cash-in from the API', async () => {
    renderWithAuth(['REGISTER_OPEN', 'REGISTER_CASH']);
    fireEvent.change(screen.getByLabelText('Register'), { target: { value: 'reg-1' } });
    fireEvent.change(screen.getByLabelText('Opening cash'), { target: { value: '150' } });
    fireEvent.click(screen.getByText('Open register'));

    await waitFor(() => {
      expect(registerSessionsApi.open).toHaveBeenCalledWith('reg-1', 150);
      expect(screen.getByText('Session sess-1')).toBeTruthy();
      expect(screen.getByText('Opening cash: 150')).toBeTruthy();
      expect(screen.getByText('Status: OPEN')).toBeTruthy();
      expect(screen.getByText('Expected cash: 170')).toBeTruthy();
    });

    fireEvent.change(screen.getByLabelText('Cash amount'), { target: { value: '20' } });
    fireEvent.click(screen.getByText('Cash in'));
    await waitFor(() => {
      expect(registerSessionsApi.cashIn).toHaveBeenCalledWith('sess-1', 20, '');
      expect(screen.getByText('CASH_IN: 20')).toBeTruthy();
    });
  });

  it('hides open without REGISTER_OPEN', () => {
    renderWithAuth(['SALE_CREATE']);
    expect(screen.getByText('Access is restricted. You do not have permission to open a register.')).toBeTruthy();
  });
});
