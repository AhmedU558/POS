INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'QUOTATION_READ', 'Read quotations'),
    (gen_random_uuid(), 'QUOTATION_WRITE', 'Create and edit quotations'),
    (gen_random_uuid(), 'QUOTATION_SEND', 'Mark quotations as sent'),
    (gen_random_uuid(), 'QUOTATION_APPROVE', 'Accept or reject quotations'),
    (gen_random_uuid(), 'QUOTATION_CONVERT', 'Convert quotations to sales')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('Super Administrator', 'Store Manager')
  AND p.code IN ('QUOTATION_READ', 'QUOTATION_WRITE', 'QUOTATION_SEND', 'QUOTATION_APPROVE', 'QUOTATION_CONVERT')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'Cashier'
  AND p.code IN ('QUOTATION_READ', 'QUOTATION_WRITE', 'QUOTATION_CONVERT')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
