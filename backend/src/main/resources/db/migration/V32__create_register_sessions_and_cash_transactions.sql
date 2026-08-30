CREATE TABLE register_sessions (
    id UUID PRIMARY KEY,
    register_id UUID NOT NULL REFERENCES registers(id),
    cashier_id UUID NOT NULL REFERENCES users(id),
    opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMPTZ,
    opening_cash NUMERIC(19,4),
    expected_cash NUMERIC(19,4),
    actual_cash NUMERIC(19,4),
    variance NUMERIC(19,4),
    status VARCHAR(30) NOT NULL
);

CREATE INDEX idx_register_sessions_register_id_status
    ON register_sessions (register_id, status);

CREATE TABLE cash_transactions (
    id UUID PRIMARY KEY,
    register_session_id UUID NOT NULL REFERENCES register_sessions(id),
    transaction_type VARCHAR(30) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    reason VARCHAR(255),
    reference_type VARCHAR(50),
    reference_id UUID,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
