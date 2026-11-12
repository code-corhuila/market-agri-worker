package co.edu.corhuila.marketagri.worker.adapter.out.http;

import java.util.UUID;

/** I-07 answered {@code 404 USER_NOT_FOUND}: retrying cannot fix it (ADR-016 point 4). */
public final class RecipientNotFoundException extends RuntimeException {

    public RecipientNotFoundException(UUID userId) {
        super("I-07: no user " + userId);
    }
}
