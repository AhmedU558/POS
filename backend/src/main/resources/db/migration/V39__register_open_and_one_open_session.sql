CREATE UNIQUE INDEX uk_register_sessions_one_open
    ON register_sessions (register_id)
    WHERE status = 'OPEN';

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'REGISTER_OPEN', 'Open a register session')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('Super Administrator', 'Store Manager', 'Cashier')
  AND p.code = 'REGISTER_OPEN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
