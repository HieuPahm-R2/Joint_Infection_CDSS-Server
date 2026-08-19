# Execution Plan: Run-scoped Rule Diagnostics

Date: 2026-08-16

## Status

Completed

## Outcome

Every recommendation run owns an immutable rule-engine diagnostic calculated
from that run's clinical snapshot. `ai_recommendation_items` contains only the
three treatment categories, while RAG run warnings and diagnostic warnings have
separate persistence ownership.

## Context

- User request dated 2026-08-16.
- `AiRecommendationServiceImpl` creates the clinical snapshot and rule result.
- `RabbitMQConsumer` persists the asynchronous Rag_Agentic response.
- Rag_Agentic's public response contract already exposes exactly three treatment
  items and processing/treatment `warnings_json`.
- Coordinated frontend plan:
  `Frontend_Client/docs/plans/active/run-scoped-rule-diagnostics.md`.

## Scope

In scope:

- Add one rule-based diagnostic model per recommendation run.
- Backfill historical diagnostics from existing diagnostic items/run fields.
- Remove diagnostic items and the two diagnosis-owned columns from run rows.
- Restrict recommendation item categories to the three treatment categories.
- Return the run-scoped diagnostic as a separate run-detail field.
- Persist `ai_recommendation_runs.warnings_json` only from Rag_Agentic.

Out of scope:

- Changing rule-engine clinical calculations.
- Changing Rag_Agentic synthesis or its already-correct public item contract.
- Reconstructing RAG warnings that older backend logic discarded.

## Approach

1. Add a Flyway migration that creates and backfills the diagnostic table,
   cleans diagnostic items/citations, separates recognizable historical
   diagnostic warnings, drops obsolete run columns, and adds a category check.
2. Add the entity/repository/DTO and change both synchronous and asynchronous
   generation paths to save/read it.
3. Make both AI-result consumers treat run warnings as RAG-owned and reject
   unknown treatment categories instead of converting them to diagnostics.
4. Version the run-detail cache namespace so pre-migration payloads cannot leak
   the removed shape after deployment.

## Risks And Recovery

- Risk: migration touches historical clinical decision data. Mitigation:
  backfill before deletion and preserve the original snapshot/run relation.
- Risk: older RAG warnings may already have been discarded when diagnostic
  warnings were non-empty. This cannot be reconstructed; the migration only
  removes warnings provably equal to the diagnostic item's embedded warnings.
- Recovery: restore the pre-migration database backup and revert the coordinated
  backend/frontend changes. The migration is not safely reversible without the
  backup because it intentionally deletes legacy diagnostic item rows.

## Progress

- [x] Trace current snapshot, diagnostic, item, warning, and API flows.
- [x] Implement schema/model/API changes.
- [x] Add focused backend proof.
- [x] Run repository validation and record results.

## Decisions

- 2026-08-16: The user request is authority for exactly three recommendation
  categories and a separate diagnostic model.
- 2026-08-16: Existing Rag_Agentic response schema is authority that run-level
  `warnings_json` contains RAG processing/treatment warnings; rule-engine
  warnings belong to the new diagnostic record.
- 2026-08-16: Standalone evaluation remains transient for a newly selected
  episode; persisted historical display must use the diagnostic tied to a run.

## Validation

- Focused proof:
  `./mvnw -Dtest=RecommendationRunPersistenceContractTest,RecommendationContractSerializationTest,PjiDiagnosticRuleEngineTest test`
  passed: 12 tests, 0 failures/errors.
- Migration proof: PostgreSQL 16 applied V1 then V26 successfully. A legacy
  fixture proved one diagnostic was backfilled, its `DIAGNOSTIC_TEST` item and
  synthetic citation were removed, the treatment item remained, the diagnostic
  warning was cleared from the run, and `pji_probability=INFECTED` was retained
  on the new diagnostic row. The temporary container/database were removed.
- Compile proof: `./mvnw -DskipTests compile` passed.
- Rag_Agentic contract proof:
  `uv run pytest -q tests/test_recommendation_service.py tests/test_recommendation_route.py`
  passed: 8 tests.
- Full backend suite: 49 tests ran with 0 assertion failures and 15
  environment-only errors: 14 Mockito inline-agent attachment errors and one
  blocked PostgreSQL socket in the application-context test.
- Repository-required checks: `git diff --check` passed. GitNexus MCP was not
  available, so impact/change review used the repository-prescribed source and
  diff fallback.
- Existing migration limitation: applying every SQL file to a fresh V1 database
  stops in V20 because that migration references `role.id`; isolated V26 proof
  passes and this unrelated pre-existing V20 defect remains follow-up work.

## Result

Recommendation runs now expose a separate snapshot-bound diagnostic, retain
only RAG warnings on the run, and persist only the three treatment categories
as recommendation items. Historical diagnostic data is migrated before legacy
rows/columns are removed.
