package co.edu.corhuila.marketagri.worker.adapter.out.retry;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounded retry of ADR-014 and ADR-016 point 6 (norm 5.7.2): only a {@link TransientFailure} is
 * retried, at most {@code attempts} times, waiting a random time between 0 and
 * {@code min(cap, base × 2ⁿ)} before attempt {@code n + 1} (full jitter). Anything else, and the
 * last transient failure, goes up to the consumer, which sends the message to the DLQ.
 */
public final class Retry {

    private static final Logger log = LoggerFactory.getLogger(Retry.class);

    /** A failure a new attempt can fix: network, {@code 429}, {@code 5xx}, an SMTP {@code 4xx}. */
    public static final class TransientFailure extends RuntimeException {

        public TransientFailure(String message) {
            super(message);
        }
    }

    /** The tests record the waits instead of sleeping. */
    @FunctionalInterface
    public interface Pause {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final int attempts;
    private final Duration base;
    private final Duration cap;
    private final RandomGenerator random;
    private final Pause pause;

    public Retry(int attempts, Duration base, Duration cap, RandomGenerator random, Pause pause) {
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be at least 1: " + attempts);
        }
        this.attempts = attempts;
        this.base = base;
        this.cap = cap;
        this.random = random;
        this.pause = pause;
    }

    public <T> T run(String what, Supplier<T> operation) {
        for (int done = 1; ; done++) {
            try {
                return operation.get();
            } catch (TransientFailure failure) {
                if (done == attempts) {
                    throw failure;
                }
                log.warn("{} attempt {} of {} failed: {}", what, done, attempts, failure.getMessage());
                waitAfter(done, failure);
            }
        }
    }

    private void waitAfter(int done, TransientFailure failure) {
        long ceiling = Math.min(cap.toMillis(), base.toMillis() << Math.min(done, 30));
        try {
            pause.sleep(Duration.ofMillis(random.nextLong(ceiling + 1)));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw failure;
        }
    }
}
