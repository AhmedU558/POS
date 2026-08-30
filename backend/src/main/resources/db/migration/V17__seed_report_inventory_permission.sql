INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'REPORT_INVENTORY', 'Read inventory, movement, and expiry reports')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('Super Administrator', 'Store Manager', 'Inventory Manager', 'Accountant')
  AND p.code = 'REPORT_INVENTORY'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
