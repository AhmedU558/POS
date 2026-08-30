CREATE TABLE payment_methods (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(30) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true
);

INSERT INTO payment_methods (id, code, name, type, is_active)
VALUES (gen_random_uuid(), 'CASH', 'Cash', 'CASH', true);
