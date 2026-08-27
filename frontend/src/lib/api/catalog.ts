import { apiClient } from '../apiClient';
import { Category, CategoryRequest, Brand, BrandRequest, Unit, UnitRequest } from '../../types/catalog';

export class ApiError extends Error {
  constructor(public status: number, public message: string, public code?: string) {
    super(message);
    this.name = 'ApiError';
  }
}

async function handleResponse<T>(res: Response): Promise<T> {
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new ApiError(res.status, body.error?.message || 'An unexpected error occurred', body.error?.code);
  }
  return body.data;
}

export async function getCategories(): Promise<Category[]> {
  const res = await apiClient('/categories', { method: 'GET' });
  return handleResponse<Category[]>(res);
}

export async function createCategory(request: CategoryRequest): Promise<Category> {
  const res = await apiClient('/categories', { method: 'POST', body: JSON.stringify(request) });
  return handleResponse<Category>(res);
}

export async function updateCategory(id: string, request: CategoryRequest): Promise<Category> {
  const res = await apiClient(`/categories/${id}`, { method: 'PATCH', body: JSON.stringify(request) });
  return handleResponse<Category>(res);
}

export async function getBrands(): Promise<Brand[]> {
  const res = await apiClient('/brands', { method: 'GET' });
  return handleResponse<Brand[]>(res);
}

export async function createBrand(request: BrandRequest): Promise<Brand> {
  const res = await apiClient('/brands', { method: 'POST', body: JSON.stringify(request) });
  return handleResponse<Brand>(res);
}

export async function updateBrand(id: string, request: BrandRequest): Promise<Brand> {
  const res = await apiClient(`/brands/${id}`, { method: 'PATCH', body: JSON.stringify(request) });
  return handleResponse<Brand>(res);
}

export async function getUnits(): Promise<Unit[]> {
  const res = await apiClient('/units', { method: 'GET' });
  return handleResponse<Unit[]>(res);
}

export async function createUnit(request: UnitRequest): Promise<Unit> {
  const res = await apiClient('/units', { method: 'POST', body: JSON.stringify(request) });
  return handleResponse<Unit>(res);
}

export async function updateUnit(id: string, request: UnitRequest): Promise<Unit> {
  const res = await apiClient(`/units/${id}`, { method: 'PATCH', body: JSON.stringify(request) });
  return handleResponse<Unit>(res);
}
