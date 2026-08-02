# Execution Plan: QR Mobile Upload Session — Backend

Date: 2026-07-30

## Status

Completed

## Outcome

An authenticated doctor can create a five-minute upload session bound to one
patient and episode. A mobile browser can validate the unguessable capability,
upload up to ten clinical images directly to MinIO, complete the single-use
session, and trigger the existing OCR pipeline through RabbitMQ. The waiting
laptop receives the OCR job identifier over a doctor-scoped SSE stream.

## Context

- Product authority: workspace `new_goal.txt` plus the confirmed policy of ten
  session creations per five minutes per authenticated doctor with greedy refill.
- Existing integrations: `RedisCacheConfig`, `MinioChannel`, `RabbitMQConfig`,
  `ExtractImagesClient`, and the existing SSE controller pattern.
- Cross-repository consumers: `Frontend_Client` and `Extract_Images`.

## Scope

In scope:

- Redis-backed upload session and file metadata with a five-minute TTL.
- Hashed 256-bit capability token, constant-time comparison, atomic terminal
  transition, patient/episode ownership checks, and audit logs.
- Presigned MinIO PUT URLs with five-minute-or-less expiry and post-upload
  object verification.
- RabbitMQ upload-session event, backend consumer, OCR hand-off, and per-session SSE.
- Rate limiting at ten create requests per five minutes per authenticated user.
- Tests for expiry, reuse, patient mismatch, invalid type, invalid size, and
  object verification behavior.

Out of scope:

- PostgreSQL persistence for upload sessions.
- A new standalone upload microservice.
- Durable compliance storage beyond the existing structured application logs.

## Approach

1. Add the upload-session domain, Redis repository, service, controller, SSE
   relay, and API error contract.
2. Extend MinIO with private upload-bucket initialization, presigned PUT, stat,
   download metadata, and cleanup operations.
3. Add RabbitMQ topology and a consumer that hands verified objects to the
   existing extraction client.
4. Add the dedicated rate-limit rule and public capability endpoint security
   while keeping session creation JWT-protected.
5. Prove security transitions and validation rules with focused tests, then run
   the Maven test suite.

## Risks And Recovery

- Redis/Rabbit/HTTP cannot provide global exactly-once delivery. The session
  processing state prevents normal duplicates; a crash after upstream OCR
  acceptance but before Redis acknowledgement remains an observable retry risk.
- Presigned PUT cannot enforce content length before bytes reach MinIO. The API
  validates declared metadata before signing and the consumer validates actual
  object size/type before OCR, deleting invalid objects.
- Rollback is source/config removal only; Redis keys and uploaded objects expire
  or are cleaned by terminal processing.

## Progress

- [x] Product quota and HEIC decisions confirmed.
- [x] Existing architecture and blast radius inspected.
- [x] Implement backend feature.
- [x] Run focused and repository-wide validation.

## Decisions

- 2026-07-30: Use SSE because the repository already has authenticated
  fetch-stream infrastructure and the product explicitly allows SSE.
- 2026-07-30: Store only a SHA-256 token digest in Redis; raw tokens exist only
  in the QR payload and mobile tab memory.
- 2026-07-30: Include `episodeId` in session creation and validate it belongs to
  `patientId`; this preserves the “currently open episode” product invariant.
- 2026-07-30: Use the existing `pji.ai.exchange` with a dedicated routing key
  and queue; the consumer downloads verified MinIO objects and calls the
  existing OCR HTTP boundary.

## Validation

- `./mvnw -DskipTests compile`: passed.
- `./mvnw -Dtest=UploadSessionServiceTest test`: seven tests passed, covering
  expiry, reuse, wrong token, patient mismatch, invalid type, invalid size, and
  rejected stored-object metadata cleanup.
- `./mvnw test`: attempted; the new seven tests pass, but the repository-wide
  suite remains red on unrelated existing MapStruct generated-class errors,
  Mockito/ByteBuddy agent attachment in the container, and the application
  context's unavailable PostgreSQL connection.
- GitNexus change detection and targeted impact/context checks were run. The
  index did not include new files and mixed pre-existing worktree changes into
  its high-risk result, so compile, focused tests, and source review are the
  authoritative proof.

## Result

Implemented the Redis capability lifecycle, public mobile APIs, authenticated
doctor SSE, private MinIO direct upload and verification, RabbitMQ OCR hand-off,
delayed cleanup, audit logging, and the confirmed distributed rate limit. The
lasting contract is recorded in `docs/product/qr-mobile-upload.md` and
`docs/decisions/0001-qr-upload-capability.md`.

Known limitation: the SSE emitter registry is in-process. Horizontal backend
scaling requires a broadcast relay or session-aware routing before laptop
connections can land on arbitrary instances.
