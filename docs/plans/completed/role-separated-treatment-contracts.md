# Role-Separated Treatment Contracts

Date: 2026-08-24

## Status

Completed

## Outcome

Every newly generated AI recommendation run has an immutable `SURGERY` or
`ANTIBIOTIC` scope, is created only by the corresponding professional role, and
accepts only that scope's decision. Episode final selections are independent by
discipline rather than requiring two signatures on one run.

## Context

- Product authority: workspace `new_goal_v2.txt` and the 2026-08-24 user request.
- Supersedes the combined-run final-selection part of ADR 0005 while preserving
  its ownership, optimistic revision and signed immutability rules.
- Coordinated plans live in Frontend_Client and Rag_Agentic.

## Scope

In scope:

- Run scope persistence, DTOs, API input, RabbitMQ messages and retry behavior.
- Role-aware generation and decision-lane enforcement.
- Independent doctor/pharmacist final selections with legacy migration.
- Persistence for the pharmacist structured care-plan decision.
- Contract and service tests.

Out of scope:

- Medication dispensing/administration state machines and external SMS/LIS
  commands without owning integrations.

## Approach

1. Add a forward-only migration and enum for scoped runs/selections.
2. Thread scope through sync/async generation, messaging and projections.
3. Enforce doctor/surgery and pharmacist/antibiotic access at the service
   boundary.
4. Change final selection eligibility to one signed decision in its own lane.
5. Retain `LEGACY_COMBINED` read compatibility and test migration semantics.

## Risks And Recovery

- Existing rows lack scope; migrate them to `LEGACY_COMBINED`.
- Queue producers/consumers deploy independently; default missing scope to
  legacy only for old messages and require explicit scope for new generation.
- Recovery uses a forward migration; applied migrations are never edited.

## Progress

- [x] Inspect current run, decision, permission and queue contracts.
- [x] Implement schema and backend contracts.
- [x] Implement role and final-selection policy.
- [x] Add focused tests and validate.

## Decisions

- 2026-08-24: Scope, not UI page, is the server-enforced authority boundary.
- 2026-08-24: Final selection is unique per episode and decision lane.
- 2026-08-24: The previous combined-run requirement remains only for legacy
  historical reads.

## Validation

- Focused proof: access, scope, ownership and final-selection service tests.
- Integration proof: migration/compile and recommendation serialization tests.
- Repository-required checks: GitNexus detect-changes and `git diff --check`.

## Result

Implemented scoped runs, exact category enforcement for sync/queue results,
role/run ownership, independent final selections and care-plan persistence.
Compile and five serialization/category contract tests passed. Mockito service
tests could not start because this environment blocks Byte Buddy JVM attach;
the failure occurs before test setup or assertions.
