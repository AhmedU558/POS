-- Reference data for the identity domain.
--
-- Roles are taken verbatim from the approved roles-and-permissions.md.
-- Permission codes are the identity codes defined by REST API Specification section 7.
-- Codes for other modules are seeded by the migrations that introduce those modules, so no
-- migration grants access to endpoints that do not exist yet.
--
-- No users are seeded. On a fresh deployment nobody can authenticate yet: the first
-- administrator account is created by Story 1.2, which owns that security decision.
--
-- Every statement is idempotent: re-running this migration against a populated database
-- changes nothing. Database Design & ERD Specification section 26 asks for seed data to be
-- separated from schema changes; this file adds no DDL.

INSERT INTO roles (id, name, description) VALUES
    (gen_random_uuid(), 'Super Administrator',
     'Full system, organization, stores, users, roles, configuration, and reports.'),
    (gen_random_uuid(), 'Store Manager',
     'Store operations, inventory, suppliers, reports, discounts, registers, and approvals.'),
    (gen_random_uuid(), 'Cashier',
     'POS sales, customer registration, authorized discounts, returns, receipts, and register closing.'),
    (gen_random_uuid(), 'Inventory Manager',
     'Products, receiving, adjustments, transfers, batches, expiry, and stock reports.'),
    (gen_random_uuid(), 'Accountant',
     'Purchasing/AP, supplier payments, budgets, expenses, and financial reports.'),
    (gen_random_uuid(), 'Online Order Staff',
     'Online order review, fulfillment, cancellation, and refund workflows.')
ON CONFLICT (name) DO NOTHING;

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'USER_READ',  'List and view users.'),
    (gen_random_uuid(), 'USER_WRITE', 'Create and update users.'),
    (gen_random_uuid(), 'USER_ADMIN', 'Activate and deactivate user accounts.'),
    (gen_random_uuid(), 'ROLE_READ',  'List roles and permissions.'),
    (gen_random_uuid(), 'ROLE_WRITE', 'Create roles and replace role permissions.')
ON CONFLICT (code) DO NOTHING;

-- Least privilege (roles-and-permissions.md): only Super Administrator administers identity.
--
-- UI/UX Specification section 33 marks Users/Roles as "Limited" for Store Manager, but no
-- approved document defines what "Limited" grants. Rather than invent a grant, the other five
-- roles receive no identity permissions here. The gap is resolved when the user and role
-- endpoints are built.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'Super Administrator'
  AND p.code IN ('USER_READ', 'USER_WRITE', 'USER_ADMIN', 'ROLE_READ', 'ROLE_WRITE')
ON CONFLICT (role_id, permission_id) DO NOTHING;
