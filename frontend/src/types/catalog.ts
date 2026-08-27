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
