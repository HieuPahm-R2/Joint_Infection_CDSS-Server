# Execution Plan: Authorization Hardening

Date: 2026-09-20

## Status

Active

## Outcome

Protected APIs fail closed for invalid identities, roles, sessions and route
permissions; access and refresh tokens cannot be confused; permission and
session revocation is immediate; and clinical records require an owner or care
team relationship, with audited ADMIN break-glass access.

## Context

- Authority: `docs/decisions/0008-general-authorization-policy.md`
- Vocabulary: `CONTEXT.md`
- Existing role-scoped clinical authority:
  `docs/decisions/0006-role-scoped-treatment-recommendations.md`
- Workflow: `docs/WORKFLOW.md`
- Validation pattern: `docs/patterns/encoding-invariants.md`

## Scope

In scope:

- Fail-closed account, role, session and route authorization.
- Typed, issuer/audience-bound access and refresh JWTs with separate keys.
- Atomic refresh rotation and reuse revocation in Redis.
- Immediate cache/session invalidation on security-sensitive mutations.
- Episode care-team persistence, owner backfill and clinical object checks.
- Production-safe security defaults, audit events and positive/negative tests.

Out of scope:

- External identity-provider adoption.
- Changing deployment secrets or branch-protection settings outside this repo.
- Multi-hospital tenancy, because the current domain has no hospital/tenant
  authority or persisted tenant boundary.

## Approach

1. Encode identity/session/token invariants and focused tests.
2. Encode route/IAM invariants and immediate cache invalidation.
3. Add episode care-team ownership, migration, APIs and scoped clinical access.
4. Add security audit signals and production configuration guards.
5. Run focused, integration and full repository validation, then inspect graph
   changes and move this plan to completed only when all proof passes.

## Risks And Recovery

- Legacy tokens will fail validation; deployment must intentionally force a
  fresh login. Rollback restores the prior application but not old Redis token
  state.
- Enforcing ACTIVE can lock out legacy users; migration backfills non-deleted
  users before adding the constraint.
- Owner backfill may not resolve every historical creator. Such records remain
  admin-only and can be assigned through the care-team API.
- Existing uncommitted AI-service changes belong to the user and will not be
  overwritten; authorization work avoids those files unless required.

## Progress

- [x] Record accepted authority and vocabulary.
- [ ] Harden identity, JWT and session lifecycle.
- [ ] Harden route permissions and IAM administration.
- [ ] Add care-team persistence and clinical record enforcement.
- [ ] Add positive and negative proof.
- [ ] Run graph change analysis and repository validation.

## Decisions

- 2026-09-20: Use cumulative RBAC plus episode relationship checks; route
  permission alone never grants access to every clinical object.
- 2026-09-20: Preserve `ADMIN` as an audited break-glass compatibility role
  rather than silently changing existing administrator semantics.
- 2026-09-20: Treat Redis-backed session and refresh state as security state;
  absence or backend failure fails closed.

## Validation

- Focused proof: security unit tests for allow and deny cases.
- Integration proof: MockMvc authentication/authorization matrix and Flyway
  migration validation.
- Repository-required checks: Maven test suite and GitNexus detect-changes.

## Result

Pending implementation and validation.
