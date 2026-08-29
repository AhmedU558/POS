-- V11__create_product_tables.sql

-- 1. Seed PRODUCT_PRICE_WRITE permission
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'PRODUCT_PRICE_WRITE', 'Create and update product prices')
ON CONFLICT (code) DO NOTHING;

-- Grant to Super Administrator, Store Manager, and Inventory Manager
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('Super Administrator', 'Store Manager', 'Inventory Manager')
  AND p.code = 'PRODUCT_PRICE_WRITE'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- 2. Create products table
CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category_id UUID REFERENCES categories(id),
    brand_id UUID REFERENCES brands(id),
    unit_id UUID REFERENCES units(id),
    purchase_price NUMERIC(19,4) NOT NULL CHECK (purchase_price >= 0),
    selling_price NUMERIC(19,4) NOT NULL CHECK (selling_price >= 0),
    wholesale_price NUMERIC(19,4) CHECK (wholesale_price >= 0),
    tax_rate NUMERIC(7,4) NOT NULL CHECK (tax_rate >= 0),
    min_stock NUMERIC(19,4) NOT NULL CHECK (min_stock >= 0),
    max_stock NUMERIC(19,4) CHECK (max_stock >= 0),
    track_batch BOOLEAN NOT NULL DEFAULT false,
    track_expiry BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_products_category_id_is_active ON products(category_id, is_active);
CREATE INDEX idx_products_brand_id ON products(brand_id);

-- 3. Create product_barcodes table
CREATE TABLE product_barcodes (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    barcode VARCHAR(100) NOT NULL UNIQUE,
    is_primary BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_barcodes_product_id ON product_barcodes(product_id);

-- 4. Create product_prices table
CREATE TABLE product_prices (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    price_type VARCHAR(50) NOT NULL,
    amount NUMERIC(19,4) NOT NULL CHECK (amount >= 0),
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_to TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_prices_product_id ON product_prices(product_id);

