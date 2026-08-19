# Remove recommendation warning persistence

## Objective

Remove warning payloads from the recommendation contract so the backend neither
accepts nor persists rule-engine or RAG warnings.

## Plan

1. Remove warning fields and warning generation from diagnostic and recommendation DTO/entity flows.
2. Add a forward migration that drops persisted warning columns.
3. Remove frontend contract fields and validate backend/frontend compilation.

## Decision

`warnings_json` is no longer part of the recommendation API or database model.
Existing warning data is intentionally discarded by migration.

## Result

- Removed warning generation, inbound binding, entity fields, response fields,
  and frontend declarations.
- Added migration V27 to drop both persisted warning columns.
- Validated with 12 focused backend tests and frontend TypeScript checking.
