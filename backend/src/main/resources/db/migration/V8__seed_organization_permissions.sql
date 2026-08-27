INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'STORE_READ', 'View stores.'),
    (gen_random_uuid(), 'STORE_WRITE', 'Create and modify stores.'),
    (gen_random_uuid(), 'TERMINAL_READ', 'View terminals.'),
    (gen_random_uuid(), 'TERMINAL_WRITE', 'Create and modify terminals.'),
    (gen_random_uuid(), 'REGISTER_READ', 'View registers.'),
    (gen_random_uuid(), 'REGISTER_WRITE', 'Create and modify registers.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'Super Administrator'
  AND p.code IN ('STORE_READ', 'STORE_WRITE', 'TERMINAL_READ', 'TERMINAL_WRITE', 'REGISTER_READ', 'REGISTER_WRITE')
ON CONFLICT (role_id, permission_id) DO NOTHING;
