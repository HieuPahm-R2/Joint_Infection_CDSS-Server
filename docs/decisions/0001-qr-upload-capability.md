# 0001 QR Upload Capability Boundary

Date: 2026-07-30

## Status

Accepted

## Context

Doctors need to transfer clinical images from a phone to the episode open on a
laptop without authenticating again on the phone or using a third-party cloud.
The repository already provides Redis, MinIO, RabbitMQ, authenticated fetch-SSE,
and an OCR HTTP integration.

## Decision

Use a five-minute Redis capability bound to patient, episode, and doctor. Store
only a SHA-256 digest of a random 256-bit token. Mobile clients use the raw token
to request short-lived presigned MinIO PUT URLs and atomically complete the
session once. RabbitMQ triggers the existing OCR boundary; an authenticated
doctor-scoped SSE stream returns the resulting job to the laptop.

Limit creation to ten sessions per five minutes per authenticated doctor using
the existing distributed Bucket4j greedy-refill filter.

## Alternatives Considered

1. Requiring login on the phone adds friction and does not solve direct handoff.
2. Proxying image bytes through Spring increases server load and exposure.
3. Adding PostgreSQL persistence conflicts with the intentionally ephemeral
   capability lifecycle.
4. Adding STOMP/WebSocket duplicates an existing authenticated SSE pattern.

## Consequences

Positive:

- Image bytes travel directly from the phone to a private object bucket.
- Redis TTL and an atomic state transition enforce expiry and single use.
- The existing OCR and realtime client patterns remain the integration boundary.

Tradeoffs:

- Redis, RabbitMQ, and OCR do not form a global exactly-once transaction.
- The in-process SSE registry assumes one backend instance; a multi-instance
  deployment must add a broadcast relay or session-aware routing.

## Follow-Up

- Add a broadcast relay before horizontally scaling backend SSE instances.
- Keep the dedicated MinIO bucket private and browser-reachable through the
  configured public MinIO endpoint.
