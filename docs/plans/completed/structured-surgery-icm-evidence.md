# Execution Plan: Structured surgery ICM evidence

Date: 2026-08-08

## Status

Completed

## Outcome

Positive histology and intraoperative purulence persist as nullable structured
surgery fields and take precedence in ICM scoring, with text fallback retained
for legacy records.

## Scope

- Added PostgreSQL migration, entity/DTO/mapper/snapshot fields, pending-task
  recognition, structured-first scoring, and focused tests.
- Kept single-positive-culture derived from culture-result rows.

## Progress

- [x] Add persistence and API fields.
- [x] Prefer structured snapshot evidence in the rule engine.
- [x] Run focused and repository checks.

## Decisions

- 2026-08-08: Use nullable booleans to distinguish unknown from explicit negative.
- 2026-08-08: Preserve `findings` parsing only as a legacy fallback.

## Validation

- Clean Java compilation succeeded.
- `PjiDiagnosticRuleEngineTest` and `SurgeryMapperTest`: 8 tests passed.
- GitNexus change detection completed; `git diff --check` passed.

## Result

Completed. The additive migration still requires deployment through the normal
Flyway startup path; no database-backed migration test was available locally.
