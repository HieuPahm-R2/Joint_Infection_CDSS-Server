# PJI Backend Server

> Spring Boot backend for the **PJI (Prosthetic Joint Infection) Clinical Decision Support System** — REST API, clinical state management, authentication, and orchestration of the asynchronous AI recommendation pipeline.

This service is the **source of truth** for the frontend API contract, authentication/authorization rules, persisted clinical data, and RabbitMQ result handling. It is one component of the larger `pog` workspace (Frontend, RAG/Agentic AI, Image Extraction, Infra).

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Framework | Spring Boot 3.5 (Java 17) |
| Database | PostgreSQL + Flyway migrations |
| ORM | Spring Data JPA / Hibernate |
| Cache & Rate limiting | Redis (Jedis) + Bucket4j |
| Messaging | RabbitMQ (async AI recommendations) |
| Object storage | MinIO |
| Security | Spring Security + OAuth2 Resource Server + JWT |
| API docs | SpringDoc OpenAPI (Swagger UI) |
| Observability | Actuator, Micrometer, Prometheus, OpenTelemetry (OTLP) |
| Build | Maven (wrapper included) |

---

## Prerequisites

- **JDK 17** (Eclipse Temurin recommended)
- **Maven** — not required, the project ships the `./mvnw` wrapper
- **Docker** (recommended) — to run the backing services: PostgreSQL, Redis, RabbitMQ, MinIO

> The Maven wrapper downloads its own Maven, so you only need a JDK installed locally.

---

## Quick Start (local development)

The app runs with the **`dev`** profile by default. The `dev` profile provides sensible fallback defaults for every connection, so the only hard requirement is that the backing services (PostgreSQL, Redis, RabbitMQ, MinIO) are reachable.

### 1. Start backing services

Backing services are defined in the `Infras_Devops` component. From the workspace root:

```bash
docker compose -f Infras_Devops/docker-buildlocal.yml up -d
```

The `dev` defaults expect:

| Service | Host:Port (dev default) |
|---------|--------------------------|
| PostgreSQL | `localhost:5433`, db `pji_dev`, user `user` / `123456` |
| Redis | `localhost:6379` |
| RabbitMQ | `localhost:5672`, user `admin` / `admin2026` |
| MinIO | `localhost:9000`, key `admin` / `admin2026` |

### 2. Run the application

```bash
cd Backend_Server
./mvnw spring-boot:run
```

The API starts on **http://localhost:8085**.

- Swagger UI: **http://localhost:8085/swagger-ui.html** (enabled in `dev` only)
- Health: **http://localhost:8085/actuator/health**
- API base path: **`/api/v1`**

On first startup Flyway applies all migrations and a bootstrap admin is created (see [Bootstrap admin](#bootstrap-admin)).

### Startup and first-login effects

Starting the application with the documented Maven command is stateful. Flyway can apply database migrations; startup runners can insert endpoint permissions, add ADMIN grants, evict Redis permission-cache entries, and create bootstrap permission, role, and user records when their code paths apply.

A login without a valid trusted-device cookie returns a device-verification challenge instead of tokens. That branch writes cooldown, OTP, and challenge entries to Redis and attempts to send the OTP through configured SMTP; `POST /api/v1/auth/verify-device` verifies the OTP, persists trusted-device state, and issues tokens.

---

## Configuration

Configuration lives in `src/main/resources/`:

- `application.yaml` — base config common to all profiles
- `application-dev.yml` — local development overrides (active by default)
- `application-prod.yml` — production overrides; base fallback values still apply unless this profile overrides them

Select a profile via `SPRING_PROFILES_ACTIVE` (`dev` | `prod`).

### Key environment variables

In `dev`, all of these have working defaults — override only what you need. In `prod`, effective values come from both base and production profile configuration.

| Variable | Purpose | Dev default |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | Active profile | `dev` |
| `SERVER_PORT` | HTTP port | `8085` |
| `SPRING_DATASOURCE_URL` | JDBC URL | `jdbc:postgresql://localhost:5433/pji_dev` |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | DB credentials | `user` / `123456` |
| `REDIS_HOST` / `REDIS_PORT` | Redis | `localhost` / `6379` |
| `RABBITMQ_HOST` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | RabbitMQ | `localhost` / `admin` / `admin2026` |
| `INTEGRATION_MINIO_URL` / `_ACCESS_KEY` / `_SECRET_KEY` | MinIO | `http://localhost:9000` / `admin` / `admin2026` |
| `JWT_BASE64_SECRET` | JWT signing key (base64) | dev-only key |
| `APP_CORS_ALLOWED_ORIGINS` | Allowed CORS origins (CSV) | localhost dev ports |
| `AI_SERVICE_URL` | RAG/Agentic service base URL | `http://localhost:8000` |
| `EXTRACT_IMAGES_URL` | Image extraction service URL | `http://localhost:8002` |
| `MAIL_HOST` / `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP for OTP/recovery email | Gmail SMTP |
| `PASSWORD_RECOVERY_REQUEST_COOLDOWN_SECONDS` / `DEVICE_VERIFICATION_REQUEST_COOLDOWN_SECONDS` | Redis-backed per-email cooldown before another OTP email can be sent | `60` / `60` |
| `CAPTCHA_ENABLED` / `CAPTCHA_PROVIDER` / `CAPTCHA_SECRET_KEY` | CAPTCHA verification for password recovery OTP requests (`turnstile` or `recaptcha`) | disabled / `turnstile` / empty |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OpenTelemetry traces endpoint | `http://localhost:4318/v1/traces` |

> **Tip:** Do not commit secrets. For local development, set sensitive values (mail password, AI keys) through your IDE run configuration (e.g. VS Code `launch.json` env) or an uncommitted `.env`.

### Bootstrap admin

On startup the app creates an initial admin account (toggle with `BOOTSTRAP_ADMIN_ENABLED`):

- Email: `BOOTSTRAP_ADMIN_EMAIL` (default `admin@example.com`)
- Password: `BOOTSTRAP_ADMIN_PASSWORD` (default `123456`)

Change these for any non-local environment.

---

## Build & Test

```bash
# Run the unit/integration tests
./mvnw test

# Build the runnable jar (skip tests)
./mvnw -B -DskipTests clean package

# Run the packaged jar
java -jar target/pji-0.0.1-SNAPSHOT.jar
```

---

## Run with Docker

The project includes a multi-stage `Dockerfile` (build + JRE runtime).

```bash
# Build the image
docker build -t pji-backend .

# Run it (provide env for whichever profile you target)
docker run -p 8085:8085 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/pji \
  -e SPRING_DATASOURCE_USERNAME=... \
  -e SPRING_DATASOURCE_PASSWORD=... \
  -e RABBITMQ_HOST=... -e REDIS_HOST=... \
  -e INTEGRATION_MINIO_URL=... \
  -e JWT_BASE64_SECRET=... \
  pji-backend
```

The image exposes port `8085` and respects `JAVA_OPTS`. For full-stack deployment (with PostgreSQL, Redis, RabbitMQ, MinIO wired up), use the compose/Kubernetes manifests in `Infras_Devops`.

---

## Database Migrations

Schema is owned by **Flyway**, not Hibernate (`ddl-auto: validate`). Migrations live in:

```
src/main/resources/db/migration/   # V1__baseline.sql, V2__..., ...
```

Migrations run automatically on application startup. To add a schema change, create a new `V{n}__description.sql` file — never edit an applied migration.

---

## API Surface

All routes are served under the **`/api/v1`** prefix. Controllers are organised by domain:

| Area | Endpoints |
|------|-----------|
| **Auth** | login / refresh / logout, password recovery, device verification, users, roles, permissions |
| **Medical** | patients, episodes (+ locking), clinical records, medical history, surgeries, lab/culture/sensitivity/image results, MinIO upload, image extraction |
| **Agentic (AI)** | AI recommendations (generate + run status), recommendation streaming, doctor recommendation review, AI chat, pending lab tasks |
| **Notification** | notifications + SSE notification stream |

Interactive docs are available at `/swagger-ui.html` when running the `dev` profile.

### AI Recommendation flow (async)

1. Frontend calls `POST /api/v1/episodes/{id}/ai-recommendations/generate`.
2. Backend validates auth + clinical state and atomically stores the snapshot,
   run, diagnosis, and outbox job before returning **`202 ACCEPTED`**.
3. The outbox dispatcher publishes the job at-least-once to RabbitMQ
   (`pji.ai.exchange` → `ai.recommendation.generate`) with confirmation and
   retries broker failures.
4. The `Rag_Agentic` service consumes it, runs completeness checks + multi-agent RAG, and publishes the result back.
5. Backend consumes the result (`ai.recommendation.result`), updates run status to `SUCCESS` / `PARTIAL` / `FAILED`, and persists recommendation items + citations.
6. Frontend polls `GET /api/v1/ai-recommendations/runs/{runId}` for status and renders the result.

---

## Observability

Actuator endpoints exposed: `health`, `info`, `prometheus`, `metrics`.

- Health: `GET /actuator/health`
- Prometheus scrape: `GET /actuator/prometheus`
- Traces are exported via OTLP (configurable with `OTEL_EXPORTER_OTLP_ENDPOINT`, toggle with `OTEL_TRACING_ENABLED`).

---

## Rate Limiting

Bucket4j-backed rate limiting (Redis) is enabled by default (`RATE_LIMIT_ENABLED=true`):

- **auth-email** routes (`forgot-password`, `verify-device`): 3 req/10 min (per IP)
- **auth** routes (`login`, `refresh`, `reset-password`): 5 req/min (per IP)
- **AI** routes (`ai-recommendations`, `ai-chat`): 30 req/min per user
- **default** `/api/v1/**`: 200 req/min per user

Password recovery CAPTCHA is off in the dev profile by default. In production it is enabled unless `CAPTCHA_ENABLED=false`; set `CAPTCHA_SECRET_KEY` on the backend and `VITE_TURNSTILE_SITE_KEY` when building the frontend image.

---

## Project Layout

```
src/main/java/com/vietnam/pji/
├── PjiApplication.java     # Main entry point
├── config/                 # Security, JWT, CORS, integrations, properties
├── controller/             # REST endpoints (auth, medical, agentic, notification)
├── dto/                    # Request/response DTOs
├── exception/              # Global exception handling
├── message/                # RabbitMQ producers/consumers + payloads
├── model/                  # JPA entities
├── repository/             # Spring Data repositories
├── security/               # Auth filters, token handling
├── services/               # Business logic
└── utils/                  # Shared helpers
src/main/resources/
├── application*.yml        # Profile config
└── db/migration/           # Flyway SQL migrations
```

See `docs/backend-architecture.md` for a deeper architectural breakdown.

---

## Troubleshooting

| Symptom | Likely cause / fix |
|---------|--------------------|
| App fails at startup with connection errors | Backing services not running — start `Infras_Devops/docker-buildlocal.yml` |
| `Flyway validate` failure | A migration was edited after being applied — add a new migration instead |
| `401` on every request | Missing/expired JWT, or wrong `JWT_BASE64_SECRET` between issuer and verifier |
| CORS errors in browser | Add the frontend origin to `APP_CORS_ALLOWED_ORIGINS` |
| AI recommendations never complete | `Rag_Agentic` not running, or RabbitMQ unreachable / wrong credentials |
| Swagger UI 404 in prod | Disabled by design in the `prod` profile |
