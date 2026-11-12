package co.edu.corhuila.marketagri.worker.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.marketagri.worker.application.port.in.NotifyTransactionConfirmed.Outcome;
import co.edu.corhuila.marketagri.worker.application.port.out.Contacts;
import co.edu.corhuila.marketagri.worker.application.port.out.NoticeSender;
import co.edu.corhuila.marketagri.worker.application.port.out.ProcessedEvents;
import co.edu.corhuila.marketagri.worker.domain.model.Contact;
import co.edu.corhuila.marketagri.worker.domain.model.Notice;
import co.edu.corhuila.marketagri.worker.domain.model.Notices;
import co.edu.corhuila.marketagri.worker.domain.model.TransactionConfirmed;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * HU-NOTIF-001 in the core (ADR-016 points 3–5; FR-031, FR-033): one e-mail to the buyer and one
 * to the producer, each recorded in processed_event only AFTER it was sent, and never sent again
 * for the same (eventId, recipientId).
 */
class NotifyTransactionConfirmedServiceTest {

    private static final UUID BUYER = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private static final UUID PRODUCER = UUID.fromString("b0000000-0000-4000-8000-000000000002");
    private static final TransactionConfirmed EVENT = new TransactionConfirmed(
            UUID.fromString("e0000000-0000-4000-8000-000000000001"),
            UUID.fromString("70000000-0000-4000-8000-000000000001"), "Café pergamino",
            BUYER, PRODUCER, new BigDecimal("12.5"), "KILOGRAMO", 8_500_000L);
    private static final Map<UUID, Contact> PEOPLE = Map.of(
            BUYER, new Contact(BUYER, "comprador@market-agri.test", "Bruno Díaz"),
            PRODUCER, new Contact(PRODUCER, "productor@market-agri.test", "Ana Ruiz"));

    /** What happened, in order: "send:<email>" and "record:<recipient>". */
    private final List<String> log = new ArrayList<>();
    private final List<Notice> sent = new ArrayList<>();
    private final Set<UUID> recorded = new HashSet<>();
    private final List<UUID> lookedUp = new ArrayList<>();
    private String failSendingTo;

    private final Contacts contacts = id -> {
        lookedUp.add(id);
        return PEOPLE.get(id);
    };
    private final NoticeSender sender = notice -> {
        if (notice.to().equals(failSendingTo)) {
            throw new IllegalStateException("SMTP refused the connection");
        }
        log.add("send:" + notice.to());
        sent.add(notice);
    };
    private final ProcessedEvents processed = new ProcessedEvents() {
        @Override
        public boolean contains(UUID eventId, UUID recipientId) {
            return eventId.equals(EVENT.eventId()) && recorded.contains(recipientId);
        }

        @Override
        public void record(UUID eventId, UUID recipientId) {
            log.add("record:" + recipientId);
            recorded.add(recipientId);
        }
    };
    private final NotifyTransactionConfirmedService service = new NotifyTransactionConfirmedService(
            contacts, sender, processed, new Notices("https://market.test"));

    @Test
    void bothPartiesGetOneEmailEachRecordedAfterItWasSent() {
        Outcome outcome = service.notify(EVENT);

        assertThat(log).containsExactly(
                "send:comprador@market-agri.test", "record:" + BUYER,
                "send:productor@market-agri.test", "record:" + PRODUCER);
        assertThat(outcome.sent()).containsExactly(BUYER, PRODUCER);
        assertThat(outcome.alreadySent()).isEmpty();
    }

    @Test
    void theBuyerReadsTheirPurchaseInUstedWithTheAmountAndTheLink() {
        service.notify(EVENT);

        Notice buyer = sent.get(0);
        assertThat(buyer.subject()).isEqualTo("Su compra fue confirmada");
        assertThat(buyer.body()).contains("Bruno Díaz", "Su pago fue confirmado", "Café pergamino",
                "12,5 kg", "$ 85.000", "https://market.test/transacciones/70000000-0000-4000-8000-000000000001");
    }

    @Test
    void theProducerReadsTheirSale() {
        service.notify(EVENT);

        Notice producer = sent.get(1);
        assertThat(producer.to()).isEqualTo("productor@market-agri.test");
        assertThat(producer.subject()).isEqualTo("Tiene una venta confirmada");
        assertThat(producer.body()).contains("Ana Ruiz", "Café pergamino", "12,5 kg", "$ 85.000",
                "/transacciones/70000000-0000-4000-8000-000000000001");
    }

    @Test
    void noEmailCarriesAnInternalIdentifierBeyondTheTransactionLink() {
        service.notify(EVENT);

        for (Notice notice : sent) {
            assertThat(notice.body()).doesNotContain(BUYER.toString(), PRODUCER.toString(),
                    EVENT.eventId().toString());
        }
    }

    @Test
    void aRedeliveredEventSendsNothingAndLooksUpNobody() {
        recorded.addAll(List.of(BUYER, PRODUCER));

        Outcome outcome = service.notify(EVENT);

        assertThat(sent).isEmpty();
        assertThat(lookedUp).isEmpty();
        assertThat(outcome.sent()).isEmpty();
        assertThat(outcome.alreadySent()).containsExactly(BUYER, PRODUCER);
    }

    @Test
    void aRedeliveryAfterAPartialFailureOnlyRepeatsTheRecipientThatFailed() {
        recorded.add(BUYER);

        Outcome outcome = service.notify(EVENT);

        assertThat(log).containsExactly("send:productor@market-agri.test", "record:" + PRODUCER);
        assertThat(outcome.alreadySent()).containsExactly(BUYER);
    }

    @Test
    void aFailedEmailIsNotRecordedSoTheRedeliverySendsItAgain() {
        failSendingTo = "productor@market-agri.test";

        assertThatThrownBy(() -> service.notify(EVENT)).hasMessageContaining("SMTP");

        assertThat(recorded).containsExactly(BUYER);
    }
}
