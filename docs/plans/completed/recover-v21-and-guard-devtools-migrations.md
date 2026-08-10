# Execution Plan: Recover V21 and prevent DevTools migration restarts

Date: 2026-08-10

## Status

Completed

## Outcome

The development database validates V21 against its current source content, and
DevTools no longer restarts the backend while migrations are being edited.

## Context

- `V21__remove_pharmacist_final_decisions.sql` was applied at 22:50:58 on
  2026-08-09; the current file was written at 22:54:42.
- The project includes Spring Boot DevTools with no migration-path exclusion.
- `docs/plans/completed/remove-pharmacist-final-decision.md` defines V21's
  intended end state.

## Scope

In scope:

- Verify V21 postconditions in the local development database.
- Repair V21's Flyway checksum if the postconditions match.
- Exclude migrations from DevTools automatic restart in the dev profile.

Out of scope:

- Re-running V21's destructive SQL.
- Changing production configuration or database state.

## Approach

1. Inspect V21's postconditions with read-only database queries.
2. Add a V22 forward-only migration for any postconditions absent from the
   database.
3. Update the local dev DevTools configuration to ignore migration resources.
4. Repair V21 metadata and apply V22.
5. Validate the entire migration chain.

## Risks And Recovery

- V21 drops a table. Never re-run it; only repair metadata after verifying its
  effects.
- Excluding migration resources means migrations are applied only after an
  intentional manual restart. This is the desired development workflow.
- If postconditions are absent, preserve the original checksum and write a new
  forward migration rather than repairing V21.

## Progress

- [x] Establish V21 was modified after application.
- [x] Verify V21 postconditions: the table and permission are absent, but one
  review still has legacy pharmacist-plan keys.
- [x] Add V22 and DevTools migration exclusion.
- [x] Repair V21, apply V22, and validate.

## Decisions

- 2026-08-10: Exclude `db/migration/**` from automatic DevTools restarts in
  the development profile. The user explicitly requires protection from a
  migration being applied before an agent has finished writing it.
- 2026-08-10: Add V22 for the one remaining legacy JSON document rather than
  repair V21 alone. The V21 source postcondition is not fully present.

## Validation

- Live database postcondition query: no legacy plan keys remain; V21 and V22
  are successful in schema history.
- Flyway validate after repair: 22 migrations validated successfully.
- Configuration parse/build check: YAML resolves `db/migration/**`,
  `./mvnw -DskipTests compile` and `git diff --check` passed.

## Result

V21 was repaired to checksum `686782415`; it was not replayed. V22 ran once
and removed the one remaining legacy pharmacist-plan document. DevTools now
ignores migration resources in the development profile, so a manual restart is
required after completing any future migration.
