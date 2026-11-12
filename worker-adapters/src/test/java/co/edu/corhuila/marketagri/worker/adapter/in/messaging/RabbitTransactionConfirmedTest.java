package co.edu.corhuila.marketagri.worker.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import co.edu.corhuila.marketagri.worker.adapter.correlation.Correlation;
import co.edu.corhuila.marketagri.worker.adapter.out.retry.Retry;
import co.edu.corhuila.marketagri.worker.application.port.in.NotifyTransactionConfirmed;
import co.edu.corhuila.marketagri.worker.domain.model.TransactionConfirmed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;

/**
 * The consumer of worker.transaction-confirmed (ADR-016 points 2 and 6, ADR-014): one message at a
 * time, acknowledged only after every recipient was handled; any failure — an unreadable event, or
 * one the adapters could not fix with their retries — is rejected without requeue, so it goes to
 * the DLQ. The event's correlation id is in every log line while it is handled (norm 5.7.3).
 */
class RabbitTransactionConfirmedTest {

    private static final UUID EVENT = UUID.fromString("e0000000-0000-4000-8000-000000000001");
    private static final UUID TRANSACTION = UUID.fromString("70000000-0000-4000-8000-000000000001");
    private static final UUID BUYER = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private static final UUID PRODUCER = UUID.fromString("b0000000-0000-4000-8000-000000000002");

    private final Channel channel = Mockito.mock(Channel.class);
    private final List<TransactionConfirmed> notified = new ArrayList<>();
    private final List<String> correlations = new ArrayList<>();
    private RuntimeException failure;
    private final NotifyTransactionConfirmed notify = event -> {
        correlations.add(MDC.get(Correlation.MDC_KEY));
        if (failure != null) {
            throw failure;
        }
        notified.add(event);
        return new NotifyTransactionConfirmed.Outcome(List.of(BUYER), List.of(PRODUCER));
    };
    private final RabbitTransactionConfirmed consumer =
            new RabbitTransactionConfirmed(new ConnectionFactory(), new ObjectMapper(), notify);

    @Test
    void aValidEventIsNotifiedWithItsCorrelationIdAndThenAcknowledged() throws Exception {
        consumer.receive(channel, delivery(envelope("TransactionConfirmed", 1)));

        assertThat(notified).containsExactly(new TransactionConfirmed(EVENT, TRANSACTION, "Café pergamino",
                BUYER, PRODUCER, new BigDecimal("12.5"), "KILOGRAMO", 8_500_000L));
        assertThat(correlations).containsExactly("corr-checkout-1");
        assertThat(MDC.get(Correlation.MDC_KEY)).isNull();
        verify(channel).basicAck(7L, false);
        verify(channel, never()).basicReject(7L, false);
    }

    @Test
    void anUnreadableOrForeignEventGoesToTheDlqWithoutBeingNotified() throws Exception {
        for (String body : List.of("{}", "not json", envelope("TransactionFailed", 1), envelope("TransactionConfirmed", 2),
                envelope("TransactionConfirmed", 1).replace("\"amountCents\":8500000", "\"amountCents\":0"))) {
            Channel fresh = Mockito.mock(Channel.class);
            consumer.receive(fresh, delivery(body));
            verify(fresh).basicReject(7L, false);
            verify(fresh, never()).basicAck(7L, false);
        }
        assertThat(notified).isEmpty();
    }

    @Test
    void aFailureTheRetriesCouldNotFixGoesToTheDlq() throws Exception {
        failure = new Retry.TransientFailure("auth-api unreachable");

        consumer.receive(channel, delivery(envelope("TransactionConfirmed", 1)));

        verify(channel).basicReject(7L, false);
        verify(channel, never()).basicAck(7L, false);
    }

    private static String envelope(String type, int version) {
        return """
                {"eventId":"%s","eventType":"%s","aggregateId":"%s","aggregateType":"Transaction",
                 "occurredAt":"2026-11-11T15:04:05Z","version":%d,
                 "payload":{"transactionId":"%s","conversationId":"%s","productId":"%s","productName":"Café pergamino",
                            "buyerId":"%s","producerId":"%s","quantity":12.5,"unit":"KILOGRAMO",
                            "unitPriceCents":680000,"amountCents":8500000,"currency":"COP","confirmedAt":"2026-11-11T15:04:05Z"},
                 "metadata":{"correlationId":"corr-checkout-1","causationId":null,"userId":null}}
                """.formatted(EVENT, type, TRANSACTION, version, TRANSACTION, UUID.randomUUID(), UUID.randomUUID(),
                BUYER, PRODUCER);
    }

    private static Delivery delivery(String body) {
        return new Delivery(new Envelope(7L, false, "market-agri.events", "transactions.transaction-confirmed"),
                new AMQP.BasicProperties.Builder().build(), body.getBytes(StandardCharsets.UTF_8));
    }
}
