-- V5__normalise_user_emails.sql
-- Stage 10: emails are case-insensitive. The application lower-cases them on
-- registration and login; this migration does the same for existing rows and
-- enforces it, so "Alice@x.com" and "alice@x.com" can never be two accounts.
-- Fails (and changes nothing) if two existing accounts differ only by case.

UPDATE users SET email = lower(email) WHERE email <> lower(email);

ALTER TABLE users ADD CONSTRAINT chk_users_email_lower_case CHECK (email = lower(email));

-- The UNIQUE constraint on email already creates an index
DROP INDEX IF EXISTS idx_users_email;
