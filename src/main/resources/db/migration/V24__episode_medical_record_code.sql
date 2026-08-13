ALTER TABLE "public"."pji_episodes"
    ADD COLUMN "medical_record_code" character varying(14);

-- Existing rows receive stable, compact codes derived from their already unique IDs.
UPDATE "public"."pji_episodes"
SET "medical_record_code" = 'BA' || LPAD("id"::text, 12, '0')
WHERE "medical_record_code" IS NULL;

ALTER TABLE "public"."pji_episodes"
    ALTER COLUMN "medical_record_code" SET NOT NULL,
    ADD CONSTRAINT "uk_pji_episodes_medical_record_code" UNIQUE ("medical_record_code"),
    ADD CONSTRAINT "ck_pji_episodes_medical_record_code_format"
        CHECK ("medical_record_code" ~ '^BA[A-Z0-9]{12}$');
