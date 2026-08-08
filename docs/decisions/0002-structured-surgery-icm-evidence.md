# 0002 Structured Surgery ICM Evidence

Date: 2026-08-08

## Status

Accepted

## Context

Positive histology and intraoperative purulence contribute independently to the
ICM diagnostic score. They were previously inferred only from free-text surgery
findings, while single-positive-culture evidence already has an authoritative
structured source in culture-result rows.

## Decision

Store positive histology and intraoperative purulence as separate nullable
boolean columns on surgery. `true`, `false`, and `null` mean positive/present,
negative/absent, and not assessed respectively. Diagnostic scoring prioritizes
these structured values and falls back to legacy findings text only when no
structured value is available.

Keep single-positive-culture as a derived fact from culture-result rows; do not
duplicate it on surgery.

## Alternatives Considered

1. Keeping all evidence in findings text makes scoring depend on wording.
2. Putting histology in culture status mixes pathology and microbiology domains.
3. Duplicating single-positive-culture on surgery creates synchronization risk.

## Consequences

Positive:

- Explicit negative results remain distinct from missing assessments.
- New diagnoses no longer depend on keyword recognition.
- Existing records remain usable through text fallback.

Tradeoffs:

- Clients editing surgery must round-trip both nullable fields.
- Legacy findings are not automatically backfilled into structured columns.

## Follow-Up

- Consider a dedicated histology-result entity if numeric PMN/HPF details or
  multiple pathology samples become product requirements.
