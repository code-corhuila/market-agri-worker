package co.edu.corhuila.marketagri.worker.domain.model;

/** One e-mail, ready to send: address, subject and plain-text body (ADR-016 point 3). */
public record Notice(String to, String subject, String body) {
}
