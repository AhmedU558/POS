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

export interface CashMovement {
  id: string;
  registerSessionId: string;
  transactionType: string;
  amount: number;
  reason: string | null;
  createdAt: string;
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

  cashIn: async (id: string, amount: number, reason?: string) => {
    const res = await apiClient('/register-sessions/' + id + '/cash-in', {
      method: 'POST',
      body: JSON.stringify({ amount, reason: reason || null }),
    });
    return handleResponse<CashMovement>(res);
  },

  cashOut: async (id: string, amount: number, reason?: string) => {
    const res = await apiClient('/register-sessions/' + id + '/cash-out', {
      method: 'POST',
      body: JSON.stringify({ amount, reason: reason || null }),
    });
    return handleResponse<CashMovement>(res);
  },
};
