INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'PROMOTION_READ', 'Read promotions'),
    (gen_random_uuid(), 'PROMOTION_WRITE', 'Create and edit promotions')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('Super Administrator', 'Store Manager')
  AND p.code IN ('PROMOTION_READ', 'PROMOTION_WRITE')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'Cashier'
  AND p.code IN ('PROMOTION_READ')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
