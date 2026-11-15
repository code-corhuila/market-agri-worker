package co.edu.corhuila.marketagri.worker.application.port.out;

import co.edu.corhuila.marketagri.worker.domain.model.Notice;

/** Sends one notice; the channel (SMTP) is the adapter's choice (ADR-016 point 3). */
public interface NoticeSender {

    void send(Notice notice);
}
