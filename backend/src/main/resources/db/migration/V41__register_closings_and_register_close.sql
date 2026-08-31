CREATE SEQUENCE z_report_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE register_closings (
    id UUID PRIMARY KEY,
    register_session_id UUID NOT NULL REFERENCES register_sessions(id),
    z_report_number VARCHAR(50) NOT NULL,
    notes VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_register_closings_session UNIQUE (register_session_id),
    CONSTRAINT uk_register_closings_z_report UNIQUE (z_report_number)
);

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'REGISTER_CLOSE', 'Close a register session and generate a Z report')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('Super Administrator', 'Store Manager', 'Cashier')
  AND p.code = 'REGISTER_CLOSE'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
