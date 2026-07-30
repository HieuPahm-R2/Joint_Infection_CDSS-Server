# Execution Plan: Episode Medical Record Refactor

Date: 2026-07-30

## Status

Completed

## Outcome

The backend persists the episode management and diagnosis structure represented
by the supplied medical record, exposes the surgical-disease clinical field,
and no longer exposes index-arthroplasty days in the model/request contract.

## Context

- `renew_episode.jpg` in the workspace.
- `PjiEpisode`, `EpisodeRequestDTO`, and `EpisodeMapper`.
- `ClinicalRecord`, `ClinicalRecordRequestDTO`, and the existing snapshot
  compatibility contract.

## Scope

In scope:

- Additive episode fields and a typed JSON department-transfer value object.
- A Flyway migration for the new episode columns and the non-destructive
  `notations` to `surgical_disease` rename.
- Clinical model/DTO changes requested by the user.

Out of scope:

- Diagnosis/recommendation behavior changes.
- Dropping the legacy `days_since_index_arthroplasty` database column.

## Approach

Add the migration and value object, update the entity and request DTO, preserve
the existing snapshot getter contract internally, add mapper/contract tests,
then run focused and repository tests.

## Risks And Recovery

- The snapshot assembler is CRITICAL blast radius; do not edit it. Preserve its
  `getNotations()` input through an internal compatibility getter.
- Existing uncommitted changes in the mapper and service are user-owned and
  must remain intact.
- The migration is additive except for a data-preserving column rename. Recovery
  is a compensating migration plus a code revert.

## Progress

- [x] Inspect schema, contracts, existing edits, and blast radius.
- [x] Implement migration, model, DTO, and tests.
- [x] Run focused and full Maven proof.
- [x] Review affected flows and move this plan to completed.

## Decisions

- 2026-07-30: Keep `days_since_index_arthroplasty` physically in the database
  as dormant legacy data while removing it from application contracts.
- 2026-07-30: Preserve the `notations` snapshot key through a JSON-hidden model
  getter backed by `surgicalDisease`.

## Validation

- Focused proof: episode mapper, clinical mapper/JSON contract, existing
  clinical mapper, and diagnostic rule-engine tests passed.
- Integration or end-to-end proof: Flyway validated all 13 migrations, applied
  V13 to the local development database, and Hibernate schema validation
  passed. The full Spring context test remains blocked by an unrelated existing
  missing `PriorAcceptedDiagnosisMapper` bean.
- Repository-required checks: GitNexus change detection completed with MEDIUM
  aggregate risk; snapshot assembler and diagnostic rule engine were not
  modified.

## Result

Implemented additive episode persistence, the typed department-transfer JSON
value object, the Flyway schema change, and the clinical field contract. Seven
tests in the full run passed; the sole context-load error is the pre-existing
missing mapper bean described above. Legacy arthroplasty-day data remains in a
dormant database column for recovery while it is absent from model, DTO, and UI
contracts.
