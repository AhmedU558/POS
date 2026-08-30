INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'PURCHASE_READ', 'Read purchase orders'),
    (gen_random_uuid(), 'PURCHASE_WRITE', 'Create and update draft purchase orders'),
    (gen_random_uuid(), 'PURCHASE_APPROVE', 'Submit or cancel draft purchase orders')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('Super Administrator', 'Store Manager', 'Inventory Manager')
  AND p.code IN ('PURCHASE_READ', 'PURCHASE_WRITE', 'PURCHASE_APPROVE')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
