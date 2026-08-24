# Separate antibiotic care planner

## Outcome

Backend recommendation validation accepts exactly the two regimen categories
for new `ANTIBIOTIC` runs. Care-plan generation is no longer part of the run
result contract.

## Contract

- `SURGERY`: `SURGERY_PROCEDURE`.
- `ANTIBIOTIC`: `SYSTEMIC_ANTIBIOTIC`, `LOCAL_ANTIBIOTIC`.
- `LEGACY_COMBINED`: unchanged.
- Existing stored care-plan items and decision JSON remain readable for
  backward compatibility; no destructive migration is performed.

## Work and proof

- [x] Updated `RecommendationScope.requiredItemCategories()` and contract tests.
- [x] Verified the shared result validation contract with its focused test.

## Validation

- `./mvnw -q -Dtest=RecommendationRunPersistenceContractTest test`
- `git diff --check`

## Recovery

Revert the backend change. No schema rollback is required.
