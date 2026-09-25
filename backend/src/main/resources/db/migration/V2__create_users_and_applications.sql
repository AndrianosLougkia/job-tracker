-- V2__create_users_and_applications.sql
-- Stage 2: core domain tables.

-- ------------------------------------------------------------------ --
--  users                                                               --
-- ------------------------------------------------------------------ --
CREATE TABLE users (
    id             BIGSERIAL    PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users (email);

-- ------------------------------------------------------------------ --
--  job_applications                                                    --
-- ------------------------------------------------------------------ --
CREATE TYPE application_status AS ENUM (
    'WISHLIST',
    'APPLIED',
    'SCREENING',
    'INTERVIEW',
    'OFFER',
    'REJECTED',
    'WITHDRAWN'
);

CREATE TABLE job_applications (
    id               BIGSERIAL          PRIMARY KEY,
    user_id          BIGINT             NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    company          VARCHAR(255)       NOT NULL,
    role             VARCHAR(255)       NOT NULL,
    status           application_status NOT NULL DEFAULT 'WISHLIST',
    job_description  TEXT,
    notes            TEXT,
    applied_at       DATE,
    created_at       TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ        NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_job_applications_user_id         ON job_applications (user_id);
CREATE INDEX idx_job_applications_user_id_status  ON job_applications (user_id, status);
CREATE INDEX idx_job_applications_created_at      ON job_applications (created_at DESC);
