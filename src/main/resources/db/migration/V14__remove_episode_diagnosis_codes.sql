ALTER TABLE "public"."pji_episodes"
    DROP COLUMN IF EXISTS "referral_diagnosis_code",
    DROP COLUMN IF EXISTS "emergency_diagnosis_code",
    DROP COLUMN IF EXISTS "inpatient_diagnosis_code",
    DROP COLUMN IF EXISTS "discharge_primary_diagnosis_code",
    DROP COLUMN IF EXISTS "discharge_cause_code",
    DROP COLUMN IF EXISTS "accompanying_disease_code",
    DROP COLUMN IF EXISTS "preoperative_diagnosis_code",
    DROP COLUMN IF EXISTS "postoperative_diagnosis_code";
