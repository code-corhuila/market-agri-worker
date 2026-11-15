-- Each index names the query or rule it serves (Anexo A, rule 3). The table is new and empty,
-- so CONCURRENTLY is not needed. The duplicate check is served by the primary key.

-- Purge job: rows older than the redelivery window, oldest first, in bounded batches (ADR-016 point 7)
CREATE INDEX idx_processed_event_processed_at
    ON worker.processed_event (processed_at);
