package co.edu.corhuila.marketagri.worker.adapter.correlation;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * Neutral holder of the correlation id (Annex D): the consumer fills it with the event's
 * {@code metadata.correlationId}, the outbound adapters read it (norm 5.7.3).
 */
public final class Correlation {

    public static final String MDC_KEY = "correlationId";
    private Correlation() {
    }

    /** The id of the current request or run; a new one if nothing set it (it is then logged by the caller). */
    public static String current() {
        String id = MDC.get(MDC_KEY);
        return id != null ? id : UUID.randomUUID().toString();
    }
}
