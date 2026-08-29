import { apiClient } from '../apiClient';
import { 
  Category, CategoryRequest, Brand, BrandRequest, Unit, UnitRequest,
  Product, ProductRequest, ProductBarcode, BarcodeRequest, ProductPrice, PriceRequest 
} from '../../types/catalog';

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

// ---- Products ----

export async function getProducts(params?: {
  query?: string;
  categoryId?: string;
  brandId?: string;
  isActive?: boolean;
  page?: number;
  size?: number;
}): Promise<Product[]> {
  const searchParams = new URLSearchParams();
  if (params?.query) searchParams.append('query', params.query);
  if (params?.categoryId) searchParams.append('categoryId', params.categoryId);
  if (params?.brandId) searchParams.append('brandId', params.brandId);
  if (params?.isActive !== undefined) searchParams.append('isActive', params.isActive.toString());
  if (params?.page !== undefined) searchParams.append('page', params.page.toString());
  if (params?.size !== undefined) searchParams.append('size', params.size.toString());

  // Note: If backend supports proper pagination envelopes, this signature should return a PaginatedResponse.
  // We'll assume the /products endpoint returns a flat array in `data` or we handle it here.
  const res = await apiClient(`/products?${searchParams.toString()}`);
  return handleResponse<Product[]>(res);
}

export async function getProduct(id: string): Promise<Product> {
  const res = await apiClient(`/products/${id}`);
  return handleResponse<Product>(res);
}

export async function createProduct(data: ProductRequest): Promise<Product> {
  const res = await apiClient('/products', {
    method: 'POST',
    body: JSON.stringify(data),
  });
  return handleResponse<Product>(res);
}

export async function updateProduct(id: string, data: Partial<ProductRequest>): Promise<Product> {
  const res = await apiClient(`/products/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(data),
  });
  return handleResponse<Product>(res);
}

export async function updateProductStatus(id: string, isActive: boolean): Promise<void> {
  const res = await apiClient(`/products/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ isActive }),
  });
  return handleResponse<void>(res);
}

export async function getProductBarcodes(id: string): Promise<ProductBarcode[]> {
  const res = await apiClient(`/products/${id}/barcodes`);
  return handleResponse<ProductBarcode[]>(res);
}

export async function addProductBarcode(id: string, data: BarcodeRequest): Promise<ProductBarcode> {
  const res = await apiClient(`/products/${id}/barcodes`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
  return handleResponse<ProductBarcode>(res);
}

export async function removeProductBarcode(id: string, barcodeId: string): Promise<void> {
  const res = await apiClient(`/products/${id}/barcodes/${barcodeId}`, {
    method: 'DELETE',
  });
  return handleResponse<void>(res);
}

export async function getProductPrices(id: string): Promise<ProductPrice[]> {
  const res = await apiClient(`/products/${id}/prices`);
  return handleResponse<ProductPrice[]>(res);
}

export async function addProductPrice(id: string, data: PriceRequest): Promise<ProductPrice> {
  const res = await apiClient(`/products/${id}/prices`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
  return handleResponse<ProductPrice>(res);
}
