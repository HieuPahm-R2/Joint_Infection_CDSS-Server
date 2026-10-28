# QR Mobile Upload Sessions

An authenticated doctor can create a short-lived upload capability for the
patient and episode currently open on the laptop. The capability lets a mobile
browser send clinical images directly to the private MinIO upload bucket without
logging in or proxying image bytes through the backend.

## Product Contract

- A session is bound to one `patientId`, one `episodeId`, and the authenticated
  doctor who created it.
- The capability expires after five minutes and can be completed only once.
- The QR contains a one-time exchange code in a URL fragment, valid for at most
  60 seconds (and never beyond the session expiry). It is not an upload token.
- Mobile exchanges the code once and retains the issued UploadToken in memory
  only. Reloading or reopening a consumed QR requires a new QR from the laptop.
- A session accepts at most ten JPEG, PNG, HEIC, or HEIF images, each no larger
  than 5 MiB.
- Session creation is limited to ten requests per five minutes per
  authenticated doctor, with greedy refill.
- Completing a session verifies the stored object size, MIME type, and file
  signature before publishing the OCR job through RabbitMQ.
- The owning doctor's laptop receives the OCR job identifier and temporary
  image previews over an authenticated Server-Sent Events stream.
- Upload objects use a dedicated private bucket and are deleted through a
  delayed cleanup message after the OCR hand-off.

## HTTP Contract

- `POST /api/v1/patients/{patientId}/upload-sessions` requires JWT
  authentication and an `episodeId`.
- The create response includes `expiresAt` for the upload session and
  `exchangeCodeExpiresAt` for the QR countdown; `qrPayload` is
  `/m/upload/{sessionId}#code=...`, with no query string.
- `POST /api/v1/upload-sessions/{sessionId}/exchange` accepts `{ "code": "..." }`
  and returns `{ "uploadToken": "...", "expiresAt": "..." }` in the normal data
  envelope. Redis consumes the code atomically; replay/wrong code returns 401,
  expired code/session returns 410. Failed guesses do not consume the code.
- `GET /api/v1/upload-sessions/{sessionId}/validate` validates the mobile
  capability from `Authorization: Bearer <UploadToken>`.
- `POST /api/v1/upload-sessions/{sessionId}/presigned-url` validates declared
  file metadata and returns a short-lived MinIO PUT URL.
- `POST /api/v1/upload-sessions/{sessionId}/complete` atomically consumes the
  capability and queues OCR processing.
- Presign and complete also require the UploadToken in `Authorization`, never
  in a query parameter or JSON body. Presign JSON contains only file metadata;
  complete does not require a body. Legacy token query/body fallback is absent.
- `GET /api/v1/upload-sessions/{sessionId}/events` requires the creating
  doctor's JWT and streams the terminal `uploaded` event.

## Security And Operations

Exchange codes and UploadTokens are independently random 256-bit values. Only
their SHA-256 digests are stored in Redis; service comparison is constant-time.
The exchange Lua script checks status, code expiry against Redis time, and a
positive session TTL, installs the token digest and removes the code in one
operation. Exchange never extends the five-minute session expiry. Code alone
cannot upload. Concurrent exchange attempts have exactly one winner.

The mobile page reads the fragment into page memory and clears the URL before
the POST. Effect re-execution shares the original exchange promise; it never
retries an ambiguous exchange automatically. Tokens are cleared on unmount,
expiry, validation failure and successful completion; no localStorage,
sessionStorage or cookies store them. Script compromise on the page remains
outside the protection provided by memory-only credentials.

Capability HTTP responses use `Cache-Control: no-store` and `Referrer-Policy:
no-referrer`; the frontend document also sets a no-referrer meta policy. Public
fetches omit cookies, disallow redirects, and disable caching. Application code
does not log these credentials. Gateway/APM configuration must not opt into
recording exchange bodies, token responses or Authorization headers.

Opaque Bearer tokens bypass JWT decoding only for the four precise mobile
method/routes and are verified in UploadSessionService. They cannot authenticate
the doctor-only SSE endpoint or other account/clinical APIs. Session creation
continues to require the doctor's JWT and ordinary functional permissions.
PostgreSQL stores no upload-session record.

Deploy backend and frontend together. Old QR links fail closed in the new
mobile client. Pre-deploy upload capabilities already issued expire on their
existing TTL; this change does not erase previously collected access logs.

## Validation

- `./mvnw -Dtest=UploadSessionServiceTest,UploadCapabilitySecurityTest test`
  exercises service rules and the real Spring Security + MVC interceptor path.
- Start an isolated Redis and set `QR_TEST_REDIS_PORT` to its loopback port, then
  run `./mvnw -Dtest=RedisUploadSessionRepositoryIntegrationTest test` for actual
  Lua replay, expiry, TTL and 16-way concurrent exchange checks. These integration
  tests skip explicitly when the environment variable is absent.
- Frontend: `npm run test:qr-upload` and `npm run build`.

Production must set `UPLOAD_SESSION_PUBLIC_WEB_URL` to the browser-facing
frontend origin and `INTEGRATION_MINIO_PUBLIC_URL` to the browser-facing MinIO
origin. Both URLs must be reachable from the mobile browser; Docker-internal
hostnames are invalid for these values. `UPLOAD_SESSION_MINIO_BUCKET` may
override the dedicated bucket name.

For local phone testing, open Vite through the workstation LAN IP and set the
same `DEV_LAN_IP` in `Infras_Devops/.env`. Vite proxies same-origin `/api`
requests to the loopback-only backend, while the MinIO API binds to the selected
interface for presigned PUT requests. QR routes and presigned URLs use the
LAN-reachable address. MinIO remains loopback-only when `DEV_LAN_IP` is absent.
