package co.edu.corhuila.marketagri.worker.application.port.out;

import java.util.UUID;

/** {@code worker.processed_event}: which (event, recipient) pairs already got their notice (ADR-013). */
public interface ProcessedEvents {

    boolean contains(UUID eventId, UUID recipientId);

    void record(UUID eventId, UUID recipientId);
}
