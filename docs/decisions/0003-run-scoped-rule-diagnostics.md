# 0003 Run-scoped Rule Diagnostics

Date: 2026-08-16

## Status

Accepted

## Context

The backend calculates a deterministic PJI diagnosis from the same clinical
snapshot sent to Rag_Agentic for each recommendation run. That diagnosis was
stored partly on `ai_recommendation_runs` and partly as a fourth
`DIAGNOSTIC_TEST` row in `ai_recommendation_items`. Historical UI flows could
instead recalculate the current episode, causing an old treatment plan to be
shown with a newer diagnosis. Run warnings also conditionally mixed rule-engine
and RAG output.

## Decision

Persist exactly one `rule_based_diagnostic_results` row per recommendation run.
It owns the title, diagnostic payload, assessment, explanation, and diagnostic
warnings calculated from the run's immutable clinical snapshot.

Restrict `ai_recommendation_items` to exactly these treatment domains:

- `SYSTEMIC_ANTIBIOTIC`
- `SURGERY_PROCEDURE`
- `LOCAL_ANTIBIOTIC`

`ai_recommendation_runs.warnings_json` is owned exclusively by the public
Rag_Agentic response and contains processing/treatment warnings. The run-detail
API returns the rule result as a separate `diagnostic` object.

## Alternatives Considered

1. Recalculate diagnosis whenever a historical run is opened. This makes a run
   internally inconsistent after medical data changes.
2. Keep `DIAGNOSTIC_TEST` as a recommendation item. This mixes deterministic
   backend diagnosis with AI treatment recommendations and permits a fourth
   category despite the RAG contract.
3. Keep assessment/explanation directly on the run. This duplicates ownership
   and makes warning provenance ambiguous.

## Consequences

Positive:

- Historical diagnosis and treatment recommendations share one snapshot.
- Recommendation item persistence matches the three-category RAG contract.
- RAG and diagnostic warning provenance is explicit.

Tradeoffs:

- Deployment requires an irreversible data migration unless a pre-migration
  backup is restored.
- RAG warnings discarded by older conditional persistence cannot be recreated.
- Clients must migrate from run fields/diagnostic items to `detail.diagnostic`.

## Follow-Up

- Resolve the independent baseline-to-V20 Flyway failure before relying on a
  fresh database migration through every historical version.
