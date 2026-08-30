CREATE TABLE stock_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id UUID NOT NULL REFERENCES stores(id),
    product_id UUID NOT NULL REFERENCES products(id),
    batch_id UUID REFERENCES inventory_batches(id),
    alert_type VARCHAR(40) NOT NULL,
    quantity NUMERIC(19,4) NOT NULL,
    minimum_level NUMERIC(19,4),
    expiration_date DATE,
    status VARCHAR(20) NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    acknowledged_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_stock_alerts_type CHECK (alert_type IN ('LOW_STOCK', 'EXPIRY')),
    CONSTRAINT chk_stock_alerts_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED'))
);

CREATE UNIQUE INDEX uq_stock_alerts_low_stock
    ON stock_alerts (store_id, product_id)
    WHERE alert_type = 'LOW_STOCK';

CREATE UNIQUE INDEX uq_stock_alerts_expiry
    ON stock_alerts (store_id, batch_id)
    WHERE alert_type = 'EXPIRY';
