# Execution Plan: Split PJI diagnostic rule engine by responsibility

Date: 2026-08-05

## Status

Completed

## Outcome

`PjiDiagnosticRuleEngine` remains the stable Spring service boundary while its
data access, criteria evaluation, culture evaluation, and report construction
are separated into focused Spring components without changing the diagnostic
API response contract.

## Context

- `README.md`: backend owns the API contract and AI recommendation orchestration.
- `src/main/java/com/vietnam/pji/services/diagnosis/PjiDiagnosticRuleEngine.java`:
  formerly a 1,082-line deterministic diagnostic calculator.
- `src/test/java/com/vietnam/pji/services/diagnosis/PjiDiagnosticRuleEngineTest.java`:
  behavioral proof for representative rule paths.
- GitNexus impact for `evaluate`: CRITICAL because it is used by diagnostic,
  recommendation-generation, and retry flows; its public signature and output
  shape remained stable.

## Scope

In scope:

- Extract cohesive internal responsibilities to package-private Spring components.
- Preserve `PjiDiagnosticRuleEngine.evaluate(Map<String, Object>)` and
  `DiagnosticResult` as the external service/API boundary.
- Add focused regression proof for culture resistance and data-quality warnings.

Out of scope:

- Changes to PJI diagnostic criteria, thresholds, API routes, or persisted JSON schema.

## Approach

1. Added a shared snapshot reader for normalized clinical-data access.
2. Extracted culture evidence, major/minor criteria, and report-building components.
3. Reduced `PjiDiagnosticRuleEngine` to Spring-injected orchestration while
   retaining the result type and JSON assembly order.

## Risks And Recovery

- Risk: changing rule ordering or response-map keys can affect API consumers.
  Mitigation: retained the original contract and added representative regression coverage.
- Recovery: source-only change, recoverable by reverting the diagnosis package changes.

## Progress

- [x] Inspect product/workflow guidance and caller impact.
- [x] Extract focused components and retain stable facade.
- [x] Add regression proof and validate build.
- [x] Record result and move plan to completed.

## Decisions

- 2026-08-05: Kept the existing facade and nested `DiagnosticResult` because
  the controller and service expose that type directly.
- 2026-08-05: No diagnostic policy changed; existing code and tests remain the
  authority for behavior preservation.

## Validation

- Focused proof: `./mvnw clean -Dtest=PjiDiagnosticRuleEngineTest test` passed
  (3 tests, 0 failures/errors).
- Build proof: `./mvnw -DskipTests package` passed.
- Diff validation: `git diff --check` passed.
- GitNexus changed-scope inspection: reported low risk and no affected indexed
  processes; newly added components are untracked and therefore not yet present
  in the current index.

## Result

The engine is now a small orchestration facade backed by four package-private
Spring components: snapshot reader, culture evaluator, criteria evaluator, and
report builder. Existing output contract, thresholds, and rule text are retained.
