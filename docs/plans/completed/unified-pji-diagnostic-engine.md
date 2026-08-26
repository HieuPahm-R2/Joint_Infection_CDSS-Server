# Execution Plan: Unified PJI diagnostic engine

Date: 2026-08-26

## Status

Completed

## Outcome

Backend owns one versioned, deterministic PJI diagnostic contract for both
episode snapshots and stateless quick diagnosis. Missing or unreadable evidence
never becomes a negative finding, preoperative and intraoperative stages remain
explicit, and every consumer receives the same conclusion for the same normalized
evidence.

## Context

- `src/main/java/com/vietnam/pji/services/diagnosis/`: current Backend rule engine.
- `src/main/java/com/vietnam/pji/services/agent/impl/AiRecommendationServiceImpl.java`:
  episode/recommendation consumers.
- `docs/decisions/0002-structured-surgery-icm-evidence.md`: structured surgery
  evidence authority.
- Coordinated Frontend plan:
  `Frontend_Client/docs/plans/completed/unified-pji-diagnostic-engine.md`.
- Clinical authority confirmed by the user on 2026-08-26: the validated 2018
  PJI definition is authoritative for chronic hip/knee PJI; proposed 2018 acute
  thresholds remain explicitly limited; the unpublished unified 2025 definition
  is not a scoring authority.

## Scope

In scope:

- Version and expose the diagnostic profile used for every result.
- Distinguish eligibility, missing evidence, negative evidence, preoperative
  score, intraoperative score, and combined score.
- Use chronic PMN `>80%`; retain acute CRP/WBC/PMN thresholds with an explicit
  validation limitation.
- Require exactly one positive culture for the two-point single-culture
  criterion; do not collapse multiple different organisms into that criterion.
- Add a stateless authenticated evaluation API for the quick calculator.
- Preserve the existing episode endpoint and persisted recommendation diagnostic
  shape where compatible.
- Add table-driven and contract tests for boundaries, missing data, cultures,
  acute/chronic behavior, and legacy snapshot normalization.

Out of scope:

- Implementing the unpublished Unified PJI Definition 2025.
- Allowing genomic/NGS results to change the core ICM score.
- Changing treatment recommendation policies.

## Approach

1. Introduce a typed stateless diagnostic request and a versioned profile.
2. Adapt the request and episode snapshot into the same canonical snapshot boundary.
3. Evaluate eligibility, major criteria, preoperative evidence, and
   intraoperative evidence as separate stages.
4. Return an incomplete result when required evidence for the requested stage is
   unknown; never interpret unknown as negative.
5. Preserve legacy report keys while adding stage/completeness/profile metadata.
6. Expose the stateless API and cover it with serialization/engine tests.
7. Validate existing recommendation flows and coordinate the Frontend migration.

## Risks And Recovery

- CRITICAL blast radius: the engine feeds direct diagnosis, synchronous and
  asynchronous recommendation generation, and retry. Mitigate with additive
  response fields, golden cases, and focused regression tests before consumer
  migration.
- Historical results may retain older profile semantics. Keep persisted results
  immutable and stamp all newly evaluated results with the new profile version.
- Recovery: revert Backend and Frontend commits together; existing persisted
  run-scoped diagnostics remain readable because legacy JSON keys are retained.

## Progress

- [x] Trace both current rule engines and compare clinical behavior.
- [x] Confirm clinical authority and architecture direction with the user.
- [x] Run pre-change GitNexus impact analysis (Backend risk: CRITICAL).
- [x] Implement typed request adaptation, versioned staged evaluator, and safe outcomes.
- [x] Add authenticated stateless quick-diagnosis endpoint and role permission migration.
- [x] Add focused boundary, missing-data, culture, phase, and request-contract tests.
- [x] Preserve additive legacy report keys for recommendation/persisted diagnostic consumers.
- [x] Coordinate and validate Frontend migration.

## Decisions

- 2026-08-26: Backend is the only authority for core PJI scoring; Frontend will
  not retain an independent ICM scoring implementation.
- 2026-08-26: Missing/unknown is distinct from negative and cannot produce a
  definitive `NOT_INFECTED` result.
- 2026-08-26: Chronic PMN uses `>80%` from the validated 2018 definition.
- 2026-08-26: Acute thresholds remain available with an explicit non-validation
  warning rather than being represented as validated chronic criteria.
- 2026-08-26: Genomic interpretation is supportive and cannot add to the core
  diagnostic score.

## Validation

- Focused proof: `./mvnw -q -Dtest=PjiDiagnosticRuleEngineTest,PjiDiagnosticEvaluationRequestDTOTest test` passed (15 tests).
- Compile proof: `./mvnw -q -DskipTests compile` passed.
- Cross-repository consumer proof: Frontend production build and PJI model/presentation tests passed.
- Full Backend suite attempted: unrelated sandbox infrastructure blocked 24 Mockito/Byte Buddy tests from attaching to the JVM; application-context test also could not open the configured PostgreSQL socket. No PJI-focused failure was observed.

## Result

Backend now evaluates both episode snapshots and stateless questionnaire input through one staged rule engine. New results identify `PJI_ICM_2018_VALIDATED_V1`, keep acute thresholds explicitly unvalidated, expose decision-stage versus available-evidence scores, and preserve unknown evidence through `INCOMPLETE` instead of coercing it to `NOT_INFECTED`.
