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
- [x] Add care-team persistence and clinical record enforcement.
- [x] Add positive and negative proof.
- [ ] Run graph change analysis and repository validation.

## Decisions

- 2026-09-20: Use cumulative RBAC plus episode relationship checks; route
  permission alone never grants access to every clinical object.
- 2026-09-20: Preserve `ADMIN` as an audited break-glass compatibility role
  rather than silently changing existing administrator semantics.
- 2026-09-20: Treat Redis-backed session and refresh state as security state;
  absence or backend failure fails closed.
- 2026-10-15: Represent episode record access as persisted care-team membership
  (`episode_care_team`, migration V36) instead of inferring it from the
  free-text `created_by` email columns. Chosen over keeping the pharmacist
  exemption - that exemption is a system-wide privilege bypass - and over
  denying pharmacists outright, which would break the ANTIBIOTIC lane entirely.
- 2026-10-15: Records whose creator cannot be resolved stay admin-only, exactly
  as this plan already promised. V36 backfills every resolvable creator as the
  episode's OWNER; the remainder are reachable only through the care-team API.
- 2026-10-15: Only an ACTIVE administrator breaks glass. The break-glass flag is
  now derived from a validated principal, so a deactivated administrator loses
  the record-relationship bypass instead of keeping it by role-name match.

## Validation

- Focused proof: `CurrentActorProviderTest` (8), `EpisodeRecordAccessTest` (10),
  `RecommendationAccessServiceTest` (9), `ClinicalDecisionServiceImplTest` (2),
  `GlobalExceptionHandlerTest` (9) - allow and deny cases for the active
  principal, the care-team relationship, the blank-owner denial, the
  administrator break-glass, and both professional lanes.
- Migration proof: all 36 migrations applied cleanly to an empty PostgreSQL
  16.2 database created for the check. `episode_care_team` verified for columns,
  both unique indexes (`uq_episode_care_team_episode_user`, and
  `uq_episode_care_team_active_owner` partial on
  `active AND member_role = 'OWNER'`) and both foreign keys. The V36 backfill was
  replayed against fixtures: it enrolled exactly the resolvable creators, matched
  a padded mixed-case email, left `' '`, `''` and unresolvable creators
  admin-only, and was idempotent on replay. Both unique indexes were confirmed by
  an insert that raised `duplicate key value violates unique constraint`.
- Repository-required checks: `./mvnw.cmd test` - 138 tests, 0 failures,
  0 errors. `PjiApplicationTests.contextLoads` still requires a reachable
  PostgreSQL (`localhost:5433`), which this environment does not provide; that
  error predates this work.

## Result

Record-relationship authorization is implemented and proven. The identity/JWT
lifecycle and the route-permission/IAM hardening remain open; they are
independent of the record layer.

Deliberately **not** extended here: the episode-scoped clinical CRUD endpoints
(lab results, cultures, surgeries, clinical records, medical history, images,
patient and episode CRUD) still authorize per route permission rather than per
record. They never had a record check, so adding one before care-team data
exists everywhere would deny existing staff access to records whose creator
cannot be resolved. Broaden enforcement after care-team adoption, per this
plan's `Approach` step 3.
