package co.edu.corhuila.marketagri.worker.application.port.in;

import co.edu.corhuila.marketagri.worker.domain.model.TransactionConfirmed;
import java.util.List;
import java.util.UUID;

/** HU-NOTIF-001: a confirmed transaction becomes one e-mail to the buyer and one to the producer. */
public interface NotifyTransactionConfirmed {

    Outcome notify(TransactionConfirmed event);

    /** Who got an e-mail now, and who already had it (a redelivery, logged and ignored: FR-033). */
    record Outcome(List<UUID> sent, List<UUID> alreadySent) {
    }
}
