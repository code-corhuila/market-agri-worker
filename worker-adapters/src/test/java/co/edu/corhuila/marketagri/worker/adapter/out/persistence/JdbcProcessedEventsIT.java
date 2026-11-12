package co.edu.corhuila.marketagri.worker.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * The SQL of ProcessedEvents against the real worker schema, as worker_app (Annex C "Pruebas":
 * TEST_DATABASE_URL from scripts/test-database.sh; skipped without it). Random ids: nothing to clean.
 */
class JdbcProcessedEventsIT {

    private JdbcProcessedEvents processed;

    @BeforeEach
    void connect() {
        String url = System.getenv("TEST_DATABASE_URL");
        assumeTrue(url != null && !url.isBlank(), "TEST_DATABASE_URL not set");
        processed = new JdbcProcessedEvents(JdbcClient.create(new DriverManagerDataSource(url)));
    }

    @Test
    void aRecordedPairIsFoundAndOtherPairsAreNot() {
        UUID event = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        UUID producer = UUID.randomUUID();

        processed.record(event, buyer);

        assertThat(processed.contains(event, buyer)).isTrue();
        assertThat(processed.contains(event, producer)).isFalse();
        assertThat(processed.contains(UUID.randomUUID(), buyer)).isFalse();
    }

    @Test
    void recordingTheSamePairTwiceIsHarmless() {
        UUID event = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();

        processed.record(event, buyer);
        processed.record(event, buyer);

        assertThat(processed.contains(event, buyer)).isTrue();
    }
}
