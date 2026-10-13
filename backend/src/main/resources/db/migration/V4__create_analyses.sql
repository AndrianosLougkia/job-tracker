-- V4__create_analyses.sql
-- Stage 6: resume-versus-job-description analyses.
-- The structured AI output is stored as JSONB; match_score is duplicated into its
-- own column so dashboards can aggregate without parsing JSON.

CREATE TYPE analysis_status AS ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED');

CREATE TABLE analyses (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    application_id  BIGINT          NOT NULL REFERENCES job_applications (id) ON DELETE CASCADE,
    resume_id       BIGINT          NOT NULL REFERENCES resumes (id) ON DELETE CASCADE,
    status          analysis_status NOT NULL DEFAULT 'PENDING',
    provider        VARCHAR(50)     NOT NULL,
    match_score     INTEGER         CHECK (match_score BETWEEN 0 AND 100),
    result_json     JSONB,                           -- NULL until COMPLETED
    error_message   VARCHAR(1000),                   -- set only when FAILED
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,

    CONSTRAINT chk_analyses_completed_has_result
        CHECK (status <> 'COMPLETED' OR (result_json IS NOT NULL AND match_score IS NOT NULL))
);

CREATE INDEX idx_analyses_user_id_created_at ON analyses (user_id, created_at DESC);
CREATE INDEX idx_analyses_application_id     ON analyses (application_id);
CREATE INDEX idx_analyses_resume_id          ON analyses (resume_id);
