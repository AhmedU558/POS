CREATE TABLE supplier_payments (
    id UUID PRIMARY KEY,
    supplier_invoice_id UUID NOT NULL REFERENCES supplier_invoices(id),
    amount NUMERIC(19,4) NOT NULL,
    payment_date DATE NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    reference VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_supplier_payments_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_supplier_payments_supplier_invoice_id
    ON supplier_payments (supplier_invoice_id);
