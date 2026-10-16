-- V38 (decision 0010, section 4): optimistic locking on the clinical children.
--
-- Only the decision-support entities carried @Version, so two writers could
-- update the same clinical row concurrently and one change would be silently
-- lost. The episode edit lock covers the aggregate save, but not the writers that
-- go through their own services: lab auto-fulfilment, the QR upload session, and
-- the OCR image writer.
--
-- The check lives in one place -- a version column on the row -- rather than as a
-- hand-written compare-and-set in every service that touches a clinical row.
--
-- medical_histories is deliberately excluded: it is a 1:1 row keyed by the
-- admission id and is only written under the admission edit lock.

ALTER TABLE public.clinical_assessments        ADD COLUMN version_no bigint NOT NULL DEFAULT 0;
ALTER TABLE public.lab_reports                 ADD COLUMN version_no bigint NOT NULL DEFAULT 0;
ALTER TABLE public.specimen_cultures           ADD COLUMN version_no bigint NOT NULL DEFAULT 0;
ALTER TABLE public.antibiotic_susceptibilities ADD COLUMN version_no bigint NOT NULL DEFAULT 0;
ALTER TABLE public.imaging_studies             ADD COLUMN version_no bigint NOT NULL DEFAULT 0;
ALTER TABLE public.surgeries                   ADD COLUMN version_no bigint NOT NULL DEFAULT 0;
