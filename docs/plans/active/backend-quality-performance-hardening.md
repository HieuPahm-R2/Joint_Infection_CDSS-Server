# Execution Plan: Backend Quality, Performance, And Scalability Hardening

Date: 2026-09-16

## Status

Active

## Outcome

Improve demonstrable maintainability, performance, and scalability properties
of the Backend_Server without changing the documented REST or messaging
contracts.

## Context

- `README.md`: application responsibilities and asynchronous recommendation flow.
- `docs/WORKFLOW.md`: validation and authority requirements.
- `pom.xml`, source code, and tests: executable implementation and proof.
- GitNexus CLI query was attempted on 2026-09-16. Its index is two commits
  behind HEAD and its FTS extension cannot load in this environment; source
  inspection is the approved fallback.

## Scope

In scope:

- Evidence-backed defects and high-leverage refactors with compatible external
  behavior.
- Focused tests or other executable proof for each behavior change.
- Database-access, transaction, and asynchronous-processing improvements when
  their contract is evident in code and tests.

Out of scope:

- New clinical policy, API contract changes, schema redesign, and infrastructure
  topology changes without explicit authority.
- Rebuilding the GitNexus index or altering developer-machine dependencies.

## Approach

1. Map the source and tests; audit architecture and runtime paths independently.
2. Prioritize concrete findings by correctness, leverage, and compatibility risk.
3. Perform the smallest cohesive repairs, starting with low-risk changes.
4. Run focused tests, then the repository test suite; inspect graph changes when
   the available index supports it.

## Risks And Recovery

- A refactor can alter persistence, transaction, or message-delivery semantics.
  Preserve public contracts and add regression proof before merging.
- Revert only the affected change if validation fails; do not reset unrelated
  work.

## Progress

- [x] Read repository workflow, product overview, and design guidance.
- [x] Check clean working tree and attempt GitNexus exploration.
- [x] Complete architecture and runtime audits.
- [x] Apply and validate low-risk performance and maintainability improvements.
- [x] Run repository tests and record results.
- [x] Make recommendation-run creation atomic per episode and persist publish failures.
- [x] Separate AI chat database transactions from external AI I/O.
- [x] Defer notification SSE fan-out until its database transaction commits.
- [x] Persist recommendation jobs with their runs and dispatch them at-least-once
  through a transactional outbox.
- [ ] Choose the deployment and input-limit policies needed for the remaining
  P1 work.

## Decisions

- 2026-09-16: Limit changes to source-supported, backwards-compatible
  improvements; request direction for product-policy or public-contract choices.
- 2026-09-16: Use one batched sensitivity query per episode and preserve the
  aggregate response's empty lists for cultures without sensitivities.
- 2026-09-16: Replace Redis `KEYS` permission-cache invalidation with cursor
  scanning and bounded deletes; this avoids blocking Redis or materializing the
  whole keyspace.
- 2026-09-16: Keep clinical-decision tests aligned with accepted scoped-run
  semantics (Decision 0006) and the legacy compatibility branch (Decision 0005).
- 2026-09-16: Serialize recommendation snapshot/run numbering by locking the
  episode row in `RecommendationRunCreator`; commit durable input before AI or
  RabbitMQ I/O.
- 2026-09-16: Preserve chat failure semantics (no messages persisted when AI
  fails) while using separate short prepare/write transactions around the AI
  HTTP call.
- 2026-09-16: Defer notification SSE fan-out until `afterCommit`; an SSE event
  must never advertise a notification row that can still roll back.
- 2026-09-17: A `202` async recommendation response means the snapshot, run,
  diagnosis, and outbox payload committed atomically. Dispatch retries
  indefinitely with bounded exponential backoff and RabbitMQ confirmation;
  duplicates remain possible and consumers stay idempotent by run/request ID.

## Validation

- Focused proof: `RedisServiceImplTest`, `EpisodeAggregateServiceImplTest`, and
  `ClinicalDecisionServiceImplTest` passed after a clean compilation; P0
  regression tests cover recommendation-creation locking, broker publish
  failure, and chat persistence ordering. Outbox tests cover atomic payload
  creation, confirmed delivery, durable retry after publish failure, cancellation,
  async retry routing, and terminal-result idempotency.
- Integration or end-to-end proof: existing Spring test coverage where available.
- Repository-required checks: `./mvnw.cmd test` passed: 92 tests, 0 failures,
  0 errors, 0 skipped (2026-09-17). The Spring context applied the outbox
  migration and successfully polled it with `FOR UPDATE SKIP LOCKED`.

## Result

Implemented and verified the source-supported P0 subset plus commit-safe
notification fan-out. Recommendation jobs now use the accepted transactional
outbox and at-least-once retry semantics. The following findings still need an
owner decision before they can be safely changed: cross-replica SSE routing and
capacity policy; extract-images file/aggregate limits or object-storage hand-off;
maximum page sizes and snapshot-retention policy; and production secret/default
handling. Keep this plan active only after that direction is available.
