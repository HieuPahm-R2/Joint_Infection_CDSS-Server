ALTER TABLE "public"."pji_episodes"
    ADD COLUMN "admission_time" time without time zone,
    ADD COLUMN "discharge_time" time without time zone,
    ADD COLUMN "admission_count" integer,
    ADD COLUMN "initial_department_treatment_days" integer,
    ADD COLUMN "initial_department_admission_date" date,
    ADD COLUMN "initial_department_admission_time" time without time zone,
    ADD COLUMN "department_transfers" jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN "hospital_transfer_type" character varying(30),
    ADD COLUMN "hospital_transfer_destination" text,
    ADD COLUMN "discharge_disposition" character varying(30),
    ADD COLUMN "referral_diagnosis" text,
    ADD COLUMN "referral_diagnosis_code" character varying(30),
    ADD COLUMN "emergency_diagnosis" text,
    ADD COLUMN "emergency_diagnosis_code" character varying(30),
    ADD COLUMN "inpatient_diagnosis" text,
    ADD COLUMN "inpatient_diagnosis_code" character varying(30),
    ADD COLUMN "has_incident" boolean,
    ADD COLUMN "has_complication" boolean,
    ADD COLUMN "complication_cause" character varying(30),
    ADD COLUMN "postoperative_treatment_days" integer,
    ADD COLUMN "surgery_count" integer,
    ADD COLUMN "discharge_primary_diagnosis" text,
    ADD COLUMN "discharge_primary_diagnosis_code" character varying(30),
    ADD COLUMN "discharge_cause" text,
    ADD COLUMN "discharge_cause_code" character varying(30),
    ADD COLUMN "accompanying_disease" text,
    ADD COLUMN "accompanying_disease_code" character varying(30),
    ADD COLUMN "preoperative_diagnosis" text,
    ADD COLUMN "preoperative_diagnosis_code" character varying(30),
    ADD COLUMN "postoperative_diagnosis" text,
    ADD COLUMN "postoperative_diagnosis_code" character varying(30);

ALTER TABLE "public"."clinical_records"
    RENAME COLUMN "notations" TO "surgical_disease";

COMMENT ON COLUMN "public"."clinical_records"."days_since_index_arthroplasty"
    IS 'Legacy data retained for recovery; no longer exposed by the application contract.';
