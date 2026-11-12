package co.edu.corhuila.marketagri.worker.application.port.out;

import co.edu.corhuila.marketagri.worker.domain.model.Contact;
import java.util.UUID;

/** The users of auth as the worker sees them: only I-07, the contact of one user (Annex D, rule 8). */
public interface Contacts {

    Contact find(UUID userId);
}
