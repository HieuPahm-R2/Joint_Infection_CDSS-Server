ALTER TABLE "pji_episodes"
    DROP COLUMN IF EXISTS "admission_time",
    DROP COLUMN IF EXISTS "discharge_time",
    DROP COLUMN IF EXISTS "initial_department_admission_time";
