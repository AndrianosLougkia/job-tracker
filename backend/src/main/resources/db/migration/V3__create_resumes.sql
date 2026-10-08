-- V3__create_resumes.sql
-- Stage 5: resume metadata table.
-- File bytes are stored in MinIO; only metadata lives in PostgreSQL.

CREATE TABLE resumes (
    id                BIGSERIAL    PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    original_filename VARCHAR(255) NOT NULL,
    storage_key       VARCHAR(512) NOT NULL UNIQUE,
    file_size_bytes   BIGINT       NOT NULL,
    extracted_text    TEXT,                          -- NULL if extraction failed
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_resumes_user_id ON resumes (user_id);
