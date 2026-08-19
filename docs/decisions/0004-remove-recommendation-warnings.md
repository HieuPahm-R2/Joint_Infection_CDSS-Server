# 0004 Remove Recommendation Warnings

Date: 2026-08-17

## Status

Accepted

## Context

Recommendation warnings had two historical producers (the backend rule engine
and Rag_Agentic), with incompatible meanings. They are not required in the
recommendation product contract.

## Decision

Remove `warnings_json` from both recommendation persistence models and from
all inbound and outbound backend DTOs. The rule engine must not generate a
warning payload. Unknown warning fields returned by external producers are not
bound or persisted.

This supersedes the warning-ownership portion of decision 0003.

## Alternatives Considered

1. Retain only RAG warnings on the recommendation run.
2. Retain only rule-engine warnings on the diagnostic result.

## Consequences

Positive:

- The recommendation contract has no ambiguous warning provenance.
- Historical and future runs cannot retain stale warning payloads.

Tradeoffs:

- Existing warning data is irreversibly discarded by migration V27.

## Follow-Up

- If warnings become a product requirement again, define an owned model and
  explicit producer contract before adding persistence.
