# Execution Plan: Reconcile current V20 with the development database

Date: 2026-08-09

## Status

Completed

## Outcome

The development database can start with the current V20 migration content while
preserving every required final-decision schema, data backfill, and permission.

## Context

- `src/main/resources/db/migration/V20__recommendation_final_decisions.sql`
- `flyway_schema_history` records V20 with checksum `2120700590`; current source
  resolves to `359101744`.

## Scope

In scope:

- Read the configured development database schema and relevant data.
- Add a V21 reconciliation migration only for state absent from the database.
- Repair V20's checksum only after confirming the database is semantically
  aligned with the current V20 intent.

Out of scope:

- Altering production databases.
- Rewriting or rolling back V20.

## Approach

1. Inspect the live development database state required by V20.
2. Compare it with current V20's schema, backfill, and permissions.
3. Add a safe, minimal V21 reconciliation migration for gaps found.
4. Run Flyway repair and migrate against the configured development database.
5. Verify Flyway history and the resulting database state.

## Risks And Recovery

- Repair only changes Flyway metadata; do it after schema comparison and retain
  the pre-repair checksum in this plan.
- New DDL/data may be partially present. Use conditional SQL and conflict-safe
  inserts where required.
- If validation fails, restore the prior `flyway_schema_history` checksum from
  `2120700590` and do not run V21.

## Progress

- [x] Establish V20 checksum mismatch and preserve current V20 as desired state.
- [x] Inspect the development database.
- [x] Determine that V21 is unnecessary: all schema, indexes, backfill rows,
  and permissions produced by V20 are already present.
- [x] Repair the V20 checksum.
- [x] Verify Flyway validates all 20 migrations.

## Decisions

- 2026-08-09: Preserve current V20. The user requires its contents; replacing
  it with the historical version would discard desired behavior.
- 2026-08-09: Do not add V21. Live development schema and backfill match the
  current V20 behavior; the difference is checksum metadata only.

## Validation

- Focused proof: live development schema has all V20 tables, columns, unique
  indexes, one-to-one backfill counts, and required permissions.
- Integration proof: Flyway repair aligned one migration and subsequent Flyway
  validate succeeded for all 20 migrations.

## Result

V20 checksum was repaired from `2120700590` to `359101744` in the local
development database. No business tables were changed; no V21 was required.
Flyway validate completed successfully for all 20 migrations.
