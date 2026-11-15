package co.edu.corhuila.marketagri.worker.adapter.out.http;

import co.edu.corhuila.marketagri.worker.adapter.correlation.Correlation;
import co.edu.corhuila.marketagri.worker.adapter.out.retry.Retry;
import co.edu.corhuila.marketagri.worker.application.port.out.Contacts;
import co.edu.corhuila.marketagri.worker.domain.model.Contact;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * I-07 {@code GET /internal/v1/auth/users/{id}/contact} (ADR-016 point 4), as Annex D asks of
 * every call: the worker's own {@code SERVICE_TOKEN} (rule 4), the event's correlation id (rule 5),
 * a timeout (rule 6) and bounded retry only on network errors, {@code 429} and {@code 5xx} (rule 7).
 */
public final class HttpContacts implements Contacts {

    private static final int OK = 200;
    private static final int NOT_FOUND = 404;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int FIRST_SERVER_ERROR = 500;

    private final HttpClient client;
    private final String authApiUrl;
    private final String serviceToken;
    private final Duration timeout;
    private final Retry retry;
    private final ObjectMapper json;

    public HttpContacts(String authApiUrl, String serviceToken, Duration timeout, Retry retry, ObjectMapper json) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalArgumentException("SERVICE_TOKEN is required");
        }
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.authApiUrl = authApiUrl.endsWith("/") ? authApiUrl.substring(0, authApiUrl.length() - 1) : authApiUrl;
        this.serviceToken = serviceToken;
        this.timeout = timeout;
        this.retry = retry;
        this.json = json;
    }

    @Override
    public Contact find(UUID userId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(authApiUrl + "/internal/v1/auth/users/" + userId + "/contact"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + serviceToken)
                .header("X-Correlation-Id", Correlation.current())
                .header("Accept", "application/json")
                .GET().build();
        return retry.run("I-07", () -> read(userId, send(request)));
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException unreachable) {
            throw new Retry.TransientFailure("auth-api unreachable: " + unreachable.getClass().getSimpleName());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new Retry.TransientFailure("interrupted");
        }
    }

    private Contact read(UUID userId, HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == OK) {
            try {
                JsonNode body = json.readTree(response.body());
                return new Contact(UUID.fromString(body.path("id").asText()), body.path("email").asText(),
                        body.path("name").asText());
            } catch (IOException | IllegalArgumentException unreadable) {
                throw new IllegalStateException("I-07 answered an unreadable 200", unreadable);
            }
        }
        if (status == NOT_FOUND) {
            throw new RecipientNotFoundException(userId);
        }
        if (status == TOO_MANY_REQUESTS || status >= FIRST_SERVER_ERROR) {
            throw new Retry.TransientFailure("I-07 answered " + status);
        }
        throw new IllegalStateException("I-07 answered " + status);
    }
}
