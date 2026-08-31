CREATE TABLE budgets (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id),
    name VARCHAR(255) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL REFERENCES users(id)
);

CREATE INDEX idx_budgets_store_id ON budgets (store_id);

CREATE TABLE budget_lines (
    id UUID PRIMARY KEY,
    budget_id UUID NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    category VARCHAR(100) NOT NULL,
    allocated_amount NUMERIC(19,4) NOT NULL CHECK (allocated_amount >= 0)
);

CREATE INDEX idx_budget_lines_budget_id ON budget_lines (budget_id);

CREATE TABLE expenses (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id),
    category VARCHAR(100) NOT NULL,
    amount NUMERIC(19,4) NOT NULL CHECK (amount >= 0),
    expense_date DATE NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL REFERENCES users(id)
);

CREATE INDEX idx_expenses_store_id_date ON expenses (store_id, expense_date);
