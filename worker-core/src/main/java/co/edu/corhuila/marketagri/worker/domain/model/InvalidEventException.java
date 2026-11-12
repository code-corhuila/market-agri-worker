package co.edu.corhuila.marketagri.worker.domain.model;

/**
 * An event that breaks its contract (api-contract §6). Retrying cannot fix it: the consumer sends
 * it to the dead-letter queue at once (ADR-016 point 6).
 */
public final class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
