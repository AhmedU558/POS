CREATE TABLE customer_credits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES customers(id),
    balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency_code CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_customer_credits_customer_id UNIQUE (customer_id),
    CONSTRAINT ck_customer_credits_balance_non_negative CHECK (balance >= 0)
);

CREATE TABLE customer_credit_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_credit_id UUID NOT NULL REFERENCES customer_credits(id),
    transaction_type VARCHAR(40) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    reference_type VARCHAR(50),
    reference_id UUID,
    balance_after NUMERIC(19,4) NOT NULL,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_customer_credit_transactions_amount_nonzero CHECK (amount <> 0)
);

CREATE INDEX idx_customer_credit_transactions_credit_id_created_at
    ON customer_credit_transactions (customer_credit_id, created_at);

CREATE OR REPLACE FUNCTION reject_customer_credit_transaction_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'customer_credit_transactions rows are immutable: % rejected', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_customer_credit_transactions_immutable
    BEFORE UPDATE OR DELETE ON customer_credit_transactions
    FOR EACH ROW EXECUTE FUNCTION reject_customer_credit_transaction_mutation();

CREATE TRIGGER trg_customer_credit_transactions_no_truncate
    BEFORE TRUNCATE ON customer_credit_transactions
    FOR EACH STATEMENT EXECUTE FUNCTION reject_customer_credit_transaction_mutation();
