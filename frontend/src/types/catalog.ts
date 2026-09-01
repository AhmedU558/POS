export interface Category {
  id: string;
  name: string;
  description: string | null;
  parentId: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CategoryRequest {
  name: string;
  description?: string | null;
  parentId?: string | null;
  isActive?: boolean;
}

export interface Brand {
  id: string;
  name: string;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface BrandRequest {
  name: string;
  description?: string | null;
  isActive?: boolean;
}

/** Mirrors `UnitResponse`: a unit is a code and a name. There is no abbreviation field. */
export interface Unit {
  id: string;
  code: string;
  name: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UnitRequest {
  code: string;
  name: string;
  isActive?: boolean;
}

/**
 * Mirrors `ProductResponse`.
 *
 * The active flag is `isActive`, not `active`: the backend record component is named `isActive`
 * and Jackson serialises records by component name. Reading `active` here silently produced
 * `undefined`, so every product displayed as inactive and saving the edit form deactivated it.
 * Category, brand and unit use the bare `active` name — the contract is not uniform.
 */
export interface Product {
  id: string;
  sku: string;
  name: string;
  description: string | null;
  categoryId: string | null;
  brandId: string | null;
  unitId: string | null;
  purchasePrice: number;
  sellingPrice: number;
  wholesalePrice: number | null;
  taxRate: number;
  minStock: number;
  maxStock: number | null;
  trackBatch: boolean;
  trackExpiry: boolean;
  isActive: boolean;
  imageUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

/** `POST /products`. Carries `isActive`; the update contract does not. */
export interface ProductCreateRequest {
  sku: string;
  name: string;
  description?: string | null;
  categoryId?: string | null;
  brandId?: string | null;
  unitId?: string | null;
  purchasePrice: number;
  sellingPrice: number;
  wholesalePrice?: number | null;
  taxRate: number;
  minStock: number;
  maxStock?: number | null;
  trackBatch: boolean;
  trackExpiry: boolean;
  isActive: boolean;
  imageUrl?: string | null;
}

/**
 * `PATCH /products/{id}`. Deliberately omits `isActive` — activation moves through
 * `PATCH /products/{id}/status`, and sending it here has no effect.
 */
export type ProductUpdateRequest = Omit<ProductCreateRequest, 'isActive'>;

export interface ProductBarcode {
  id: string;
  productId: string;
  barcode: string;
  isPrimary: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface BarcodeRequest {
  barcode: string;
  isPrimary: boolean;
}

export type PriceType = 'REGULAR' | 'PROMOTIONAL' | 'WHOLESALE';

export interface ProductPrice {
  id: string;
  productId: string;
  priceType: PriceType;
  amount: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PriceRequest {
  priceType: PriceType;
  amount: number;
  effectiveFrom: string;
  effectiveTo?: string | null;
}
