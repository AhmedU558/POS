CREATE SEQUENCE sale_receipt_number_seq;

CREATE TABLE sales (
    id UUID PRIMARY KEY,
    receipt_number VARCHAR(50) NOT NULL,
    store_id UUID NOT NULL REFERENCES stores(id),
    terminal_id UUID NOT NULL REFERENCES terminals(id),
    register_id UUID NOT NULL REFERENCES registers(id),
    register_session_id UUID NOT NULL REFERENCES register_sessions(id),
    cashier_id UUID NOT NULL REFERENCES users(id),
    customer_id UUID REFERENCES customers(id),
    status VARCHAR(30) NOT NULL,
    subtotal NUMERIC(19,4) NOT NULL,
    discount_total NUMERIC(19,4) NOT NULL,
    tax_total NUMERIC(19,4) NOT NULL,
    grand_total NUMERIC(19,4) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_sales_receipt_number UNIQUE (receipt_number)
);

CREATE INDEX idx_sales_store_id_created_at ON sales (store_id, created_at);
CREATE INDEX idx_sales_register_id_register_session_id ON sales (register_id, register_session_id);

CREATE TABLE sale_items (
    id UUID PRIMARY KEY,
    sale_id UUID NOT NULL REFERENCES sales(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity NUMERIC(19,4) NOT NULL,
    unit_price NUMERIC(19,4) NOT NULL,
    discount_amount NUMERIC(19,4) NOT NULL,
    tax_amount NUMERIC(19,4) NOT NULL,
    line_total NUMERIC(19,4) NOT NULL,
    batch_id UUID REFERENCES inventory_batches(id),
    CONSTRAINT ck_sale_items_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX idx_sale_items_product_id_sale_id ON sale_items (product_id, sale_id);

CREATE TABLE sale_payments (
    id UUID PRIMARY KEY,
    sale_id UUID NOT NULL REFERENCES sales(id),
    payment_method_id UUID NOT NULL REFERENCES payment_methods(id),
    amount NUMERIC(19,4) NOT NULL,
    reference_number VARCHAR(100),
    status VARCHAR(30) NOT NULL
);
