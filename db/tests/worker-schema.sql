-- HU-NOTIF-001 · W1: the worker schema and its permissions (ADR-013, ADR-016, Anexo J.3, J.7). Run by
-- db-ci after the first update; nothing is changed.
\set ON_ERROR_STOP on

CREATE FUNCTION pg_temp.expect(ok boolean, what text) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    IF ok IS NOT TRUE THEN
        RAISE EXCEPTION 'FAIL: %', what;
    END IF;
    RAISE NOTICE 'ok: %', what;
END $$;

-- 1. The state of the transversal worker lives in its own schema, never in public (ADR-013).
SELECT pg_temp.expect(EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'worker'),
    'the worker schema exists');

-- 2. Two NOLOGIN roles carry the permissions; worker_app, created by infra, gets the writer.
SELECT pg_temp.expect(
    (SELECT count(*) = 2 AND bool_and(NOT rolcanlogin) FROM pg_roles
     WHERE rolname IN ('worker_reader', 'worker_writer')),
    'worker_reader and worker_writer exist and cannot log in');
SELECT pg_temp.expect(pg_has_role('worker_app', 'worker_writer', 'MEMBER'),
    'worker_app is a member of worker_writer');

-- 3. processed_event: the writer reads, inserts and deletes (the purge job, ADR-016 point 7), never
--    updates: a recorded notice is never rewritten.
SELECT pg_temp.expect(has_schema_privilege('worker_app', 'worker', 'USAGE'),
    'worker_app can use the worker schema');
SELECT pg_temp.expect(
    has_table_privilege('worker_app', 'worker.processed_event', 'SELECT')
    AND has_table_privilege('worker_app', 'worker.processed_event', 'INSERT')
    AND has_table_privilege('worker_app', 'worker.processed_event', 'DELETE')
    AND NOT has_table_privilege('worker_app', 'worker.processed_event', 'UPDATE'),
    'worker_app reads, inserts and deletes processed_event, without UPDATE');
SELECT pg_temp.expect(
    (SELECT bool_or(a.privilege_type = 'INSERT') AND bool_or(a.privilege_type = 'DELETE')
            AND NOT bool_or(a.privilege_type = 'UPDATE')
     FROM pg_default_acl d
     JOIN pg_namespace n ON n.oid = d.defaclnamespace AND n.nspname = 'worker'
     CROSS JOIN LATERAL aclexplode(d.defaclacl) a
     WHERE d.defaclobjtype = 'r' AND a.grantee = 'worker_writer'::regrole),
    'new tables give worker_writer INSERT and DELETE, without UPDATE');

\echo 'worker-schema: all checks passed'
