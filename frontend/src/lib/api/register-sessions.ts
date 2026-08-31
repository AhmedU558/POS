import { apiClient } from '../apiClient';

export interface RegisterSession {
  id: string;
  registerId: string;
  storeId: string;
  terminalId: string;
  cashierId: string;
  status: string;
  openingCash: number;
  openedAt: string;
  closedAt: string | null;
}

async function handleResponse<T>(res: Response): Promise<T> {
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(body.error?.message || 'An unexpected error occurred');
  }
  return body.data;
}

export const registerSessionsApi = {
  open: async (registerId: string, openingCash: number) => {
    const res = await apiClient('/registers/' + registerId + '/sessions/open', {
      method: 'POST',
      body: JSON.stringify({ openingCash }),
    });
    return handleResponse<RegisterSession>(res);
  },

  get: async (id: string) => {
    const res = await apiClient('/register-sessions/' + id, { method: 'GET' });
    return handleResponse<RegisterSession>(res);
  },
};
