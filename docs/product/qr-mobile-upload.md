# QR Mobile Upload Sessions

An authenticated doctor can create a short-lived upload capability for the
patient and episode currently open on the laptop. The capability lets a mobile
browser send clinical images directly to the private MinIO upload bucket without
logging in or proxying image bytes through the backend.

## Product Contract

- A session is bound to one `patientId`, one `episodeId`, and the authenticated
  doctor who created it.
- The capability expires after five minutes and can be completed only once.
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
- `GET /api/v1/upload-sessions/{sessionId}/validate?token=...` validates the
  mobile capability.
- `POST /api/v1/upload-sessions/{sessionId}/presigned-url` validates declared
  file metadata and returns a short-lived MinIO PUT URL.
- `POST /api/v1/upload-sessions/{sessionId}/complete` atomically consumes the
  capability and queues OCR processing.
- `GET /api/v1/upload-sessions/{sessionId}/events` requires the creating
  doctor's JWT and streams the terminal `uploaded` event.

## Security And Operations

Raw capability tokens contain 256 bits of randomness. Only their SHA-256 digest
is stored in Redis, and token comparison is constant-time. Redis owns the
five-minute expiry; PostgreSQL stores no upload-session record.

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
