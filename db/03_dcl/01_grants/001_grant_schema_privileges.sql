-- Permissions stay within the worker schema (Anexo J.3). The writer deletes only for the purge job
-- (ADR-016 point 7) and never updates: a recorded notice is never rewritten.
GRANT USAGE ON SCHEMA worker TO worker_reader, worker_writer;
GRANT SELECT ON ALL TABLES IN SCHEMA worker TO worker_reader;
GRANT SELECT, INSERT, DELETE ON ALL TABLES IN SCHEMA worker TO worker_writer;

ALTER DEFAULT PRIVILEGES IN SCHEMA worker GRANT SELECT ON TABLES TO worker_reader;
ALTER DEFAULT PRIVILEGES IN SCHEMA worker GRANT SELECT, INSERT, DELETE ON TABLES TO worker_writer;
