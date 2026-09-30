-- UserDetailsConfig.loadUserByUsername rejects any account whose status is not ACTIVE,
-- but users.status is nullable with no database default (V1__baseline.sql:1042) and no
-- code path ever set it. Every account that already existed - including the bootstrap
-- administrator - was therefore permanently unable to log in.
--
-- The application now writes ACTIVE on both creation paths (DatabaseInitializer for the
-- bootstrap admin, UserServiceImpl.create for API-created users), so this migration only
-- has to repair existing rows.
--
-- Backfill only, on purpose. Tightening the column to NOT NULL is left to a later,
-- separately owned change: it should come after this backfill is verified in every
-- environment, and it turns an omitted status into a hard insert failure instead of a
-- silently unusable account. See docs/plans/active/authorization-hardening.md.
--
-- users.status is character varying(50), so the plain literal needs no enum cast even
-- though the userstatus enum type exists.
UPDATE public.users
SET status = 'ACTIVE'
WHERE status IS NULL;
