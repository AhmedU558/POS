-- Seed inventory permissions for Phase 3
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'INVENTORY_READ', 'Read inventory balances and ledger'),
    (gen_random_uuid(), 'INVENTORY_ADJUST', 'Perform manual stock adjustments')
ON CONFLICT (code) DO NOTHING;

-- Grant INVENTORY_READ and INVENTORY_ADJUST to Super Administrator
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'Super Administrator'
  AND p.code IN ('INVENTORY_READ', 'INVENTORY_ADJUST')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Grant INVENTORY_READ and INVENTORY_ADJUST to Store Manager
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'Store Manager'
  AND p.code IN ('INVENTORY_READ', 'INVENTORY_ADJUST')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Grant INVENTORY_READ and INVENTORY_ADJUST to Inventory Manager
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'Inventory Manager'
  AND p.code IN ('INVENTORY_READ', 'INVENTORY_ADJUST')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
