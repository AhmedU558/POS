CREATE TABLE promotions (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(30) NOT NULL,
    discount_value NUMERIC(19,4) NOT NULL,
    start_date TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    priority INT NOT NULL DEFAULT 0,
    stackable BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL REFERENCES users(id)
);

CREATE INDEX idx_promotions_store_id ON promotions (store_id);

CREATE TABLE promotion_rules (
    id UUID PRIMARY KEY,
    promotion_id UUID NOT NULL REFERENCES promotions(id),
    rule_type VARCHAR(30) NOT NULL,
    rule_value VARCHAR(255) NOT NULL
);

CREATE INDEX idx_promotion_rules_promotion_id ON promotion_rules (promotion_id);

CREATE TABLE discount_assignments (
    id UUID PRIMARY KEY,
    promotion_id UUID NOT NULL REFERENCES promotions(id),
    assignment_type VARCHAR(30) NOT NULL,
    assignment_id UUID NOT NULL
);

CREATE INDEX idx_discount_assignments_promotion_id ON discount_assignments (promotion_id);
