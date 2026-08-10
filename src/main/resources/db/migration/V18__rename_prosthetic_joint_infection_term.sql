-- Use the preferred Vietnamese term in existing generated and stored responses.
-- JSONB values are converted to text only for the replacement, then parsed back,
-- so their structure and value types remain unchanged.
DO $$
DECLARE
    column_record record;
BEGIN
    FOR column_record IN
        SELECT table_schema, table_name, column_name, data_type
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND data_type IN ('character varying', 'text', 'jsonb')
    LOOP
        IF column_record.data_type = 'jsonb' THEN
            EXECUTE format(
                'UPDATE %I.%I SET %I = replace(%I::text, %L, %L)::jsonb WHERE %I::text LIKE %L',
                column_record.table_schema,
                column_record.table_name,
                column_record.column_name,
                column_record.column_name,
                'nhiễm trùng khớp giả',
                'nhiễm trùng khớp nhân tạo',
                column_record.column_name,
                '%nhiễm trùng khớp giả%'
            );
        ELSE
            EXECUTE format(
                'UPDATE %I.%I SET %I = replace(%I, %L, %L) WHERE %I LIKE %L',
                column_record.table_schema,
                column_record.table_name,
                column_record.column_name,
                column_record.column_name,
                'nhiễm trùng khớp giả',
                'nhiễm trùng khớp nhân tạo',
                column_record.column_name,
                '%nhiễm trùng khớp giả%'
            );
        END IF;
    END LOOP;
END $$;