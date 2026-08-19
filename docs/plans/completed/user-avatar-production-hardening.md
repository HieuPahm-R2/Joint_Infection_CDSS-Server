# Execution Plan: User Avatar Production Hardening

Date: 2026-08-14

## Status

Completed

## Outcome

Account updates do not trigger lazy-role 500 errors, and avatar storage failures produce stable, production-safe responses.

## Scope

- Initialize role permissions before account-update transactions close.
- Make the avatar bucket environment-configurable and enforce the product's 5 MB limit.
- Isolate MinIO failures as HTTP 503 and malformed multipart requests as HTTP 400/413.
- Keep account reads available when presigned URL generation is temporarily unavailable.

## Progress

- [x] Trace the failing update and production deployment path.
- [x] Implement backend hardening and focused tests.
- [x] Validate backend and production compose configuration.

## Recovery

All changes are additive or application-level. Roll back the application/config changes without reverting the nullable avatar columns.

## Validation

- Clean focused tests passed for account updates, avatar storage failures, URL fallback, and 400/413/503 error payloads.
- All backend tests except the PostgreSQL-dependent application-context test passed.
- Maven compile and `git diff --check` passed.
- GitNexus reports medium change scope around auth and the existing MinIO upload path; the legacy upload contract remains covered by the repository test suite.

## Result

Role permissions are initialized before account-update transactions close. Avatar uploads use a configurable private bucket, return 503 for storage outages, return 413 for oversized files, and do not fail account reads when public URL signing is unavailable.
