package co.edu.corhuila.marketagri.worker.adapter.out.smtp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.marketagri.worker.adapter.correlation.Correlation;
import co.edu.corhuila.marketagri.worker.adapter.out.retry.Retry;
import co.edu.corhuila.marketagri.worker.domain.model.Notice;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * The SMTP channel of ADR-016 point 3: a plain-text UTF-8 e-mail to Mailpit. A 4xx reply or a
 * refused connection is retried (point 6); a 5xx reply is not; an address that could inject a
 * header is refused before connecting.
 */
class SmtpNoticeSenderTest {

    private static final Notice NOTICE = new Notice("comprador@market-agri.test", "Su compra fue confirmada",
            "Hola, Bruno Díaz:\n\nTotal: $ 85.000\n");

    private FakeSmtpServer mailpit;
    private SmtpNoticeSender sender;

    @BeforeEach
    void start() throws IOException {
        mailpit = new FakeSmtpServer();
        sender = sender(mailpit.port());
        MDC.put(Correlation.MDC_KEY, "corr-event-1");
    }

    private static SmtpNoticeSender sender(int port) {
        Retry retry = new Retry(5, Duration.ofMillis(1), Duration.ofMillis(5), ThreadLocalRandom.current(), d -> { });
        return new SmtpNoticeSender("127.0.0.1", port, "notificaciones@market-agri.test", Duration.ofSeconds(2), retry);
    }

    @AfterEach
    void stop() throws IOException {
        MDC.clear();
        mailpit.close();
    }

    @Test
    void sendsOneUtf8PlainTextMessageWithItsEnvelope() {
        sender.send(NOTICE);

        assertThat(mailpit.commands).contains("MAIL FROM:<notificaciones@market-agri.test>",
                "RCPT TO:<comprador@market-agri.test>", "DATA", "QUIT");
        String message = mailpit.messages.get(0);
        assertThat(message).contains("From: notificaciones@market-agri.test", "To: comprador@market-agri.test",
                "Subject: =?UTF-8?B?" + base64("Su compra fue confirmada") + "?=",
                "Content-Type: text/plain; charset=UTF-8", "Content-Transfer-Encoding: base64",
                "X-Correlation-Id: corr-event-1");
        String body = message.substring(message.indexOf("\n\n") + 2).replace("\n", "");
        assertThat(new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8))
                .isEqualTo("Hola, Bruno Díaz:\r\n\r\nTotal: $ 85.000\r\n");
    }

    @Test
    void aTemporaryRefusalIsRetried() {
        mailpit.refuseNext.add("451 try again later");

        sender.send(NOTICE);

        assertThat(mailpit.messages).hasSize(1);
        assertThat(mailpit.commands.stream().filter(c -> c.startsWith("RCPT")).count()).isEqualTo(2);
    }

    @Test
    void aPermanentRefusalIsNotRetried() {
        mailpit.refuseNext.add("550 no such mailbox");

        assertThatThrownBy(() -> sender.send(NOTICE)).isNotInstanceOf(Retry.TransientFailure.class)
                .hasMessageContaining("550");
        assertThat(mailpit.messages).isEmpty();
        assertThat(mailpit.commands.stream().filter(c -> c.startsWith("RCPT")).count()).isEqualTo(1);
    }

    @Test
    void aClosedPortIsATransientFailure() throws IOException {
        int closed = mailpit.port();
        mailpit.close();

        assertThatThrownBy(() -> sender(closed).send(NOTICE)).isInstanceOf(Retry.TransientFailure.class);
    }

    @Test
    void anAddressThatCouldInjectAHeaderIsRefusedBeforeConnecting() {
        Notice injected = new Notice("a@b.test\r\nBcc: all@x.test", "s", "b");

        assertThatThrownBy(() -> sender.send(injected)).isInstanceOf(IllegalArgumentException.class);
        assertThat(mailpit.commands).isEmpty();
    }

    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
