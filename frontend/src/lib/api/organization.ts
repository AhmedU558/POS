import { get, patch, post } from './http';

/*
 * Stores, terminals and registers.
 *
 * These endpoints existed server-side with no screen in front of them, which is why a fresh
 * installation could never reach a working till: a sale requires an open register session, a
 * session requires a register, a register requires a terminal, and a terminal requires a store.
 */

export interface Store {
  id: string;
  code: string;
  name: string;
  currencyCode: string;
  timezone: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface StoreRequest {
  code: string;
  name: string;
  currencyCode: string;
  timezone: string;
}

export interface Terminal {
  id: string;
  storeId: string;
  code: string;
  name: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface TerminalRequest {
  code: string;
  name: string;
  status: string;
}

export interface Register {
  id: string;
  storeId: string;
  terminalId: string;
  code: string;
  name: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface RegisterRequest {
  terminalId: string;
  code: string;
  name: string;
  status: string;
}

export const TERMINAL_STATUSES = ['ACTIVE', 'INACTIVE'] as const;
export const REGISTER_STATUSES = ['ACTIVE', 'INACTIVE'] as const;

export const storesApi = {
  /** Returns only the stores the signed-in user is assigned to (REST API Specification 30). */
  list: () => get<Store[]>('/stores'),
  get: (id: string) => get<Store>(`/stores/${id}`),
  create: (body: StoreRequest) => post<Store>('/stores', body),
  update: (id: string, body: StoreRequest) => patch<Store>(`/stores/${id}`, body),
  setStatus: (id: string, active: boolean) => patch<Store>(`/stores/${id}/status`, { active }),
};

export const terminalsApi = {
  list: (storeId: string) => get<Terminal[]>(`/stores/${storeId}/terminals`),
  get: (storeId: string, id: string) => get<Terminal>(`/stores/${storeId}/terminals/${id}`),
  create: (storeId: string, body: TerminalRequest) => post<Terminal>(`/stores/${storeId}/terminals`, body),
  update: (storeId: string, id: string, body: TerminalRequest) =>
    patch<Terminal>(`/stores/${storeId}/terminals/${id}`, body),
};

export const registersApi = {
  list: (storeId: string) => get<Register[]>(`/stores/${storeId}/registers`),
  get: (storeId: string, id: string) => get<Register>(`/stores/${storeId}/registers/${id}`),
  create: (storeId: string, body: RegisterRequest) => post<Register>(`/stores/${storeId}/registers`, body),
  update: (storeId: string, id: string, body: RegisterRequest) =>
    patch<Register>(`/stores/${storeId}/registers/${id}`, body),
};
