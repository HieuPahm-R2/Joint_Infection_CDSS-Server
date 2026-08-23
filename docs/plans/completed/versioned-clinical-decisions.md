# Versioned Clinical Decisions

Date: 2026-08-23

## Status

Completed

## Outcome

Every successful AI recommendation run exposes one independently owned doctor
decision lane and one independently owned pharmacist decision lane. A clinician
cannot overwrite another clinician's draft or signed decision, and an episode
can select a run as final only after both decisions are signed.

## Context

- `AiRecommendationRun` is the immutable recommendation version.
- `DoctorRecommendationReview` currently conflates run review state, doctor
  content, and episode-final selection.
- V21 removed the prior pharmacist-decision table, so this work must use a new
  forward-only migration.
- The coordinated frontend plan is
  `Frontend_Client/docs/plans/completed/versioned-clinical-decision-workspace.md`.

## Scope

In scope:

- Decision persistence, ownership, draft/sign lifecycle, optimistic revision,
  final-run selection, aggregate workspace reads, permissions, and tests.
- Compatibility reads/writes for the existing doctor-review endpoints while the
  frontend migrates.
- Server-side enforcement of the existing episode edit lock for aggregate saves.

Out of scope:

- Recovering pharmacist treatment plans deleted by V21 without a backup.
- Changing the RAG treatment-generation contract.

## Approach

1. Add forward-only schema and typed doctor/pharmacist decision aggregates.
2. Add role-scoped APIs and a run-based episode workspace projection.
3. Enforce immutable ownership, signed-state locking, optimistic revision, and
   final selection readiness.
4. Keep legacy doctor-review endpoints compatible during the frontend cutover.
5. Enforce episode lock ownership on the full-aggregate update endpoint.
6. Add focused service/controller/contract tests and run repository validation.

## Risks And Recovery

- Existing review data may not resolve to a current user. Backfill it as
  legacy/read-only rather than assigning it to the first viewer.
- Permission rows must be added without broadening unrelated roles.
- Recovery is code rollback plus a forward migration that retires new writes;
  already-applied migrations are never edited or replayed.

## Progress

- [x] Inspect current run, review, lock, permission, and migration behavior.
- [x] Refresh GitNexus and review pre-change impact.
- [x] Add schema, model, repositories, and service rules.
- [x] Add APIs and compatibility behavior.
- [x] Add focused tests.
- [x] Validate and complete the plan.

## Decisions

- 2026-08-23: `AiRecommendationRun`, not a doctor review, is the version key.
- 2026-08-23: The run creator owns the doctor lane; the first eligible
  pharmacist to save claims the pharmacist lane. Reassignment is not implicit.
- 2026-08-23: Signed decisions are immutable; episode-final selection requires
  both lanes to be signed.
- 2026-08-23: Episode edit locks protect clinical aggregate writes, while
  professional decisions use owner plus optimistic revision rather than the
  coarse episode lock.

## Validation

- Focused proof: decision ownership/status/final-selection tests and episode
  lock enforcement tests.
- Integration proof: backend compile/test plus frontend API contract build.
- Repository-required checks: GitNexus detect-changes and `git diff --check`.

## Result

- Added V28, the `PHARMACIST` role/permissions, independent doctor and
  pharmacist draft/sign aggregates, optimistic revisions, and signed-run final
  selection.
- Added the clinical-decision workspace APIs and protected the legacy doctor
  review path with the same run-owner rule.
- Enforced Redis lock ownership on episode aggregate updates without coupling
  the professional decision lanes to that coarse lock.
- `./mvnw -DskipTests compile` passed. All 57 database-independent tests passed;
  the remaining application-context test requires the configured PostgreSQL
  service, which the sandbox cannot reach.
