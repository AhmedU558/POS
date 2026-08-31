INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'ORDER_CREATE', 'Create external online orders'),
    (gen_random_uuid(), 'ORDER_READ', 'Read online orders'),
    (gen_random_uuid(), 'ORDER_FULFILL', 'Confirm and fulfill online orders'),
    (gen_random_uuid(), 'ORDER_CANCEL', 'Cancel online orders'),
    (gen_random_uuid(), 'ORDER_REFUND', 'Refund online orders')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('Super Administrator', 'Store Manager')
  AND p.code IN ('ORDER_CREATE', 'ORDER_READ', 'ORDER_FULFILL', 'ORDER_CANCEL', 'ORDER_REFUND')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'Cashier'
  AND p.code IN ('ORDER_READ', 'ORDER_FULFILL', 'ORDER_CANCEL')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
