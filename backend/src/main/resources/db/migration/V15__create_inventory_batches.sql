CREATE TABLE inventory_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    store_id UUID NOT NULL REFERENCES stores(id),
    batch_number VARCHAR(100) NOT NULL,
    quantity NUMERIC(19,4) NOT NULL DEFAULT 0,
    expiration_date DATE,
    manufacturing_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_inventory_batches_product_id_store_id_batch_number
        UNIQUE (product_id, store_id, batch_number)
);

CREATE INDEX idx_inventory_batches_product_id_expiration_date
    ON inventory_batches (product_id, expiration_date);

ALTER TABLE inventory_transactions
    ADD CONSTRAINT fk_inventory_transactions_batch_id
    FOREIGN KEY (batch_id) REFERENCES inventory_batches(id);
