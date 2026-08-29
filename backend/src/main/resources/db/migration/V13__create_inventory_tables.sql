CREATE TABLE inventory_balances (     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),     product_id UUID NOT NULL REFERENCES products(id),     store_id UUID NOT NULL REFERENCES stores(id),     quantity NUMERIC(19,4) NOT NULL DEFAULT 0,     last_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),     UNIQUE (product_id, store_id) );  CREATE TABLE inventory_transactions (     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),     product_id UUID NOT NULL REFERENCES products(id),     store_id UUID NOT NULL REFERENCES stores(id),     batch_id UUID,     transaction_type VARCHAR(40) NOT NULL,     quantity NUMERIC(19,4) NOT NULL,     reference_type VARCHAR(50),     reference_id UUID,     unit_cost NUMERIC(19,4),     reason VARCHAR(255),     created_by UUID REFERENCES users(id),     created_at TIMESTAMPTZ NOT NULL DEFAULT NOW() );  CREATE INDEX idx_inventory_transactions_lookup  ON inventory_transactions (product_id, store_id, created_at); 

CREATE OR REPLACE FUNCTION reject_inventory_transaction_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'inventory_transactions rows are immutable: % rejected', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_inventory_transactions_immutable
    BEFORE UPDATE OR DELETE ON inventory_transactions
    FOR EACH ROW EXECUTE FUNCTION reject_inventory_transaction_mutation();

CREATE TRIGGER trg_inventory_transactions_no_truncate
    BEFORE TRUNCATE ON inventory_transactions
    FOR EACH STATEMENT EXECUTE FUNCTION reject_inventory_transaction_mutation();