# QR upload one-time exchange

Authority: owner request on 2026-10-28 to implement one-time QR exchange with
memory-only UploadToken. Extends ADR 0001 without changing clinical scope,
five-minute session expiry, quotas, OCR, or doctor-authenticated SSE.

## Contract

- QR contains sessionId and a random 256-bit exchange code in `#code=...` only.
- Code expires after at most 60 seconds, is stored as a hash, and is consumed
  atomically in Redis. Failed guesses do not consume it or extend the session.
- POST exchange returns an independently random UploadToken, stored only as a
  digest server-side and in mobile memory. Subsequent APIs use a Bearer header.
- Legacy token query parameters cannot authenticate requests. Reload after a
  successful exchange requires a new QR; no browser storage or automatic replay.
- Responses containing capabilities are not cacheable. The mobile document
  suppresses referrers. No secrets are added to application logs.

## Scope and impact

Backend repository owns Redis atomic exchange, service, HTTP and security
routing, tests, ADR and product contract. Frontend_Client owns QR consumption,
memory lifetime and HTTP client; its companion plan tracks that work.
Deploy both repositories together. Preserve unrelated local modifications.
GitNexus backend impact is incomplete (31 commits stale, DI boundaries);
frontend index is unavailable. Source callers were inspected as fallback.

## Validation and progress

- [x] Demonstrated existing QR-query regression failure before the fix.
- [x] Implemented backend and frontend contracts, ADR and product documentation.
- [x] Verified exchange, replay, wrong/expired code, session binding and concurrency.
- [x] Verified actual Spring Security + permission interceptor routing, no query or
  body credential fallback, frontend request and handoff lifecycle tests.
- [x] Focused backend tests and frontend build passed; diff reviewed.

### Observed validation — 2026-10-28

- 28 backend tests passed, none skipped: 13 UploadSessionServiceTest,
  3 UploadCapabilitySecurityTest, 3 RedisUploadSessionRepositoryIntegrationTest,
  and 9 GlobalExceptionHandlerTest. Redis ran in a task-owned isolated Podman
  container, using string serializers as production does. The 16-way exchange
  race had one winner; replay did not replace its token and TTL did not grow.
- Regression test was red on the old query URL, then green on the fragment QR.
- Frontend `npm run test:qr-upload`: 5 passed; `npm run build`: passed.
- `git diff --check`: passed for both repositories.
- Standard Maven test compilation is blocked by a pre-existing unrelated error:
  UserAvatarServiceTest accesses package-private MAX_AVATAR_SIZE_BYTES.
  Focused validation used a temporary POM outside the repository with the same
  production sources/dependencies and only the four test classes above included.
- Frontend type-check has existing errors in NotificationBell,
  AntibiogramAiVersionTab, PatientModal and ModalRole; none in changed QR files.
- No live deployed environment, full mobile-to-MinIO/OCR run, CI result or branch
  protection was verified. Existing build warnings concern missing image paths
  and large chunks. No hooks, pipeline settings or branch protection changed.

Recovery: revert this task's changes in both repositories together. In-flight
old QR links are intentionally unsupported by the updated mobile page; generate
a new QR after deployment. Never restore another task's unrelated changes.
