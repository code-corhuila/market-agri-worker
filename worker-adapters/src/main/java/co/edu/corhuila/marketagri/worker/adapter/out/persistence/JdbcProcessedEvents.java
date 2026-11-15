package co.edu.corhuila.marketagri.worker.adapter.out.persistence;

import co.edu.corhuila.marketagri.worker.application.port.out.ProcessedEvents;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@code worker.processed_event} (ADR-013, ADR-016 point 5). {@code record} runs after the e-mail
 * was sent; a pair already there (a concurrent redelivery) is left as it is.
 */
public final class JdbcProcessedEvents implements ProcessedEvents {

    private final JdbcClient jdbc;

    public JdbcProcessedEvents(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean contains(UUID eventId, UUID recipientId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM worker.processed_event WHERE event_id = ? AND recipient_id = ?)")
                .params(eventId, recipientId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public void record(UUID eventId, UUID recipientId) {
        jdbc.sql("INSERT INTO worker.processed_event (event_id, recipient_id) VALUES (?, ?) ON CONFLICT DO NOTHING")
                .params(eventId, recipientId)
                .update();
    }
}
