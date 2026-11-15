-- HU-NOTIF-001 · W1: the table rules of processed_event (ADR-013, ADR-016; FR-033, INV-008). Run by
-- db-ci after the first update; everything is rolled back at the end.
\set ON_ERROR_STOP on
BEGIN;

-- Fails the run unless the statement is refused by exactly the expected constraint.
CREATE FUNCTION pg_temp.expect_refused(stmt text, expected text) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    got text;
BEGIN
    EXECUTE stmt;
    RAISE EXCEPTION 'FAIL: accepted, % should refuse it: %', expected, stmt;
EXCEPTION WHEN check_violation OR unique_violation OR not_null_violation THEN
    GET STACKED DIAGNOSTICS got = CONSTRAINT_NAME;
    IF got IS DISTINCT FROM expected THEN
        RAISE EXCEPTION 'FAIL: refused by %, expected %: %', got, expected, stmt;
    END IF;
    RAISE NOTICE 'ok: % refuses it', expected;
END $$;

CREATE FUNCTION pg_temp.expect(ok boolean, what text) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    IF ok IS NOT TRUE THEN
        RAISE EXCEPTION 'FAIL: %', what;
    END IF;
    RAISE NOTICE 'ok: %', what;
END $$;

-- 1. One TransactionConfirmed notifies two people: each recipient is recorded on its own (DEC-003).
INSERT INTO worker.processed_event (event_id, recipient_id)
VALUES ('e0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001'),
       ('e0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000002');
SELECT pg_temp.expect((SELECT count(*) = 2 FROM worker.processed_event
                       WHERE event_id = 'e0000000-0000-4000-8000-000000000001'),
    'one event, two recipients, two rows');

-- 2. At most one notice per (eventId, recipientId) (FR-033): the second insert of a pair is refused.
SELECT pg_temp.expect_refused(
    $$INSERT INTO worker.processed_event (event_id, recipient_id)
      VALUES ('e0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001')$$,
    'pk_processed_event');

-- 3. processed_at is filled by the database when the row is written (after the e-mail, ADR-013).
SELECT pg_temp.expect((SELECT bool_and(processed_at IS NOT NULL AND processed_at <= now())
                       FROM worker.processed_event),
    'processed_at defaults to now()');

-- 4. Nothing else is stored: no notification, no payload (ADR-008, ADR-016).
SELECT pg_temp.expect(
    (SELECT array_agg(column_name::text ORDER BY ordinal_position) = ARRAY['event_id', 'recipient_id', 'processed_at']
     FROM information_schema.columns WHERE table_schema = 'worker' AND table_name = 'processed_event'),
    'processed_event holds only event_id, recipient_id and processed_at');

-- 5. The purge job reads by age (ADR-016 point 7): an index serves it.
SELECT pg_temp.expect(EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'worker'
                              AND indexname = 'idx_processed_event_processed_at'),
    'idx_processed_event_processed_at serves the purge');

ROLLBACK;
\echo 'processed-event: all checks passed'
