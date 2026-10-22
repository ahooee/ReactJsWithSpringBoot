package ir.linuxian.second.bot;

import ir.linuxian.telegram.LoginPrompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Drives TDLib's interactive login (phone → verification code → 2FA
 * password) from the admin Settings page instead of the console.
 * {@link TelegramBotRunner} hands this {@link LoginPrompt} to its
 * TelegramService: whenever TDLib asks for a value, the needed phase is
 * exposed to the admin UI, and the request blocks until the admin submits
 * it through the login endpoints.
 *
 * <p>TDLib locks the shared session directory, so only one client exists
 * at a time and a single mutable state + monitor is enough. When a newer
 * client takes over (restart after save/logout/cancel), an obsolete
 * prompt's thread simply parks forever instead of feeding answers to a
 * client nobody reads.</p>
 */
@Service
public class AdminLoginPrompt implements LoginPrompt {

    private static final Logger log = LoggerFactory.getLogger(AdminLoginPrompt.class);

    public enum Phase { IDLE, NEEDS_PHONE, NEEDS_CODE, NEEDS_PASSWORD }

    private final Object lock = new Object();
    private final AtomicLong generation = new AtomicLong();

    private Phase phase = Phase.IDLE;
    private String queuedPhone;
    private String answer;
    private boolean cancelled;
    private String lastError;

    // ---------- LoginPrompt (called by TDLib's update callback thread) ----------

    @Override
    public String ask(String label) {
        return waitFor(Phase.NEEDS_PHONE);
    }

    @Override
    public String askRaw(String label) {
        return waitFor(Phase.NEEDS_PASSWORD);
    }

    private String waitFor(Phase neededPhase) {
        long myGeneration = generation.get();
        while (true) {
            String value = null;
            boolean stale = false;
            synchronized (lock) {
                if (generation.get() != myGeneration) {
                    stale = true;
                } else {
                    if (phase != neededPhase) {
                        lastError = null;   // fresh step: drop the previous error
                    }
                    phase = neededPhase;
                    lock.notifyAll();       // wake status pollers
                    if (neededPhase == Phase.NEEDS_PHONE && notBlank(queuedPhone)) {
                        // Phone entered up front via login/start.
                        value = queuedPhone;
                        queuedPhone = null;
                    } else if (notBlank(answer)) {
                        value = answer;
                        answer = null;
                    } else {
                        try {
                            lock.wait(TimeUnit.SECONDS.toMillis(2));   // bounded, poll-for-state
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return "";
                        }
                        continue;
                    }
                }
            }
            if (stale) {
                parkForever(myGeneration);
            }
            return value == null ? "" : value;
        }
    }

    /** Obsolete client thread: park quietly (daemon), never spam TDLib. */
    private void parkForever(long generationAtWait) {
        log.debug("Obsolete TDLib login prompt parked (generation {}).", generationAtWait);
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------- admin submissions (login endpoints) ----------

    /** Phone entered up front; consumed by the next WaitPhoneNumber step
     * (or immediately if that step is already asking). */
    public void submitPhone(String phone) {
        synchronized (lock) {
            queuedPhone = phone == null || phone.isBlank() ? null : phone.trim();
            if (phase == Phase.NEEDS_PHONE && notBlank(queuedPhone)) {
                answer = queuedPhone;
                queuedPhone = null;
            }
            lock.notifyAll();
        }
    }

    public void submitCode(String code) {
        submit(Phase.NEEDS_CODE, code);
    }

    public void submitPassword(String password) {
        submit(Phase.NEEDS_PASSWORD, password);
    }

    private void submit(Phase expected, String value) {
        synchronized (lock) {
            if (phase != expected) {
                throw new IllegalStateException("TDLib is not waiting for "
                        + expected.name().replace("NEEDS_", "").toLowerCase()
                        + " right now (current step: " + phase.name() + ").");
            }
            answer = value;
            lock.notifyAll();
        }
    }

    public void cancel() {
        synchronized (lock) {
            cancelled = true;
            queuedPhone = null;
            answer = null;
            phase = Phase.IDLE;
            lock.notifyAll();
        }
    }

    /** A fresh client took over (restart/logout): reset transient state;
     * a queued phone stays available for the fresh client. */
    public void retire() {
        synchronized (lock) {
            generation.incrementAndGet();
            phase = Phase.IDLE;
            answer = null;
            cancelled = false;
            lastError = null;
            lock.notifyAll();
        }
    }

    public Phase currentPhase() {
        synchronized (lock) {
            return phase;
        }
    }

    public Map<String, Object> status() {
        synchronized (lock) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("phase", phase.name());
            m.put("cancelled", cancelled);
            m.put("error", lastError);
            return m;
        }
    }

    void reportAuthError(String message) {
        synchronized (lock) {
            lastError = message;
            lock.notifyAll();
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
