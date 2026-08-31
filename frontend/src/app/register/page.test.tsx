import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup, fireEvent } from '@testing-library/react';
import RegisterOpenPage from './page';
import * as AuthContext from '@/features/auth/AuthContext';
import { registerSessionsApi } from '@/lib/api/register-sessions';

vi.mock('@/lib/api/register-sessions', () => ({
  registerSessionsApi: { open: vi.fn(), get: vi.fn() },
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
  });

  afterEach(() => {
    cleanup();
  });

  it('opens a session and displays API opening cash', async () => {
    renderWithAuth(['REGISTER_OPEN']);
    fireEvent.change(screen.getByLabelText('Register'), { target: { value: 'reg-1' } });
    fireEvent.change(screen.getByLabelText('Opening cash'), { target: { value: '150' } });
    fireEvent.click(screen.getByText('Open register'));

    await waitFor(() => {
      expect(registerSessionsApi.open).toHaveBeenCalledWith('reg-1', 150);
      expect(screen.getByText('Session sess-1')).toBeTruthy();
      expect(screen.getByText('Opening cash: 150')).toBeTruthy();
      expect(screen.getByText('Status: OPEN')).toBeTruthy();
    });
  });

  it('hides open without REGISTER_OPEN', () => {
    renderWithAuth(['SALE_CREATE']);
    expect(screen.getByText('Access is restricted. You do not have permission to open a register.')).toBeTruthy();
  });
});
