# Execution Plan: Remove AI–Doctor Comparison Backend

Date: 2026-08-11

## Status

Completed

## Outcome

The backend no longer accepts, exposes, computes, or serves data used only to
compare AI recommendations with doctors' conclusions. Doctor reviews, doctor
diagnoses, and final-decision selection continue to work.

## Context

- `src/main/java/com/vietnam/pji/model/agentic/DoctorRecommendationReview.java`
- `src/main/java/com/vietnam/pji/dto/request/DoctorRecommendationReviewRequestDTO.java`
- `src/main/java/com/vietnam/pji/services/doctor/DoctorRecommendationReviewService.java`
- `src/main/java/com/vietnam/pji/services/doctor/impl/DoctorRecommendationReviewServiceImpl.java`
- `src/main/java/com/vietnam/pji/controller/agentic/DoctorRecommendationReviewController.java`
- Coordinated consumer removal in `Frontend_Client`.

## Scope

In scope:

- Remove the review agreement model/request field and persistence write.
- Remove comparison statistics DTO, service method, and endpoint.
- Drop the obsolete `agreement_json` database column through a new Flyway
  migration.
- Preserve review, diagnosis, and final-decision behavior.

Out of scope:

- Rewriting the already-applied V12 Flyway migration.
- Removing AI recommendation or doctor-review workflows.

## Approach

Remove the comparison-only contract from model through controller. Add a new,
short V23 migration that drops the now-unreferenced column, then compile and
test the backend against the migrated schema.

## Risks And Recovery

- Risk: accidentally removing doctor-review behavior shared by final decisions.
  Mitigation: retain all review CRUD and final-decision methods and run the test
  suite.
- Risk: dropping the column permanently deletes historical agreement payloads.
  This destructive cleanup was explicitly authorized by the user.
- Recovery: restore the database from a pre-migration backup if the removed
  historical payloads are needed; reverting application code cannot restore
  dropped values.

## Progress

- [x] Map comparison symbols and callers with GitNexus and source search.
- [x] Remove backend comparison contracts and computation.
- [x] Add and apply the column-drop migration.
- [x] Re-run focused and repository checks.

## Decisions

- 2026-08-11: The user explicitly superseded the earlier retention decision and
  authorized dropping the legacy column and its data.

## Validation

- Focused proof: runtime source search finds no comparison DTO, service method,
  endpoint, model/request field, or agreement computation. V12 retains the
  historical column addition and V23 explicitly removes it.
- Integration or end-to-end proof: `./mvnw test` passed all 31 tests. The
  application-context test validated 23 migrations and Flyway successfully
  migrated the dev schema from V22 to V23 in 42 ms.
- Repository-required checks: `git diff --check` passed; GitNexus change
  detection reported low risk and no affected execution process.

## Result

Removed the comparison-only model/request contract, persistence write,
statistics DTO/service implementation, and controller endpoint. V23 drops the
legacy `agreement_json` column; it has been applied successfully to the dev
database, so its historical values there are no longer recoverable without a
backup. Doctor review, diagnosis, and final-decision behavior remains compiled
and tested.
