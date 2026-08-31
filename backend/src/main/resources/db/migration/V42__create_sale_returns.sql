CREATE TABLE sale_returns (
    id UUID PRIMARY KEY,
    receipt_number VARCHAR(50) NOT NULL,
    sale_id UUID NOT NULL REFERENCES sales(id),
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
    reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_sale_returns_receipt_number UNIQUE (receipt_number)
);

CREATE INDEX idx_sale_returns_store_id_created_at ON sale_returns (store_id, created_at);
CREATE INDEX idx_sale_returns_sale_id ON sale_returns (sale_id);

CREATE TABLE sale_return_items (
    id UUID PRIMARY KEY,
    sale_return_id UUID NOT NULL REFERENCES sale_returns(id),
    sale_item_id UUID NOT NULL REFERENCES sale_items(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity NUMERIC(19,4) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19,4) NOT NULL CHECK (unit_price >= 0),
    discount_amount NUMERIC(19,4) NOT NULL CHECK (discount_amount >= 0),
    tax_amount NUMERIC(19,4) NOT NULL CHECK (tax_amount >= 0),
    line_total NUMERIC(19,4) NOT NULL CHECK (line_total >= 0),
    batch_id UUID REFERENCES inventory_batches(id)
);

CREATE TABLE refund_payments (
    id UUID PRIMARY KEY,
    return_id UUID NOT NULL REFERENCES sale_returns(id),
    payment_method_id UUID NOT NULL REFERENCES payment_methods(id),
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    reference_number VARCHAR(100),
    status VARCHAR(30) NOT NULL
);
