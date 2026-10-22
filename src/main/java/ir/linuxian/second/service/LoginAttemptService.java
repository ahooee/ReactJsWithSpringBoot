package ir.linuxian.second.service;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force guard for the auth endpoints. A key (client IP,
 * optionally combined with the username) gets at most {@value #MAX_FAILURES}
 * failures per {@value #WINDOW_MINUTES} minutes; further attempts get HTTP 429
 * until the window slides past. A success clears the key.
 *
 * Single-instance state on purpose: it resets on restart (fail-open for
 * availability, while still stopping online guessing attacks).
 */
@Service
public class LoginAttemptService {

    public static final int MAX_FAILURES = 5;
    public static final int WINDOW_MINUTES = 15;

    private static final long WINDOW_MILLIS = WINDOW_MINUTES * 60L * 1000L;

    private final ConcurrentHashMap<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    /** Throws {@link RateLimitException} when the key is currently blocked. */
    public void check(String key) {
        if (isBlocked(key)) {
            throw new RateLimitException("Too many attempts. Try again in a few minutes.");
        }
    }

    public void failure(String key) {
        long now = System.currentTimeMillis();
        failures.compute(key, (k, times) -> {
            Deque<Long> q = times == null ? new ArrayDeque<>() : times;
            prune(q, now);
            q.addLast(now);
            return q;
        });
    }

    public void success(String key) {
        failures.remove(key);
    }

    private boolean isBlocked(String key) {
        Deque<Long> q = failures.get(key);
        if (q == null) {
            return false;
        }
        synchronized (q) {
            prune(q, System.currentTimeMillis());
            return q.size() >= MAX_FAILURES;
        }
    }

    private static void prune(Deque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MILLIS) {
            q.pollFirst();
        }
    }
}
