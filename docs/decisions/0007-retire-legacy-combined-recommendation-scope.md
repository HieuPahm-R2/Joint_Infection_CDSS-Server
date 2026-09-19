# 0007 Retire Legacy Combined Recommendation Scope

Date: 2026-09-17

## Status

Accepted

## Context

Decision 0006 retained LEGACY_COMBINED so data created before the role-scoped
cutover could remain readable. The system is still using development data, so
there is no clinical or audit-history requirement to retain that compatibility
branch.

## Decision

RecommendationScope has exactly two values: SURGERY and ANTIBIOTIC. All
combined recommendation runs and their dependent development data are deleted
by a forward migration. Recommendation, decision, selection, and care-plan
flows no longer accept or fall back to a combined scope.

This supersedes the legacy-read compatibility portions of decisions 0005 and
0006.

## Consequences

- The persisted scope contract and application control flow match the two
  role-owned clinical lanes exactly.
