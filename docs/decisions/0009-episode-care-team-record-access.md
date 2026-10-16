# 0009 Episode care team as the record-access grant

Date: 2026-10-15

## Status

Accepted

## Context

[Decision 0008](0008-general-authorization-policy.md) requires that access to a
clinical record need ownership or explicit **Care Team Membership** for its
episode. Only ownership existed, and it was inferred from the free-text
`pji_episodes.created_by` / `patients.created_by` email columns, which cannot
express a colleague's access and cannot be revoked.

Two defects followed from that gap:

1. `RecommendationAccessService.assertCanGenerateEpisode` returned early for the
   `PHARMACIST` branch, before the ownership check, so any pharmacist holding the
   route permission could generate an antibiotic recommendation against any
   patient's episode - while an administrator was checked more strictly than a
   pharmacist.
2. The ownership check granted access whenever the owner metadata was blank
   (`hasOwnerMetadata && !sameUser(...)` with no `else`).
   `model/AbstractEntity.handleBeforeCreate` writes the literal `" "` when there
   is no authenticated principal, so a record persisted outside a request was
   readable by every caller. The plan already stated such records "remain
   admin-only".

A pharmacist never creates an episode, so removing the exemption without
supplying a real grant mechanism would have denied the ANTIBIOTIC lane outright.

## Decision

Episode record access is a persisted, grantable, revocable membership:
`episode_care_team` (migration V36), unique on `(episode_id, user_id)`, with at
most one active `OWNER` per episode enforced by a partial unique index. Revoking
sets `active = false` so responsibility history survives.

- A pharmacist reaches an episode they did not create only through membership.
- The episode creator is enrolled as `OWNER` at creation, and V36 backfills every
  existing episode whose `created_by` resolves to a user account.
- Records whose creator does not resolve stay admin-only until an administrator
  assigns a member, as the plan promised.
- Access administration is limited to the episode's `OWNER` or an active
  administrator, exposed through `/episodes/{id}/care-team`. The last active
  owner cannot be revoked, so an episode is never left unadministrable.
- Only an **active** administrator breaks glass, and each use emits a
  `SECURITY_AUDIT` event. The administrator flag is derived from a validated
  principal (`services/auth/CurrentActorProvider`), not from a role-name string
  comparison, so a deactivated administrator no longer keeps the bypass.

## Alternatives Considered

1. Keep the pharmacist exemption and only add an audit event. Leaves a
   system-wide privilege bypass in place and does not satisfy Decision 0008.
2. Deny all non-owner access immediately. Closes the hole but disables the
   antibiotic lane until membership exists in every environment.
3. A single `assigned_pharmacist_id` column on the episode. Cheaper, but models
   one discipline only and cannot express a doctor, nurse or covering colleague.

## Consequences

Positive:

- Access to a clinical record is explicit, listable, revocable and auditable.
- The record-relationship decision lives in one place
  (`EpisodeRecordAccess`), so the agentic endpoints and the clinical-decision
  endpoints can no longer diverge.
- Blank or unresolvable ownership metadata denies non-administrators.

Tradeoffs:

- Existing episodes whose creator does not resolve to a user are admin-only until
  assigned. This is intended and was already promised, but it is a visible
  narrowing of current access.
- Membership must be granted before a colleague can work on an episode; the
  frontend does not yet surface the care-team API.
- `pji_episodes.created_by` remains as audit metadata and as a transitional
  ownership fallback for records written between the backfill and enrolment.

## Follow-Up

- Broaden record enforcement to the episode-scoped clinical CRUD endpoints
  (lab results, cultures, surgeries, clinical records, medical history, images)
  once care-team data exists across environments. They currently authorize per
  route permission only; adding a record check before the backfill is verified
  everywhere would deny staff access.
- Surface care-team assignment in the frontend.
