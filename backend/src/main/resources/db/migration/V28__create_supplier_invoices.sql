CREATE TABLE supplier_invoices (
    id UUID PRIMARY KEY,
    invoice_number VARCHAR(100) NOT NULL,
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    invoice_date DATE NOT NULL,
    due_date DATE NOT NULL,
    total_amount NUMERIC(19,4) NOT NULL,
    paid_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_supplier_invoices_invoice_number UNIQUE (invoice_number)
);

CREATE INDEX idx_supplier_invoices_supplier_id_status_due_date
    ON supplier_invoices (supplier_id, status, due_date);
