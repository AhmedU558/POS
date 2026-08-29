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
  name?: string;
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
  name?: string;
  description?: string | null;
  isActive?: boolean;
}

export interface Unit {
  id: string;
  name: string;
  abbreviation: string;
  allowFractions: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UnitRequest {
  name?: string;
  abbreviation?: string;
  allowFractions?: boolean;
  isActive?: boolean;
}

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
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProductRequest {
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
}

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

export interface ProductPrice {
  id: string;
  productId: string;
  priceType: 'REGULAR' | 'PROMOTIONAL' | 'WHOLESALE';
  amount: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PriceRequest {
  priceType: 'REGULAR' | 'PROMOTIONAL' | 'WHOLESALE';
  amount: number;
  effectiveFrom: string;
  effectiveTo?: string | null;
}
