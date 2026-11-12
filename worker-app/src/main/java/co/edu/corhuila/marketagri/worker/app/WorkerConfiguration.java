package co.edu.corhuila.marketagri.worker.app;

import co.edu.corhuila.marketagri.worker.adapter.in.messaging.RabbitTransactionConfirmed;
import co.edu.corhuila.marketagri.worker.adapter.out.http.HttpContacts;
import co.edu.corhuila.marketagri.worker.adapter.out.persistence.JdbcProcessedEvents;
import co.edu.corhuila.marketagri.worker.adapter.out.retry.Retry;
import co.edu.corhuila.marketagri.worker.adapter.out.smtp.SmtpNoticeSender;
import co.edu.corhuila.marketagri.worker.application.port.in.NotifyTransactionConfirmed;
import co.edu.corhuila.marketagri.worker.application.port.out.Contacts;
import co.edu.corhuila.marketagri.worker.application.port.out.NoticeSender;
import co.edu.corhuila.marketagri.worker.application.port.out.ProcessedEvents;
import co.edu.corhuila.marketagri.worker.application.usecase.NotifyTransactionConfirmedService;
import co.edu.corhuila.marketagri.worker.domain.model.Notices;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.ConnectionFactory;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The only place that knows the concrete types: the use case of the core wired to its adapters,
 * with every limit from application.yml (Annex D, "Raíz de composición"). Each port is its own
 * bean, so a test can replace it.
 */
@Configuration
@EnableScheduling
public class WorkerConfiguration {

    @Bean
    Retry retry(@Value("${worker.retry.attempts}") int attempts, @Value("${worker.retry.base}") Duration base,
                @Value("${worker.retry.cap}") Duration cap) {
        return new Retry(attempts, base, cap, ThreadLocalRandom.current(), wait -> Thread.sleep(wait.toMillis()));
    }

    @Bean
    Contacts contacts(@Value("${worker.auth-api-url}") String authApiUrl,
                      @Value("${worker.service-token}") String serviceToken,
                      @Value("${worker.http-timeout}") Duration timeout, Retry retry, ObjectMapper json) {
        return new HttpContacts(authApiUrl, serviceToken, timeout, retry, json);
    }

    @Bean
    NoticeSender noticeSender(@Value("${worker.smtp.host}") String host, @Value("${worker.smtp.port}") int port,
                              @Value("${worker.smtp.from}") String from,
                              @Value("${worker.smtp.timeout}") Duration timeout, Retry retry) {
        return new SmtpNoticeSender(host, port, from, timeout, retry);
    }

    @Bean
    ProcessedEvents processedEvents(JdbcClient jdbc) {
        return new JdbcProcessedEvents(jdbc);
    }

    @Bean
    NotifyTransactionConfirmed notifyTransactionConfirmed(Contacts contacts, NoticeSender sender,
                                                          ProcessedEvents processed,
                                                          @Value("${worker.public-base-url}") String publicBaseUrl) {
        return new NotifyTransactionConfirmedService(contacts, sender, processed, new Notices(publicBaseUrl));
    }

    /** Off in the tests, which have no broker; infra turns it on (WORKER_BROKER_ENABLED). */
    @Bean
    @ConditionalOnProperty("worker.broker.enabled")
    RabbitTransactionConfirmed transactionConfirmedConsumer(NotifyTransactionConfirmed notify, ObjectMapper json,
                                                            @Value("${worker.broker.host}") String host,
                                                            @Value("${worker.broker.port}") int port,
                                                            @Value("${worker.broker.username}") String username,
                                                            @Value("${worker.broker.password}") String password) {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setAutomaticRecoveryEnabled(false);     // the consumer reconnects itself every 5 s
        factory.setConnectionTimeout(3000);
        factory.setHandshakeTimeout(3000);
        return new RabbitTransactionConfirmed(factory, json, notify);
    }
}
