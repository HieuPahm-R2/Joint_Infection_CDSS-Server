# Execution Plan: Remove Pharmacist Final Decision

Date: 2026-08-09

## Status

Completed

## Outcome

Doctor recommendation versions remain intact, while pharmacist final-decision
persistence and API surfaces are removed. Canonical `SensitivityResult` rows
are the only stored antibiogram decision data.

## Implemented

- Removed the entity, repository, request DTO, review relation, service method,
  controller endpoint, permission seed, and eager initialization.
- Added V21 because V20 is already applied in development and its checksum was
  repaired. V20 remains immutable.
- V21 backfills snapshot-only sensitivity rows into `sensitivity_results`,
  removes legacy pharmacist plan keys and permissions, then drops the old table.

## Validation

- `./mvnw -DskipTests compile`: passed.
- Focused recommendation contract and rule-engine tests: 8 passed.
- `git diff --check`: passed.
- Refreshed GitNexus index; final backend change detection reports LOW risk.
- V21 was not executed against a live database in this task; deployment must
  run it with the normal pre-migration backup procedure.

## Recovery

Restore the pre-V21 database backup and revert the coordinated source changes.

