INSERT INTO payment_methods (id, code, name, type, is_active) VALUES
    (gen_random_uuid(), 'CARD', 'Card', 'CARD', true),
    (gen_random_uuid(), 'STORE_CREDIT', 'Store Credit', 'STORE_CREDIT', true),
    (gen_random_uuid(), 'OTHER', 'Other', 'OTHER', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'PAYMENT_READ', 'Read configured payment methods')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('Super Administrator', 'Store Manager', 'Cashier')
  AND p.code = 'PAYMENT_READ'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
