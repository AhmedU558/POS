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

export interface RegisterSessionSummary {
  id: string;
  openingCash: number;
  cashInTotal: number;
  cashOutTotal: number;
  cashSalesTotal: number;
  expectedCash: number;
  status: string;
}

export interface CashMovement {
  id: string;
  registerSessionId: string;
  transactionType: string;
  amount: number;
  reason: string | null;
  createdAt: string;
}

export interface RegisterClosingReport {
  sessionId: string;
  zReportNumber: string;
  status: string;
  openingCash: number;
  expectedCash: number;
  actualCash: number;
  variance: number;
  notes: string | null;
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

  summary: async (id: string) => {
    const res = await apiClient('/register-sessions/' + id + '/summary', { method: 'GET' });
    return handleResponse<RegisterSessionSummary>(res);
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

  close: async (id: string, actualCash: number, notes?: string) => {
    const res = await apiClient('/register-sessions/' + id + '/close', {
      method: 'POST',
      body: JSON.stringify({ actualCash, notes: notes || null }),
    });
    return handleResponse<RegisterClosingReport>(res);
  },
};
