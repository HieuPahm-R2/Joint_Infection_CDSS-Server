WITH inserted_permission AS (
    INSERT INTO public.permissions (name, api_path, method, module)
    SELECT '[auto] POST /api/v1/pji-diagnostics/evaluate',
           '/api/v1/pji-diagnostics/evaluate',
           'POST',
           'PJI_DIAGNOSTICS'
    WHERE NOT EXISTS (
        SELECT 1 FROM public.permissions
        WHERE method = 'POST' AND api_path = '/api/v1/pji-diagnostics/evaluate'
    )
    RETURNING id
), diagnostic_permission AS (
    SELECT id FROM inserted_permission
    UNION ALL
    SELECT id FROM public.permissions
    WHERE method = 'POST' AND api_path = '/api/v1/pji-diagnostics/evaluate'
    LIMIT 1
), professional_roles AS (
    SELECT id FROM public.roles
    WHERE upper(name) IN ('DOCTOR', 'PHARMACIST', 'ADMIN', 'SUPER_ADMIN')
)
INSERT INTO public.role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM professional_roles role
CROSS JOIN diagnostic_permission permission
ON CONFLICT DO NOTHING;
