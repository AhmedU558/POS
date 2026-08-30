CREATE TABLE goods_receipts (
    id UUID PRIMARY KEY,
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    store_id UUID NOT NULL REFERENCES stores(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE goods_receipt_items (
    id UUID PRIMARY KEY,
    goods_receipt_id UUID NOT NULL REFERENCES goods_receipts(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity NUMERIC(19,4) NOT NULL,
    batch_number VARCHAR(100),
    expiration_date DATE,
    manufacturing_date DATE,
    CONSTRAINT ck_goods_receipt_items_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX idx_goods_receipts_purchase_order_id ON goods_receipts (purchase_order_id);
CREATE INDEX idx_goods_receipts_store_id ON goods_receipts (store_id);
CREATE INDEX idx_goods_receipt_items_goods_receipt_id ON goods_receipt_items (goods_receipt_id);
