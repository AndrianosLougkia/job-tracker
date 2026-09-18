-- V1__baseline.sql
-- Stage 1: Baseline migration.
-- No tables yet — entities and schema are introduced in Stage 2.
-- This migration exists so Flyway initialises cleanly from Stage 1 onwards.

-- Verify the PostgreSQL version is supported (16+).
DO $$
BEGIN
    IF (SELECT current_setting('server_version_num')::integer) < 160000 THEN
        RAISE EXCEPTION 'PostgreSQL 16 or higher is required. Found: %',
            current_setting('server_version');
    END IF;
END;
$$;
