ALTER TABLE "public"."users"
    ADD COLUMN "avatar_bucket" character varying(200),
    ADD COLUMN "avatar_object_key" character varying(500);
