CREATE TABLE customers (
    id UUID PRIMARY KEY,
    customer_code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    email VARCHAR(255),
    address TEXT,
    credit_limit NUMERIC(19,4) NOT NULL CHECK (credit_limit >= 0),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_customers_customer_code UNIQUE (customer_code)
);

CREATE INDEX idx_customers_name ON customers (name);
CREATE INDEX idx_customers_is_active ON customers (is_active);
