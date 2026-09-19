-- Development data is not retained across the role-scoped recommendation cutover.
-- Delete the only non-cascading dependent records before deleting combined runs.
DELETE FROM public.ai_chat_sessions session
USING public.ai_recommendation_runs run
WHERE session.run_id = run.id
  AND run.recommendation_scope = 'LEGACY_COMBINED';

DELETE FROM public.recommendation_final_selections
WHERE recommendation_scope = 'LEGACY_COMBINED';

DELETE FROM public.ai_recommendation_runs
WHERE recommendation_scope = 'LEGACY_COMBINED';

ALTER TABLE public.ai_recommendation_runs
    ALTER COLUMN recommendation_scope DROP DEFAULT,
    DROP CONSTRAINT chk_ai_recommendation_scope,
    ADD CONSTRAINT chk_ai_recommendation_scope
        CHECK (recommendation_scope IN ('SURGERY', 'ANTIBIOTIC'));

ALTER TABLE public.recommendation_final_selections
    ALTER COLUMN recommendation_scope DROP DEFAULT,
    DROP CONSTRAINT chk_final_selection_scope,
    ADD CONSTRAINT chk_final_selection_scope
        CHECK (recommendation_scope IN ('SURGERY', 'ANTIBIOTIC'));
