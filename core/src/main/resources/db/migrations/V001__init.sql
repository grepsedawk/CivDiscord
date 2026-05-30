-- Each migration file must contain EXACTLY ONE SQL statement. The runner
-- executes the whole file as a single statement (no semicolon-splitting),
-- so additional statements would be silently ignored or rejected by the
-- JDBC driver. Use one file per statement, and prefer idempotent DDL
-- (CREATE TABLE IF NOT EXISTS, CREATE INDEX IF NOT EXISTS).
CREATE TABLE IF NOT EXISTS schema_migrations (name TEXT PRIMARY KEY)
