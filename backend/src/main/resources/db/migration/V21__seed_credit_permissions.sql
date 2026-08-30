INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'CREDIT_READ', 'Read store credit balances and ledger'),
    (gen_random_uuid(), 'CREDIT_WRITE', 'Issue, redeem, and adjust store credit')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('Super Administrator', 'Store Manager', 'Cashier')
  AND p.code IN ('CREDIT_READ', 'CREDIT_WRITE')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
