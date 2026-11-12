package co.edu.corhuila.marketagri.worker.domain.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The payload of TransactionConfirmed (api-contract §6) as the worker needs it. An event that
 * breaks it cannot be fixed by retrying: it is refused as invalid (ADR-016 point 6).
 */
class TransactionConfirmedTest {

    static TransactionConfirmed valid() {
        return new TransactionConfirmed(UUID.randomUUID(), UUID.randomUUID(), "Café pergamino",
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("12.5"), "KILOGRAMO", 8_500_000L);
    }

    @Test
    void missingIdsOrNamesAreInvalid() {
        TransactionConfirmed ok = valid();
        assertThatThrownBy(() -> new TransactionConfirmed(null, ok.transactionId(), ok.productName(),
                ok.buyerId(), ok.producerId(), ok.quantity(), ok.unit(), ok.amountCents()))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> new TransactionConfirmed(ok.eventId(), ok.transactionId(), " ",
                ok.buyerId(), ok.producerId(), ok.quantity(), ok.unit(), ok.amountCents()))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> new TransactionConfirmed(ok.eventId(), ok.transactionId(), ok.productName(),
                null, ok.producerId(), ok.quantity(), ok.unit(), ok.amountCents()))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void aQuantityOrAnAmountThatIsNotPositiveIsInvalid() {
        TransactionConfirmed ok = valid();
        assertThatThrownBy(() -> new TransactionConfirmed(ok.eventId(), ok.transactionId(), ok.productName(),
                ok.buyerId(), ok.producerId(), BigDecimal.ZERO, ok.unit(), ok.amountCents()))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> new TransactionConfirmed(ok.eventId(), ok.transactionId(), ok.productName(),
                ok.buyerId(), ok.producerId(), ok.quantity(), ok.unit(), 0L))
                .isInstanceOf(InvalidEventException.class);
    }
}
