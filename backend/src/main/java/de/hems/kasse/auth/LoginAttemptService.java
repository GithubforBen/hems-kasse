package de.hems.kasse.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force throttle for {@code /api/auth/login} (CWE-307).
 *
 * <p>Failed attempts are counted per key — one key per targeted account and one per
 * client IP — inside a fixed window. Once a key hits the cap, further attempts are
 * rejected until the window expires. A successful login clears the account's counter.
 *
 * <p>Single-instance in-memory state is fine here: the Schulkasse runs as one backend
 * container and a restart merely resets the counters.
 */
@Component
public class LoginAttemptService {

    static final int MAX_FAILURES = 10;
    static final Duration WINDOW = Duration.ofMinutes(15);
    /** Hard cap so an attacker cycling user names can't grow the map without bound. */
    private static final int MAX_TRACKED_KEYS = 10_000;

    private record Counter(int failures, Instant windowStart) {}

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        Counter c = counters.get(key);
        if (c == null) return false;
        if (expired(c)) {
            counters.remove(key, c);
            return false;
        }
        return c.failures() >= MAX_FAILURES;
    }

    public void recordFailure(String key) {
        pruneIfNeeded();
        counters.compute(key, (k, c) -> (c == null || expired(c))
                ? new Counter(1, Instant.now())
                : new Counter(c.failures() + 1, c.windowStart()));
    }

    public void reset(String key) {
        counters.remove(key);
    }

    private static boolean expired(Counter c) {
        return c.windowStart().plus(WINDOW).isBefore(Instant.now());
    }

    private void pruneIfNeeded() {
        if (counters.size() < MAX_TRACKED_KEYS) return;
        counters.entrySet().removeIf(e -> expired(e.getValue()));
        // Still full of live entries? Fail open (drop the counters) rather than
        // risk unbounded memory — legitimate users keep working either way.
        if (counters.size() >= MAX_TRACKED_KEYS) counters.clear();
    }
}
