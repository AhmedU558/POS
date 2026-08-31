import { Page, del, get, patch, post, query } from './http';
import {
  BarcodeRequest,
  Brand,
  BrandRequest,
  Category,
  CategoryRequest,
  PriceRequest,
  Product,
  ProductBarcode,
  ProductCreateRequest,
  ProductPrice,
  ProductUpdateRequest,
  Unit,
  UnitRequest,
} from '../../types/catalog';

export { ApiError } from './http';

export interface ProductSearchParams {
  query?: string;
  categoryId?: string;
  brandId?: string;
  isActive?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

/*
 * `GET /products` returns a Spring Data page, not an array. It was previously typed as
 * `Product[]`, so `products.map` ran against the page envelope and the list screen threw.
 *
 * `query` matches name, SKU and barcode server-side (ProductRepository.searchProducts), which is
 * what lets one field serve both the catalogue search box and the till's barcode scanner.
 */
export async function searchProducts(params: ProductSearchParams = {}): Promise<Page<Product>> {
  return get<Page<Product>>(
    `/products${query({
      query: params.query,
      categoryId: params.categoryId,
      brandId: params.brandId,
      isActive: params.isActive,
      page: params.page,
      size: params.size,
      sort: params.sort,
    })}`
  );
}

export async function getProduct(id: string): Promise<Product> {
  return get<Product>(`/products/${id}`);
}

export async function createProduct(body: ProductCreateRequest): Promise<Product> {
  return post<Product>('/products', body);
}

export async function updateProduct(id: string, body: ProductUpdateRequest): Promise<Product> {
  return patch<Product>(`/products/${id}`, body);
}

export async function updateProductStatus(id: string, isActive: boolean): Promise<void> {
  return patch<void>(`/products/${id}/status`, { isActive });
}

export async function getProductBarcodes(id: string): Promise<ProductBarcode[]> {
  return get<ProductBarcode[]>(`/products/${id}/barcodes`);
}

export async function addProductBarcode(id: string, body: BarcodeRequest): Promise<ProductBarcode> {
  return post<ProductBarcode>(`/products/${id}/barcodes`, body);
}

export async function removeProductBarcode(id: string, barcodeId: string): Promise<void> {
  return del<void>(`/products/${id}/barcodes/${barcodeId}`);
}

export async function getProductPrices(id: string): Promise<ProductPrice[]> {
  return get<ProductPrice[]>(`/products/${id}/prices`);
}

export async function addProductPrice(id: string, body: PriceRequest): Promise<ProductPrice> {
  return post<ProductPrice>(`/products/${id}/prices`, body);
}

export async function getCategories(): Promise<Category[]> {
  return get<Category[]>('/categories');
}

export async function createCategory(body: CategoryRequest): Promise<Category> {
  return post<Category>('/categories', body);
}

export async function updateCategory(id: string, body: CategoryRequest): Promise<Category> {
  return patch<Category>(`/categories/${id}`, body);
}

export async function getBrands(): Promise<Brand[]> {
  return get<Brand[]>('/brands');
}

export async function createBrand(body: BrandRequest): Promise<Brand> {
  return post<Brand>('/brands', body);
}

export async function updateBrand(id: string, body: BrandRequest): Promise<Brand> {
  return patch<Brand>(`/brands/${id}`, body);
}

export async function getUnits(): Promise<Unit[]> {
  return get<Unit[]>('/units');
}

export async function createUnit(body: UnitRequest): Promise<Unit> {
  return post<Unit>('/units', body);
}

export async function updateUnit(id: string, body: UnitRequest): Promise<Unit> {
  return patch<Unit>(`/units/${id}`, body);
}

/** Reference data for the product form, loaded as one unit so the form never renders half-ready. */
export interface CatalogReferenceData {
  categories: Category[];
  brands: Brand[];
  units: Unit[];
}

export async function getCatalogReferenceData(): Promise<CatalogReferenceData> {
  const [categories, brands, units] = await Promise.all([getCategories(), getBrands(), getUnits()]);
  return { categories, brands, units };
}
