import { Page, getBare, patchBare, postBare, query } from './http';

/**
 * Store running costs.
 *
 * `ExpenseController` returns its payload without the `data` envelope, so these calls go through
 * the bare helpers rather than silently reading `undefined`.
 */
export interface Expense {
  id: string;
  storeId: string;
  category: string;
  amount: number;
  expenseDate: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
}

export interface ExpenseRequest {
  storeId: string;
  category: string;
  amount: number;
  expenseDate: string;
  description?: string | null;
}

/**
 * The API takes any string as a category. These are offered as suggestions so that spelling
 * stays consistent — budget variance groups expenses by exactly this string, and "Rent" and
 * "rent" would report as two different things.
 */
export const EXPENSE_CATEGORIES = [
  'Rent',
  'Utilities',
  'Wages',
  'Stock purchases',
  'Equipment',
  'Maintenance',
  'Marketing',
  'Insurance',
  'Transport',
  'Other',
];

export const expensesApi = {
  list: (params: { page?: number; size?: number; sort?: string } = {}) =>
    getBare<Page<Expense>>(`/expenses${query({ ...params })}`),
  get: (id: string) => getBare<Expense>(`/expenses/${id}`),
  create: (body: ExpenseRequest) => postBare<Expense>('/expenses', body),
  update: (id: string, body: ExpenseRequest) => patchBare<Expense>(`/expenses/${id}`, body),
};
