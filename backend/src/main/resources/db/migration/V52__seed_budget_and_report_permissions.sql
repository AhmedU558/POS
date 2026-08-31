INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'BUDGET_READ', 'Read budgets'),
    (gen_random_uuid(), 'BUDGET_WRITE', 'Create and edit budgets'),
    (gen_random_uuid(), 'BUDGET_APPROVE', 'Approve budgets'),
    (gen_random_uuid(), 'EXPENSE_READ', 'Read expenses'),
    (gen_random_uuid(), 'EXPENSE_WRITE', 'Record and edit expenses'),
    (gen_random_uuid(), 'REPORT_SALES', 'Access sales reports'),
    (gen_random_uuid(), 'REPORT_INVENTORY', 'Access inventory reports'),
    (gen_random_uuid(), 'REPORT_FINANCE', 'Access finance reports'),
    (gen_random_uuid(), 'REPORT_CASH', 'Access cash register reports')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('Super Administrator', 'Store Manager')
  AND p.code IN ('BUDGET_READ', 'BUDGET_WRITE', 'BUDGET_APPROVE', 'EXPENSE_READ', 'EXPENSE_WRITE', 'REPORT_SALES', 'REPORT_INVENTORY', 'REPORT_FINANCE', 'REPORT_CASH')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
