CREATE TABLE store_fbr_configs (
    store_id uuid NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    enabled boolean NOT NULL DEFAULT false,
    environment varchar(20) NOT NULL DEFAULT 'sandbox',
    ntn varchar(20),
    strn varchar(30),
    pos_id varchar(50),
    encrypted_secret varchar(255),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (store_id)
);
