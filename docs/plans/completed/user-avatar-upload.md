# Execution Plan: User Avatar Upload API

Date: 2026-08-14

## Status

Completed

## Outcome

Authenticated users can upload a validated avatar image and receive a fresh display URL from account APIs.

## Context

- `AuthController` owns `/api/v1/auth/account`.
- `MinioChannel` is the repository storage authority and requires callers to persist bucket plus object key.
- `UploadSessionProperties` establishes the existing 5 MB image-upload limit.

## Scope

In scope:

- Private MinIO avatar storage, image validation, persistence, replacement cleanup, account response URLs, and tests.

Out of scope:

- Image cropping and historical avatar retention.

## Approach

Add stable avatar storage metadata, a dedicated authenticated multipart endpoint, and resolve fresh presigned URLs on account reads.

## Risks And Recovery

- Prevent orphaned/replaced objects by deleting the new object on rollback and the previous object only after commit.
- Roll back by reverting the additive migration and avatar-specific code before deployment; after deployment leave additive nullable columns in place if code rollback is needed.

## Progress

- [x] Implement storage and API contract.
- [x] Add focused tests.
- [x] Validate build and affected flows.

## Decisions

- 2026-08-14: Reuse MinIO, the existing 5 MB image policy, private storage, and fresh presigned URLs; allow JPEG, PNG, and WEBP for browser avatar compatibility.

## Validation

- Focused proof: `UserAvatarServiceTest` passes validation, persistence, URL resolution, and after-commit cleanup checks.
- Integration or end-to-end proof: frontend consumes the endpoint; live MinIO/PostgreSQL validation remains dependent on local services.
- Repository-required checks: Maven compile passed; all tests except the DB-dependent application-context test passed; GitNexus change detection reports medium scope around auth.

## Result

Added a private MinIO-backed multipart account update, stable avatar object metadata, fresh URL resolution, replacement cleanup, and an additive Flyway migration. Full application-context validation remains unavailable without PostgreSQL.
