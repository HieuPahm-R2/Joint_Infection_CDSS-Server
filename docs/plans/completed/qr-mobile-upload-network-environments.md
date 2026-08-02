# Execution Plan: QR Mobile Upload Network Environments

Date: 2026-08-01

## Status

Completed

## Outcome

Development QR sessions default to the frontend mobile route, while production
continues to require an explicit browser-facing frontend origin.

## Context

- `docs/product/qr-mobile-upload.md`
- `src/main/resources/application.yaml`
- `src/main/resources/application-dev.yml`
- `src/main/java/com/vietnam/pji/services/upload/UploadSessionService.java`

## Scope

In scope:

- Add a safe development default for the public frontend URL.
- Document the public frontend and MinIO URL contract.

Out of scope:

- Changing upload-session lifetime, quota, or capability security.

## Approach

Keep the generic configuration unset, add a dev-profile localhost frontend
default, and let infrastructure override it with a LAN address or production
domain.

## Risks And Recovery

- A development default could leak into production; it lives only in the `dev`
  profile and production Compose requires an explicit value.
- Remove the dev override to recover the prior request-derived behavior.

## Progress

- [x] Add the dev-profile public frontend URL.
- [x] Update the operations contract.
- [x] Run focused upload-session tests and compile.

## Decisions

- 2026-08-01: The mobile route belongs to the frontend on port 5173 in local
  development; the existing product document requires an explicit production
  origin.

## Validation

- Focused proof: `UploadSessionServiceTest` passed 7 tests.
- Integration or end-to-end proof: configuration composition with local and
  production infrastructure.
- Repository-required checks: Maven compiled main and test code successfully as
  part of the focused test run.

## Result

The dev profile now targets the frontend mobile route at localhost by default,
with an environment override for LAN testing. The product contract documents
that production frontend and MinIO URLs must both be browser-reachable.
