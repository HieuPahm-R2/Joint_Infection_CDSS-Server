# Execution Plan: Recommendation Final Decisions

Date: 2026-08-09

## Status

Completed

## Outcome

Recommendation regeneration now sends only the current clinical snapshot and
the backend rule-engine diagnosis to RAG. Each AI run has at most one review;
that review owns doctor and pharmacist decision records, and one review version
per episode can be selected as final.

## Authority And Decisions

- The user request dated 2026-08-09 is product authority for the behavior.
- `SensitivityResult` remains canonical episode/rule-engine input. The
  pharmacist decision owns a JSON snapshot so historical versions do not drift.
- No pharmacist role policy was invented because the repository has no
  authoritative pharmacist role; existing review permissions remain in force.
- Legacy review JSON is kept as a response/write compatibility bridge.

## Implemented

- Removed prior accepted diagnosis DTOs, assembly, mapping, repository query,
  and outbound request fields.
- Added `rule_based_diagnosis` for HTTP and `ruleBasedDiagnosis` for RabbitMQ.
- Added one-to-one doctor/pharmacist decision entities and review/run uniqueness.
- Added final-version selection and pharmacist-decision APIs.
- Added Flyway V20 schema, backfill, indexes, permissions, and fresh-DB seeds.

## Validation

- `./mvnw -DskipTests compile`: passed.
- `./mvnw -Dtest=RecommendationContractSerializationTest,PjiDiagnosticRuleEngineTest test`:
  passed, 8 tests.
- Full `./mvnw test`: 26 tests ran with 0 assertion failures; 3 environment-only
  errors remain (Mockito agent attach blocked twice, PostgreSQL socket blocked once).
- `git diff --check`: passed.
- GitNexus change detection was run; its stale index rated the intentionally
  changed recommendation/review flows critical, so those flows were manually
  reviewed and covered by the focused build/tests above.

## Recovery

Revert the coordinated source changes and restore the pre-V20 database backup
before rolling the migration back.
