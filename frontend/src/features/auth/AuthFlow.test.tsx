import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AuthProvider } from './AuthContext';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute';
import LoginPage from '@/app/login/page';
import ForcedRotationPage from '@/app/forced-rotation/page';

// Mock useRouter
const mockReplace = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace }),
  usePathname: () => '/'
}));

describe('Authentication Flow', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    localStorage.clear();
    mockReplace.mockClear();
  });

  afterEach(() => {
    vi.restoreAllMocks(); cleanup();
  });

  it('redirects to /login when unauthenticated', async () => {
    render(
      <AuthProvider>
        <ProtectedRoute>
          <div data-testid="protected-content">Secret</div>
        </ProtectedRoute>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith('/login');
      expect(screen.queryByTestId('protected-content')).toBeNull();
    });
  });

  it('renders login page and logs in successfully', async () => {
    render(
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    );

    // Mock successful login
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({
        data: { accessToken: 'acc1', refreshToken: 'ref1', passwordChangeRequired: false }
      }), { status: 200 })
    );

    // Mock /auth/me
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({
        data: { username: 'testuser' }
      }), { status: 200 })
    );

    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/Username/i), 'testuser');
    await user.type(screen.getByLabelText('Password'), 'pass123');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => {
      expect(localStorage.getItem('accessToken')).toBe('acc1');
      expect(fetch).toHaveBeenCalledTimes(2); // /login and /me
    });
  });

  it('handles forced password rotation', async () => {
    render(
      <AuthProvider>
        <ForcedRotationPage />
      </AuthProvider>
    );

    // Initial state: user sees the rotation screen
    expect(screen.getByText(/Choose a new password/i)).not.toBeNull();

    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Current password'), 'oldPass');
    await user.type(screen.getByLabelText('New password'), 'newPass');
    await user.type(screen.getByLabelText('Confirm new password'), 'newPass');

    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(null, { status: 204 })
    );

    // Mock the subsequent /auth/me call inside refreshAuth()
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ data: { username: 'test' } }), { status: 200 })
    );

    await user.click(screen.getByRole('button', { name: 'Set new password' }));

    await waitFor(() => {
      // It should call /change-password, then /me
      expect(fetch).toHaveBeenCalledWith(
        expect.stringContaining('/auth/change-password'),
        expect.anything()
      );
      expect(fetch).toHaveBeenCalledWith(
        expect.stringContaining('/auth/me'),
        expect.anything()
      );
    });
  });
});
