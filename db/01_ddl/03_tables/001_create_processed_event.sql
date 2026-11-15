-- Notices already sent (ADR-013, ADR-016): one row per (event, recipient), written AFTER the e-mail,
-- so a crash in between repeats one e-mail and never loses one. No notification is stored (ADR-008).
CREATE TABLE worker.processed_event (
    event_id     uuid        NOT NULL,   -- eventId of the envelope
    recipient_id uuid        NOT NULL,   -- the user the notice was for
    processed_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_processed_event PRIMARY KEY (event_id, recipient_id)
);
