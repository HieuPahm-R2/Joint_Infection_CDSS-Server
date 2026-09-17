# 0007 Transactional Recommendation Outbox

Date: 2026-09-17

## Status

Accepted

## Context

The async recommendation endpoint persisted its snapshot and run in PostgreSQL,
then published the job to RabbitMQ. A process or broker failure between those
operations could leave a durable `PROCESSING` run with no durable job. Treating
a transient publish failure as a terminal run failure also made broker recovery
require a new user request.

## Decision

Persist each async recommendation payload in `recommendation_job_outbox` in the
same transaction as its snapshot, diagnosis, and run. Returning `202 ACCEPTED`
means that this database transaction committed; it does not mean that RabbitMQ
has already received the job.

An in-process dispatcher locks one ready row with `FOR UPDATE SKIP LOCKED`,
publishes it with mandatory routing and publisher confirmation, and deletes the
row only after RabbitMQ acknowledges it. Failed publications remain durable and
retry indefinitely with exponential backoff capped at 60 seconds. Cancelled runs
have pending rows discarded instead of published.

Delivery is at-least-once. A crash after RabbitMQ acknowledges a message but
before the outbox deletion commits can publish the same `runId` and `requestId`
again. Consumers must therefore remain idempotent on those identifiers; this
decision does not claim exactly-once delivery.

The synchronous HTTP fallback does not use the outbox. Retrying a failed or
timed-out run creates a new immutable run from current clinical state and uses
the same async outbox path.

## Alternatives Considered

1. Publish after the database commit without an outbox. This retains the lost-job
   window between the commit and RabbitMQ publish.
2. Mark the outbox delivered before publishing. This avoids duplicates but can
   permanently lose a job after the mark and before the publish.
3. Stop after a fixed retry count. This requires a separately owned dead-letter
   and operator-recovery policy; no such workflow is currently accepted.

## Consequences

Positive:

- A committed async run always has a durable dispatch intent.
- Temporary RabbitMQ outages recover without changing the run to `FAILED`.
- `SKIP LOCKED` permits multiple backend replicas to dispatch without selecting
  the same ready row concurrently.

Tradeoffs:

- Duplicate recommendation jobs remain possible and are part of the contract.
- Publishing holds one short database transaction and row lock while awaiting
  the broker confirmation.
- The dispatcher is application-owned; prolonged broker outages leave pending
  rows in PostgreSQL until delivery succeeds or the run is cancelled.
