# Connect antibiotic care-plan workspace

## Outcome

Expose an authenticated stateless backend endpoint that builds the episode
snapshot, reads a signed pharmacist decision, calls the RAG care-plan API, and
returns the generated plan without persistence.

## Contract

- The run must belong to the requested episode and have a signed pharmacist
  decision with both antibiotic plans.
- No repository save method is called.
- Existing episode/run access rules remain authoritative.

## Work and proof

- [x] Add RAG request/response DTOs and client call.
- [x] Add care-plan application service and controller endpoint.
- [x] Add focused tests proving validation and absence of persistence.

## Validation

- `./mvnw -q -Dtest=AntibioticCarePlanServiceImplTest,RecommendationRunPersistenceContractTest test`
- `git diff --check`

## Recovery

Revert the additive endpoint, DTOs, and service. No schema rollback is needed.
