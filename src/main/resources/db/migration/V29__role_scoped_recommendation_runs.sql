ALTER TABLE public.ai_recommendation_runs
    ADD COLUMN recommendation_scope character varying(30)
        NOT NULL DEFAULT 'LEGACY_COMBINED',
    ADD CONSTRAINT chk_ai_recommendation_scope
        CHECK (recommendation_scope IN ('SURGERY', 'ANTIBIOTIC', 'LEGACY_COMBINED'));

CREATE INDEX idx_ai_recommendation_runs_episode_scope_created
    ON public.ai_recommendation_runs (episode_id, recommendation_scope, created_at DESC);

ALTER TABLE public.pharmacist_final_decisions
    ADD COLUMN care_plan_json jsonb;

ALTER TABLE public.recommendation_final_selections
    ADD COLUMN recommendation_scope character varying(30)
        NOT NULL DEFAULT 'LEGACY_COMBINED',
    ADD CONSTRAINT chk_final_selection_scope
        CHECK (recommendation_scope IN ('SURGERY', 'ANTIBIOTIC', 'LEGACY_COMBINED'));

ALTER TABLE public.recommendation_final_selections
    DROP CONSTRAINT IF EXISTS recommendation_final_selections_episode_id_key,
    DROP CONSTRAINT IF EXISTS recommendation_final_selections_run_id_key;

DROP INDEX IF EXISTS public.recommendation_final_selections_episode_id_key;
DROP INDEX IF EXISTS public.recommendation_final_selections_run_id_key;

CREATE UNIQUE INDEX uq_final_selection_episode_scope
    ON public.recommendation_final_selections (episode_id, recommendation_scope);

CREATE UNIQUE INDEX uq_final_selection_run
    ON public.recommendation_final_selections (run_id);

-- Generation permissions remain path based. Both professional roles may call
-- the endpoint; RecommendationAccessService enforces the requested scope.
WITH pharmacist_role AS (
    SELECT id FROM public.roles WHERE upper(name) = 'PHARMACIST'
), generation_permissions AS (
    SELECT id FROM public.permissions
    WHERE method = 'POST'
      AND api_path IN (
          '/api/v1/episodes/{episodeId}/ai-recommendations/generate',
          '/api/v1/episodes/{episodeId}/diagnostic-test/evaluate'
      )
)
INSERT INTO public.role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM pharmacist_role role
CROSS JOIN generation_permissions permission
ON CONFLICT DO NOTHING;
