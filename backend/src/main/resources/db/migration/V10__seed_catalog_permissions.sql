-- Seed catalog permissions for Phase 2
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'PRODUCT_READ', 'Read product catalog data'),
    (gen_random_uuid(), 'PRODUCT_WRITE', 'Create and update product catalog data')
ON CONFLICT (code) DO NOTHING;

-- Grant catalog permissions to Super Administrator
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'Super Administrator'
  AND p.code IN ('PRODUCT_READ', 'PRODUCT_WRITE')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Grant catalog permissions to Store Manager
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'Store Manager'
  AND p.code IN ('PRODUCT_READ', 'PRODUCT_WRITE')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Grant catalog permissions to Inventory Manager
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'Inventory Manager'
  AND p.code IN ('PRODUCT_READ', 'PRODUCT_WRITE')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Grant PRODUCT_READ to Cashier
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'Cashier'
  AND p.code = 'PRODUCT_READ'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
