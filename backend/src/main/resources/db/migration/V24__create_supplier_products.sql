CREATE TABLE supplier_products (
    id UUID PRIMARY KEY,
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    product_id UUID NOT NULL REFERENCES products(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_supplier_products_supplier_id_product_id UNIQUE (supplier_id, product_id)
);

CREATE INDEX idx_supplier_products_product_id ON supplier_products (product_id);
