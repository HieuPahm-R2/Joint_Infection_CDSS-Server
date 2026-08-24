-- ANTIBIOTIC recommendations contain the systemic regimen, local regimen and
-- the pharmacist care/monitoring plan. V26 predates the care-plan category and
-- therefore rejects the worker result, rolling the whole consumer transaction
-- back and leaving the run in PROCESSING.
ALTER TABLE public.ai_recommendation_items
    DROP CONSTRAINT IF EXISTS ai_recommendation_items_treatment_category_check;

ALTER TABLE public.ai_recommendation_items
    ADD CONSTRAINT ai_recommendation_items_treatment_category_check
    CHECK (category IN (
        'SYSTEMIC_ANTIBIOTIC',
        'SURGERY_PROCEDURE',
        'LOCAL_ANTIBIOTIC',
        'ANTIBIOTIC_CARE_PLAN'
    ));
