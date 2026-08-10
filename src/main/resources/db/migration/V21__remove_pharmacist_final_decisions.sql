-- Pharmacist decisions are no longer a separate aggregate. Preserve any
-- antibiogram rows that exist only in the version snapshot before dropping it.
WITH raw_snapshot_rows AS (
    SELECT
        (culture_snapshot ->> 'cultureId')::bigint AS culture_id,
        sensitivity ->> 'antibioticName' AS antibiotic_name,
        NULLIF(sensitivity ->> 'micValue', '') AS mic_value,
        NULLIF(sensitivity ->> 'sensitivityCode', '') AS sensitivity_code,
        decision.created_by,
        decision.updated_by,
        decision.created_at,
        decision.updated_at
    FROM public.pharmacist_final_decisions decision
    CROSS JOIN LATERAL jsonb_array_elements(
        COALESCE(decision.sensitivity_results_json, '[]'::jsonb)
    ) culture_snapshot
    CROSS JOIN LATERAL jsonb_array_elements(
        COALESCE(culture_snapshot -> 'sensitivities', '[]'::jsonb)
    ) sensitivity
    WHERE culture_snapshot ->> 'cultureId' ~ '^[0-9]+$'
      AND NULLIF(trim(sensitivity ->> 'antibioticName'), '') IS NOT NULL
), latest_snapshot_rows AS (
    SELECT DISTINCT ON (culture_id, lower(antibiotic_name))
        culture_id,
        antibiotic_name,
        mic_value,
        sensitivity_code,
        created_by,
        updated_by,
        created_at,
        updated_at
    FROM raw_snapshot_rows
    ORDER BY culture_id, lower(antibiotic_name), updated_at DESC
)
INSERT INTO public.sensitivity_results (
    culture_id,
    antibiotic_name,
    mic_value,
    sensitivity_code,
    created_by,
    updated_by,
    created_at,
    updated_at
)
SELECT
    snapshot.culture_id,
    snapshot.antibiotic_name,
    snapshot.mic_value,
    snapshot.sensitivity_code,
    snapshot.created_by,
    snapshot.updated_by,
    snapshot.created_at,
    snapshot.updated_at
FROM latest_snapshot_rows snapshot
WHERE EXISTS (
    SELECT 1
    FROM public.culture_results culture
    WHERE culture.id = snapshot.culture_id
)
AND NOT EXISTS (
    SELECT 1
    FROM public.sensitivity_results existing
    WHERE existing.culture_id = snapshot.culture_id
      AND lower(existing.antibiotic_name) = lower(snapshot.antibiotic_name)
);

UPDATE public.doctor_recommendation_reviews
SET modification_json = NULLIF(
    modification_json - 'systemicAntibiotic' - 'localAntibiotic',
    '{}'::jsonb
)
WHERE modification_json ? 'systemicAntibiotic'
   OR modification_json ? 'localAntibiotic';

DELETE FROM public.role_permissions role_permission
USING public.permissions permission
WHERE role_permission.permission_id = permission.id
  AND permission.api_path = '/api/v1/doctor-reviews/{reviewId}/pharmacist-final-decision'
  AND permission.method = 'PUT';

DELETE FROM public.permissions
WHERE api_path = '/api/v1/doctor-reviews/{reviewId}/pharmacist-final-decision'
  AND method = 'PUT';

DROP TABLE public.pharmacist_final_decisions;
