INSERT INTO permissions (code, description)
VALUES ('operations:annotate', 'Add a note and clear a collection review flag')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ADMIN' AND p.code = 'operations:annotate'
ON CONFLICT DO NOTHING;
