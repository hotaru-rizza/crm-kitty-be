-- Grant audit.view to ADMIN role for existing tenants (OWNER bypasses permission checks in code).
INSERT INTO role_permissions (id, tenant_id, role, permission, granted, created_at, updated_at)
SELECT gen_random_uuid(), admin_tenants.tenant_id, 'ADMIN', 'audit.view', true, NOW(), NOW()
FROM (
    SELECT DISTINCT tenant_id
    FROM role_permissions
    WHERE role = 'ADMIN'
) admin_tenants
WHERE NOT EXISTS (
    SELECT 1
    FROM role_permissions existing
    WHERE existing.tenant_id = admin_tenants.tenant_id
      AND existing.role = 'ADMIN'
      AND existing.permission = 'audit.view'
);
