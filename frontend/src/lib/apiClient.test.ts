import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { apiClient, getToken, getRefreshToken, setTokens } from './apiClient';

describe('apiClient', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('adds Authorization header when requiresAuth is true and token exists', async () => {
    setTokens('access-123', 'refresh-123');
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({}), { status: 200 }));

    await apiClient('/test');

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/test'),
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: 'Bearer access-123',
        }),
      })
    );
  });

  it('does not add Authorization header when requiresAuth is false', async () => {
    setTokens('access-123', 'refresh-123');
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({}), { status: 200 }));

    await apiClient('/test', { requiresAuth: false });

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/test'),
      expect.not.objectContaining({
        headers: expect.objectContaining({
          Authorization: 'Bearer access-123',
        }),
      })
    );
  });

  it('automatically refreshes token on 401', async () => {
    setTokens('access-old', 'refresh-123');
    
    // First call returns 401
    vi.mocked(fetch).mockResolvedValueOnce(new Response(null, { status: 401 }));
    // Refresh call returns 200 with new tokens
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ data: { accessToken: 'access-new', refreshToken: 'refresh-new' } }), { status: 200 })
    );
    // Retry call returns 200
    vi.mocked(fetch).mockResolvedValueOnce(new Response(null, { status: 200 }));

    const res = await apiClient('/protected');

    expect(res.status).toBe(200);
    expect(getToken()).toBe('access-new');
    expect(getRefreshToken()).toBe('refresh-new');
    
    // fetch was called 3 times: initial, refresh, retry
    expect(fetch).toHaveBeenCalledTimes(3);
    
    // Verify the retry used the new token
    expect(fetch).toHaveBeenNthCalledWith(
      3,
      expect.stringContaining('/protected'),
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: 'Bearer access-new',
        }),
      })
    );
  });

  it('logs out if refresh token is rejected', async () => {
    setTokens('access-old', 'refresh-old');
    
    let logoutEventFired = false;
    window.addEventListener('auth:logout', () => { logoutEventFired = true; });

    vi.mocked(fetch).mockResolvedValueOnce(new Response(null, { status: 401 }));
    // Refresh fails
    vi.mocked(fetch).mockResolvedValueOnce(new Response(null, { status: 401 }));

    const res = await apiClient('/protected');

    // It returns the original 401 response if refresh fails completely
    expect(res.status).toBe(401);
    expect(getToken()).toBeNull();
    expect(getRefreshToken()).toBeNull();
    expect(logoutEventFired).toBe(true);
  });
});