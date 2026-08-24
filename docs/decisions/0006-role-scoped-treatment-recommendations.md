# 0006 Role-Scoped Treatment Recommendations

Date: 2026-08-24

## Status

Accepted

## Context

A combined AI run exposed surgery and antibiotic plans in one workflow even
though doctors and pharmacists own different clinical decisions. Requiring both
signatures on the same run made version ownership unclear and allowed one
discipline's work to be coupled to the other.

## Decision

Every new recommendation run has one immutable scope: `SURGERY` or
`ANTIBIOTIC`. Surgery runs contain only a surgical item and accept only a doctor
decision. Antibiotic runs contain systemic, local and structured care-plan items
and accept only a pharmacist decision.

The professional who creates the run owns its decision. Drafts are protected by
optimistic revision and signed decisions remain immutable. Episode-final
selection is unique per scope, so a signed doctor decision and a signed
pharmacist decision may be selected independently.

Existing unscoped data is classified as `LEGACY_COMBINED` and retains the
two-signature rules from decision 0005 for read compatibility. New combined
runs are prohibited.

## Alternatives Considered

1. Keep one combined run and add a second final step. This preserves coupling
   and leaves role/version ownership ambiguous.
2. Generate all categories and hide unrelated items in the UI. This does not
   enforce the policy at the API, queue or persistence boundaries.

## Consequences

Positive:

- Each role sees and owns only its clinical lane.
- AI output categories are validated against run scope before persistence.
- Doctor and pharmacist final versions can evolve independently.

Tradeoffs:

- Legacy combined runs require a permanent compatibility branch.
- Consumers and producers must deploy the scope contract together.

## Follow-Up

- Add real dispensing, administration, LIS or notification integrations only
  behind separately authorized workflows; the care plan remains advisory.
