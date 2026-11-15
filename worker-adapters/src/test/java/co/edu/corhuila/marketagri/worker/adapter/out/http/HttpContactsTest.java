package co.edu.corhuila.marketagri.worker.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.marketagri.worker.adapter.correlation.Correlation;
import co.edu.corhuila.marketagri.worker.adapter.out.retry.Retry;
import co.edu.corhuila.marketagri.worker.domain.model.Contact;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * I-07 as the worker calls it (ADR-016 point 4, Annex D rules 4–7): its own SERVICE_TOKEN, the
 * event's correlation id, retried only on 429 and 5xx; a 404 is never retried.
 */
class HttpContactsTest {

    private static final UUID ANA = UUID.fromString("0b6f2c7e-4a51-4c1e-9d0e-2f1c3a7b9e10");

    private HttpServer auth;
    private final List<Integer> statuses = new ArrayList<>();
    private final List<String> seen = new ArrayList<>();
    private HttpContacts contacts;

    @BeforeEach
    void start() throws IOException {
        auth = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        auth.createContext("/", exchange -> {
            seen.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath()
                    + " | " + exchange.getRequestHeaders().getFirst("Authorization")
                    + " | " + exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
            int status = statuses.isEmpty() ? 200 : statuses.remove(0);
            byte[] body = (status == 200
                    ? "{\"id\":\"" + ANA + "\",\"email\":\"productor@market-agri.test\",\"name\":\"Ana Ruiz\"}"
                    : "{\"error\":\"X\",\"message\":\"m\",\"details\":[],\"traceId\":\"t\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        auth.start();
        Retry retry = new Retry(5, Duration.ofMillis(1), Duration.ofMillis(5), ThreadLocalRandom.current(), d -> { });
        contacts = new HttpContacts("http://127.0.0.1:" + auth.getAddress().getPort() + "/", "service-token",
                Duration.ofSeconds(2), retry, new ObjectMapper());
        MDC.put(Correlation.MDC_KEY, "corr-event-1");
    }

    @AfterEach
    void stop() {
        MDC.clear();
        auth.stop(0);
    }

    @Test
    void readsTheContactWithTheServiceTokenAndTheEventsCorrelationId() {
        Contact contact = contacts.find(ANA);

        assertThat(contact).isEqualTo(new Contact(ANA, "productor@market-agri.test", "Ana Ruiz"));
        assertThat(seen).containsExactly(
                "GET /internal/v1/auth/users/" + ANA + "/contact | Bearer service-token | corr-event-1");
    }

    @Test
    void aServerErrorOrTooManyRequestsIsRetried() {
        statuses.addAll(List.of(503, 429));

        assertThat(contacts.find(ANA).email()).isEqualTo("productor@market-agri.test");
        assertThat(seen).hasSize(3);
    }

    @Test
    void anUnknownUserIsNotRetried() {
        statuses.add(404);

        assertThatThrownBy(() -> contacts.find(ANA)).isInstanceOf(RecipientNotFoundException.class);
        assertThat(seen).hasSize(1);
    }

    @Test
    void anExpiredTokenIsNotRetried() {
        statuses.add(401);

        assertThatThrownBy(() -> contacts.find(ANA)).isNotInstanceOf(Retry.TransientFailure.class)
                .hasMessageContaining("401");
        assertThat(seen).hasSize(1);
    }

    @Test
    void afterFiveServerErrorsItGivesUpAsATransientFailure() {
        statuses.addAll(List.of(500, 500, 500, 500, 500));

        assertThatThrownBy(() -> contacts.find(ANA)).isInstanceOf(Retry.TransientFailure.class);
        assertThat(seen).hasSize(5);
    }

    @Test
    void anUnreachableAuthIsATransientFailure() {
        auth.stop(0);

        assertThatThrownBy(() -> contacts.find(ANA)).isInstanceOf(Retry.TransientFailure.class);
    }
}
