# 0008 Deny-by-default functional and clinical-record authorization

Date: 2026-09-20

## Status

Accepted

## Context

The backend currently treats an authenticated JWT plus an endpoint permission as
sufficient access. Missing users or roles can pass the permission interceptor,
account and role status are not enforced, and most clinical CRUD endpoints do
not verify access to the requested patient or episode. The repository owner
authorized the agent on 2026-09-20 to choose and implement the general policy.

## Decision

Every protected request is denied unless it has an **Active Principal**: the
user exists, has `ACTIVE` status, has an active role, and presents an access
token bound to a live server-side session. Missing identity, role, status,
session or permission data is a denial, never an implicit grant.

Authorization has two cumulative layers:

1. A **Functional Permission** grants the ability to invoke an HTTP operation.
2. Access to a **Clinical Record** additionally requires ownership or explicit
   **Care Team Membership** for its episode. Professional workflow rules such
   as doctor/pharmacist lane and decision ownership remain mandatory.

The creator owns a new patient and is enrolled into the care team of each
episode they create. An active `ADMIN` is a **Break-glass Administrator** and
may bypass record relationships for recovery and administration, but the
bypass must emit a security-audit event. Owner metadata or membership that is
missing, stale or unresolvable denies non-admin access. Public capability
endpoints remain explicitly enumerated and must enforce their capability token.

Only an active `ADMIN` may administer users, roles or permissions. Permission
changes take effect immediately by invalidating authorization caches. Disabling,
deleting or changing the role/password of a user revokes all sessions.

Access and refresh tokens are distinct token types with separate signing keys,
issuer/audience validation and server-side refresh rotation. Refresh-token
reuse revokes the active session. Production has no fallback signing secret or
bootstrap administrator credential.

## Consequences

- Existing sessions are invalidated at rollout because legacy tokens do not
  carry the required token type and validation claims.
- Existing users must be backfilled to `ACTIVE` before status becomes required.
- Existing episodes receive care-team membership from resolvable creator audit
  metadata; unresolved records remain admin-only until explicitly assigned.
- `ADMIN` remains a compatibility break-glass role. A future IAM/clinical-admin
  split may narrow it without weakening the deny-by-default boundary.
