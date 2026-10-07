-- V1__baseline.sql
DO $$
BEGIN
    IF (SELECT current_setting('server_version_num')::integer) < 160000 THEN
        RAISE EXCEPTION 'PostgreSQL 16 or higher is required. Found: %', current_setting('server_version');
    END IF;
END;
$$;
