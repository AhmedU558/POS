import { get, post } from './http';

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
  registerId: string;
  storeId: string;
  terminalId: string;
  cashierId: string;
  status: string;
  openingCash: number;
  cashInTotal: number;
  cashOutTotal: number;
  cashSalesTotal: number;
  expectedCash: number;
  openedAt: string;
  closedAt: string | null;
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
  cashInTotal: number;
  cashOutTotal: number;
  cashSalesTotal: number;
  expectedCash: number;
  actualCash: number;
  variance: number;
  notes: string | null;
  openedAt: string;
  closedAt: string | null;
}

export const registerSessionsApi = {
  /** The caller's open session, or null when no till is open (AMD-043). */
  current: () => get<RegisterSession | null>('/register-sessions/current'),

  open: (registerId: string, openingCash: number) =>
    post<RegisterSession>(`/registers/${registerId}/sessions/open`, { openingCash }),

  get: (id: string) => get<RegisterSession>(`/register-sessions/${id}`),

  summary: (id: string) => get<RegisterSessionSummary>(`/register-sessions/${id}/summary`),

  closingReport: (id: string) => get<RegisterClosingReport>(`/register-sessions/${id}/closing-report`),

  cashIn: (id: string, amount: number, reason?: string) =>
    post<CashMovement>(`/register-sessions/${id}/cash-in`, { amount, reason: reason || null }),

  cashOut: (id: string, amount: number, reason?: string) =>
    post<CashMovement>(`/register-sessions/${id}/cash-out`, { amount, reason: reason || null }),

  close: (id: string, actualCash: number, notes?: string) =>
    post<RegisterClosingReport>(`/register-sessions/${id}/close`, { actualCash, notes: notes || null }),
};
