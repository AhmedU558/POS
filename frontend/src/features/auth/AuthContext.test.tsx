import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import { AuthProvider, useAuth } from './AuthContext';
import { setTokens } from '@/lib/apiClient';

const TestComponent = () => {
  const { isAuthenticated, isLoading, user, passwordChangeRequired, logout } = useAuth();

  if (isLoading) return <div>Loading...</div>;

  return (
    <div>
      <div data-testid="auth-status">{isAuthenticated ? 'Auth' : 'Unauth'}</div>
      <div data-testid="pwd-status">{passwordChangeRequired ? 'PwdReq' : 'NoPwdReq'}</div>
      {user && <div data-testid="user-name">{user.username}</div>}
      <button onClick={logout}>LogoutBtn</button>
    </div>
  );
};

describe('AuthContext', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks(); cleanup();
  });

  it('starts unauthenticated if no token', async () => {
    render(<AuthProvider><TestComponent /></AuthProvider>);
    
    
    
    await waitFor(() => {
      expect(screen.getByTestId('auth-status').textContent).toBe('Unauth');
    });
  });

  it('fetches /auth/me on mount if token exists', async () => {
    setTokens('access', 'refresh');
    
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ data: { username: 'testuser' } }), { status: 200 })
    );

    render(<AuthProvider><TestComponent /></AuthProvider>);
    
    await waitFor(() => {
      expect(screen.getByTestId('auth-status').textContent).toBe('Auth');
      expect(screen.getByTestId('user-name').textContent).toBe('testuser');
    });
    
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/auth/me'),
      expect.anything()
    );
  });

  it('handles 403 PASSWORD_CHANGE_REQUIRED correctly on mount', async () => {
    setTokens('access', 'refresh');
    
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ error: { code: 'PASSWORD_CHANGE_REQUIRED' } }), { status: 403 })
    );

    render(<AuthProvider><TestComponent /></AuthProvider>);
    
    await waitFor(() => {
      expect(screen.getByTestId('auth-status').textContent).toBe('Auth');
      expect(screen.getByTestId('pwd-status').textContent).toBe('PwdReq');
    });
  });

  it('clears auth if /auth/me fails (unauthorized)', async () => {
    setTokens('access', 'refresh');
    
    vi.mocked(fetch).mockResolvedValueOnce(new Response(null, { status: 401 }));
    // And if refresh also fails
    vi.mocked(fetch).mockResolvedValueOnce(new Response(null, { status: 401 }));

    render(<AuthProvider><TestComponent /></AuthProvider>);
    
    await waitFor(() => {
      expect(screen.getByTestId('auth-status').textContent).toBe('Unauth');
    });
    expect(localStorage.getItem('accessToken')).toBeNull();
  });
});
