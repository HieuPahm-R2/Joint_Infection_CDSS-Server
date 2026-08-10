-- V21 was applied before its cleanup of pharmacist-plan keys was completed.
-- Reconcile the remaining legacy JSON fields without replaying V21's table drop.
UPDATE public.doctor_recommendation_reviews
SET modification_json = NULLIF(
    modification_json - 'systemicAntibiotic' - 'localAntibiotic',
    '{}'::jsonb
)
WHERE modification_json ? 'systemicAntibiotic'
   OR modification_json ? 'localAntibiotic';
