package co.edu.corhuila.marketagri.worker.application.usecase;

import co.edu.corhuila.marketagri.worker.application.port.in.NotifyTransactionConfirmed;
import co.edu.corhuila.marketagri.worker.application.port.out.Contacts;
import co.edu.corhuila.marketagri.worker.application.port.out.NoticeSender;
import co.edu.corhuila.marketagri.worker.application.port.out.ProcessedEvents;
import co.edu.corhuila.marketagri.worker.domain.model.Contact;
import co.edu.corhuila.marketagri.worker.domain.model.Notice;
import co.edu.corhuila.marketagri.worker.domain.model.Notices;
import co.edu.corhuila.marketagri.worker.domain.model.TransactionConfirmed;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Each recipient on its own (DEC-003): skip it if its pair is already recorded, otherwise read its
 * contact, send, and only then record (ADR-013: a crash in between repeats one e-mail, never loses
 * one). A failure stops here and propagates: the consumer decides whether it is retried, and the
 * recipients already recorded are not repeated on the redelivery.
 */
public final class NotifyTransactionConfirmedService implements NotifyTransactionConfirmed {

    private final Contacts contacts;
    private final NoticeSender sender;
    private final ProcessedEvents processed;
    private final Notices notices;

    public NotifyTransactionConfirmedService(Contacts contacts, NoticeSender sender, ProcessedEvents processed,
                                             Notices notices) {
        this.contacts = contacts;
        this.sender = sender;
        this.processed = processed;
        this.notices = notices;
    }

    @Override
    public Outcome notify(TransactionConfirmed event) {
        List<UUID> sent = new ArrayList<>();
        List<UUID> alreadySent = new ArrayList<>();
        notifyOne(event, event.buyerId(), notices::purchaseConfirmed, sent, alreadySent);
        notifyOne(event, event.producerId(), notices::saleConfirmed, sent, alreadySent);
        return new Outcome(List.copyOf(sent), List.copyOf(alreadySent));
    }

    private void notifyOne(TransactionConfirmed event, UUID recipient,
                           BiFunction<TransactionConfirmed, Contact, Notice> template,
                           List<UUID> sent, List<UUID> alreadySent) {
        if (processed.contains(event.eventId(), recipient)) {
            alreadySent.add(recipient);
            return;
        }
        sender.send(template.apply(event, contacts.find(recipient)));
        processed.record(event.eventId(), recipient);
        sent.add(recipient);
    }
}
