# 0005 Run-Scoped Clinical Decisions

Date: 2026-08-23

## Status

Accepted

## Context

AI recommendation runs contain surgery, systemic-antibiotic, and
local-antibiotic proposals. The former review model stored one doctor-owned
record per run and allowed that record to carry episode-final state. It did not
provide a durable pharmacist lane or decision-owner enforcement.

## Decision

Treat `AiRecommendationRun` as the immutable version boundary. Store doctor and
pharmacist decisions as separate one-per-run aggregates with explicit owner,
draft/signed status, optimistic revision, and signed timestamp.

The run creator owns the doctor lane. The first eligible pharmacist to write
claims the pharmacist lane. Only the owner may revise a draft, signed decisions
cannot be updated, and implicit ownership transfer is prohibited.

Store episode-final selection independently from either professional decision.
A run is eligible for final selection only when both decisions are signed.

## Consequences

- Doctor and pharmacist can work concurrently without updating the same row.
- Historical run decisions remain stable when a later run becomes final.
- Reassignment or signed-decision amendment requires a future explicit audited
  workflow rather than an ordinary update.
- Existing doctor-review endpoints require a compatibility period.
