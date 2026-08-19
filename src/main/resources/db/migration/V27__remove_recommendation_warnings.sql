ALTER TABLE "rule_based_diagnostic_results"
    DROP COLUMN IF EXISTS "warnings_json";

ALTER TABLE "ai_recommendation_runs"
    DROP COLUMN IF EXISTS "warnings_json";
