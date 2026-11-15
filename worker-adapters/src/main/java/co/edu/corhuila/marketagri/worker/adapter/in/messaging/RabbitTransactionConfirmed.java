package co.edu.corhuila.marketagri.worker.adapter.in.messaging;

import co.edu.corhuila.marketagri.worker.adapter.correlation.Correlation;
import co.edu.corhuila.marketagri.worker.application.port.in.NotifyTransactionConfirmed;
import co.edu.corhuila.marketagri.worker.domain.model.InvalidEventException;
import co.edu.corhuila.marketagri.worker.domain.model.TransactionConfirmed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Delivery;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The consumer of {@code worker.transaction-confirmed} (ADR-016 point 2, norm 5.7: one consumer per
 * event). Infra declares the queue and its DLQ; this class only reads it (ADR-014). One message at a
 * time; it is acknowledged after every recipient was handled, and any failure — an event that
 * breaks its contract, or one the adapters could not fix with their bounded retries — is rejected
 * without requeue, so it goes to {@code worker.transaction-confirmed.dlq} for a person to replay.
 */
public final class RabbitTransactionConfirmed implements AutoCloseable {

    static final String QUEUE = "worker.transaction-confirmed";
    private static final String TYPE = "TransactionConfirmed";
    private static final int VERSION = 1;
    private static final Logger log = LoggerFactory.getLogger(RabbitTransactionConfirmed.class);

    private final ConnectionFactory factory;
    private final ObjectMapper json;
    private final NotifyTransactionConfirmed notify;
    private Connection connection;
    private Channel channel;

    public RabbitTransactionConfirmed(ConnectionFactory factory, ObjectMapper json, NotifyTransactionConfirmed notify) {
        this.factory = factory;
        this.json = json;
        this.notify = notify;
    }

    /** Connects, and reconnects after the broker went away; while it is away events wait in the queue (FR-034). */
    @Scheduled(fixedDelayString = "PT5S")
    public synchronized void connect() {
        if (channel != null && channel.isOpen()) {
            return;
        }
        close();
        try {
            connection = factory.newConnection("worker transaction-confirmed");
            Channel consumer = connection.createChannel();
            consumer.basicQos(1);
            consumer.basicConsume(QUEUE, false, (tag, delivery) -> receive(consumer, delivery), tag -> consumer.abort());
            channel = consumer;
        } catch (IOException | TimeoutException | RuntimeException error) {
            close();
            log.warn("Could not subscribe to {}: {}", QUEUE, error.toString());
        }
    }

    void receive(Channel consumer, Delivery delivery) throws IOException {
        long tag = delivery.getEnvelope().getDeliveryTag();
        JsonNode envelope;
        TransactionConfirmed event;
        try {
            envelope = json.readTree(delivery.getBody());
            event = read(envelope);
        } catch (IOException | RuntimeException unreadable) {
            log.error("Rejecting an unreadable {} to the DLQ: {}", TYPE, unreadable.getMessage());
            consumer.basicReject(tag, false);
            return;
        }
        String correlation = envelope.path("metadata").path("correlationId").asText(event.eventId().toString());
        try (var ignored = MDC.putCloseable(Correlation.MDC_KEY, correlation)) {
            NotifyTransactionConfirmed.Outcome outcome = notify.notify(event);
            log.info("{} {} notified {}", TYPE, event.eventId(), outcome.sent());
            if (!outcome.alreadySent().isEmpty()) {
                log.info("{} {} already notified {}: duplicate delivery ignored", TYPE, event.eventId(), outcome.alreadySent());
            }
        } catch (RuntimeException failed) {
            try (var ignored = MDC.putCloseable(Correlation.MDC_KEY, correlation)) {
                log.error("Rejecting {} {} to the DLQ", TYPE, event.eventId(), failed);
            }
            consumer.basicReject(tag, false);
            return;
        }
        consumer.basicAck(tag, false);
    }

    /** The envelope and payload of api-contract §6; anything else is {@link InvalidEventException}. */
    private static TransactionConfirmed read(JsonNode envelope) {
        if (!TYPE.equals(envelope.path("eventType").asText()) || envelope.path("version").asInt(-1) != VERSION) {
            throw new InvalidEventException("not a " + TYPE + " version " + VERSION);
        }
        JsonNode payload = envelope.path("payload");
        try {
            return new TransactionConfirmed(uuid(envelope, "eventId"), uuid(payload, "transactionId"),
                    payload.path("productName").asText(null), uuid(payload, "buyerId"), uuid(payload, "producerId"),
                    payload.path("quantity").isNumber() ? new BigDecimal(payload.path("quantity").asText()) : null,
                    payload.path("unit").asText(null), payload.path("amountCents").asLong(0));
        } catch (IllegalArgumentException malformed) {
            throw new InvalidEventException(malformed.getMessage());
        }
    }

    private static UUID uuid(JsonNode node, String field) {
        return node.hasNonNull(field) ? UUID.fromString(node.get(field).asText()) : null;
    }

    @Override
    public synchronized void close() {
        channel = null;
        if (connection != null) {
            connection.abort();
            connection = null;
        }
    }
}
