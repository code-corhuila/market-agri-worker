package co.edu.corhuila.marketagri.worker.adapter.out.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/**
 * ADR-014 and ADR-016 point 6 (norm 5.7.2): only a failure a new attempt can fix is retried, at
 * most 5 attempts, waiting a random time between 0 and min(30 s, 1 s × 2ⁿ) before attempt n + 1.
 */
class RetryTest {

    private final List<Duration> waits = new ArrayList<>();
    /** Always the top of the range, so the ceilings are visible. */
    private final RandomGenerator top = new RandomGenerator() {
        @Override
        public long nextLong() {
            return 0;
        }

        @Override
        public long nextLong(long bound) {
            return bound - 1;
        }
    };
    private final Retry retry = new Retry(5, Duration.ofSeconds(1), Duration.ofSeconds(30), top, waits::add);

    @Test
    void aTransientFailureIsRetriedUntilItWorks() {
        int[] calls = {0};
        String answer = retry.run("probe", () -> {
            if (++calls[0] < 3) {
                throw new Retry.TransientFailure("503 from auth");
            }
            return "ok";
        });

        assertThat(answer).isEqualTo("ok");
        assertThat(calls[0]).isEqualTo(3);
    }

    @Test
    void afterFiveAttemptsTheLastFailureIsThrownAndTheWaitsGrowWithFullJitter() {
        int[] calls = {0};
        assertThatThrownBy(() -> retry.run("probe", () -> {
            calls[0]++;
            throw new Retry.TransientFailure("connection refused");
        })).isInstanceOf(Retry.TransientFailure.class).hasMessageContaining("connection refused");

        assertThat(calls[0]).isEqualTo(5);
        assertThat(waits).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8),
                Duration.ofSeconds(16));
    }

    @Test
    void theWaitNeverExceedsTheCeiling() {
        Retry long7 = new Retry(7, Duration.ofSeconds(1), Duration.ofSeconds(30), top, waits::add);
        assertThatThrownBy(() -> long7.run("probe", () -> {
            throw new Retry.TransientFailure("down");
        })).isInstanceOf(Retry.TransientFailure.class);

        assertThat(waits).last().isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void anyOtherFailureIsNotRetried() {
        int[] calls = {0};
        assertThatThrownBy(() -> retry.run("probe", () -> {
            calls[0]++;
            throw new IllegalStateException("404 from auth");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(calls[0]).isEqualTo(1);
        assertThat(waits).isEmpty();
    }
}
