ALTER TABLE "public"."clinical_records"
    ADD COLUMN "onset_timing" character varying(30),
    ADD COLUMN "suspected_transmission_route" character varying(30);

COMMENT ON COLUMN "public"."clinical_records"."illness_onset_date"
    IS 'Legacy data retained for recovery; replaced by onset_timing in the application contract.';

COMMENT ON COLUMN "public"."clinical_records"."suspected_infection_type"
    IS 'Legacy data retained for recovery; replaced by suspected_transmission_route in the application contract.';
