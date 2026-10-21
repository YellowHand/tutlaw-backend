package pl.gradusiuris.backend.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Prosty limit liczby zdarzeń w oknie czasowym ("co najwyżej 8 prób w 15 minut"), trzymany w pamięci serwera.
 * <p>
 * Działa dla jednej instancji aplikacji. Przy kilku instancjach każda liczyłaby osobno,
 * a wtedy trzeba przenieść licznik do wspólnego magazynu (np. Redis).
 */
public final class SlidingWindowRateLimiter {

    /** Po przekroczeniu tylu kluczy usuwamy wygasłe wpisy, żeby pamięć nie rosła bez końca. */
    static final int MAX_KEYS = 20_000;

    private final Clock clock;
    private final Map<String, ArrayDeque<Long>> hits = new HashMap<>();
    private long longestWindowMillis = 0;

    public SlidingWindowRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Zapisuje zdarzenie, jeśli w oknie mieści się jeszcze mniej niż {@code max} zdarzeń. Zwraca false, gdy limit wyczerpany. */
    public synchronized boolean tryAcquire(String key, int max, Duration window) {
        long now = clock.millis();
        ArrayDeque<Long> queue = prune(key, now, window.toMillis(), true);
        if (queue.size() >= max) {
            return false;
        }
        queue.addLast(now);
        remember(window);
        evictIfTooMany(now);
        return true;
    }

    /** Czy w oknie jest już {@code max} lub więcej zdarzeń. Niczego nie zapisuje. */
    public synchronized boolean isLimited(String key, int max, Duration window) {
        ArrayDeque<Long> queue = prune(key, clock.millis(), window.toMillis(), false);
        return queue != null && queue.size() >= max;
    }

    /** Zapisuje zdarzenie bez sprawdzania limitu (np. nieudane logowanie). */
    public synchronized void record(String key, Duration window) {
        long now = clock.millis();
        prune(key, now, window.toMillis(), true).addLast(now);
        remember(window);
        evictIfTooMany(now);
    }

    public synchronized void clear(String key) {
        hits.remove(key);
    }

    /** Za ile sekund najstarsze zdarzenie wypadnie z okna (czyli kiedy zwolni się jedno miejsce). Co najmniej 1. */
    public synchronized long retryAfterSeconds(String key, Duration window) {
        long now = clock.millis();
        ArrayDeque<Long> queue = prune(key, now, window.toMillis(), false);
        if (queue == null || queue.isEmpty()) {
            return 1;
        }
        long remainingMillis = window.toMillis() - (now - queue.peekFirst());
        return Math.max(1, (remainingMillis + 999) / 1000);
    }

    synchronized int trackedKeys() {
        return hits.size();
    }

    private ArrayDeque<Long> prune(String key, long now, long windowMillis, boolean create) {
        ArrayDeque<Long> queue = hits.get(key);
        if (queue == null) {
            if (!create) {
                return null;
            }
            queue = new ArrayDeque<>();
            hits.put(key, queue);
        }
        while (!queue.isEmpty() && now - queue.peekFirst() >= windowMillis) {
            queue.pollFirst();
        }
        if (queue.isEmpty() && !create) {
            hits.remove(key);
            return null;
        }
        return queue;
    }

    private void remember(Duration window) {
        longestWindowMillis = Math.max(longestWindowMillis, window.toMillis());
    }

    private void evictIfTooMany(long now) {
        if (hits.size() <= MAX_KEYS) {
            return;
        }
        hits.values().removeIf(q -> q.isEmpty() || now - q.peekLast() >= longestWindowMillis);
        if (hits.size() > MAX_KEYS) {
            // Ktoś zalewa serwer unikalnymi kluczami. Wolimy zapomnieć liczniki niż wyczerpać pamięć.
            hits.clear();
        }
    }
}
