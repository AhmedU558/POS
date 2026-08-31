CREATE TABLE quotations (
    id UUID PRIMARY KEY,
    quotation_number VARCHAR(50) NOT NULL,
    store_id UUID NOT NULL REFERENCES stores(id),
    customer_id UUID REFERENCES customers(id),
    created_by UUID NOT NULL REFERENCES users(id),
    status VARCHAR(30) NOT NULL,
    subtotal NUMERIC(19,4) NOT NULL,
    discount_total NUMERIC(19,4) NOT NULL,
    tax_total NUMERIC(19,4) NOT NULL,
    grand_total NUMERIC(19,4) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    expiration_date TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_quotations_number UNIQUE (quotation_number)
);

CREATE INDEX idx_quotations_store_id_created_at ON quotations (store_id, created_at);

CREATE TABLE quotation_items (
    id UUID PRIMARY KEY,
    quotation_id UUID NOT NULL REFERENCES quotations(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity NUMERIC(19,4) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19,4) NOT NULL CHECK (unit_price >= 0),
    discount_amount NUMERIC(19,4) NOT NULL CHECK (discount_amount >= 0),
    tax_amount NUMERIC(19,4) NOT NULL CHECK (tax_amount >= 0),
    line_total NUMERIC(19,4) NOT NULL CHECK (line_total >= 0)
);

CREATE INDEX idx_quotation_items_quotation_id ON quotation_items (quotation_id);
