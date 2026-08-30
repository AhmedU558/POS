-- Story 3.2: seed INVENTORY_RECEIVE. No new tables.
-- Grants match ADR-018 D7: Super Administrator, Store Manager, Inventory Manager.

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'INVENTORY_RECEIVE', 'Receive stock into a store')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('Super Administrator', 'Store Manager', 'Inventory Manager')
  AND p.code = 'INVENTORY_RECEIVE'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
