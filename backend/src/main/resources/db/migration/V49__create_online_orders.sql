CREATE TABLE online_orders (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id),
    customer_id UUID REFERENCES customers(id),
    channel VARCHAR(50) NOT NULL,
    external_order_id VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    subtotal NUMERIC(19,4) NOT NULL,
    discount_total NUMERIC(19,4) NOT NULL,
    tax_total NUMERIC(19,4) NOT NULL,
    grand_total NUMERIC(19,4) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_online_orders_external UNIQUE (channel, external_order_id)
);

CREATE INDEX idx_online_orders_store_id_created_at ON online_orders (store_id, created_at);

CREATE TABLE online_order_items (
    id UUID PRIMARY KEY,
    online_order_id UUID NOT NULL REFERENCES online_orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity NUMERIC(19,4) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19,4) NOT NULL CHECK (unit_price >= 0),
    discount_amount NUMERIC(19,4) NOT NULL CHECK (discount_amount >= 0),
    tax_amount NUMERIC(19,4) NOT NULL CHECK (tax_amount >= 0),
    line_total NUMERIC(19,4) NOT NULL CHECK (line_total >= 0)
);

CREATE INDEX idx_online_order_items_online_order_id ON online_order_items (online_order_id);
