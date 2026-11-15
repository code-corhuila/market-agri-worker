package co.edu.corhuila.marketagri.worker.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What the worker reads of a {@code TransactionConfirmed} (api-contract §6): the envelope's
 * {@code eventId} and the payload fields the two notices need. Built only if the event keeps its
 * contract; otherwise {@link InvalidEventException}.
 */
public record TransactionConfirmed(UUID eventId, UUID transactionId, String productName, UUID buyerId,
                                   UUID producerId, BigDecimal quantity, String unit, long amountCents) {

    public TransactionConfirmed {
        require(eventId != null && transactionId != null && buyerId != null && producerId != null,
                "eventId, transactionId, buyerId and producerId are required");
        require(productName != null && !productName.isBlank(), "productName is required");
        require(unit != null && !unit.isBlank(), "unit is required");
        require(quantity != null && quantity.signum() > 0, "quantity must be positive");
        require(amountCents > 0, "amountCents must be positive");
    }

    private static void require(boolean ok, String rule) {
        if (!ok) {
            throw new InvalidEventException("TransactionConfirmed: " + rule);
        }
    }
}
