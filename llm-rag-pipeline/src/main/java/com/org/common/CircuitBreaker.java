package com.org.common;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal, dependency-free circuit breaker for outbound calls (companion to {@link Resilience};
 * swap in Resilience4j once it ships a Spring Boot 4 starter). After {@code failureThreshold}
 * consecutive failures the breaker opens and {@link #allowRequest()} returns {@code false} — the
 * caller skips the outbound call instead of paying a timeout on every request while the vendor is
 * down. After {@code cooldown} a single probe is allowed through (half-open): success closes the
 * breaker, failure re-opens it for another cooldown.
 */
public final class CircuitBreaker {

    private final int failureThreshold;
    private final long cooldownMillis;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long openedAtMillis;

    public CircuitBreaker(int failureThreshold, Duration cooldown) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.cooldownMillis = cooldown.toMillis();
    }

    /** {@code true} when the call should be attempted (closed, or half-open probe after cooldown). */
    public boolean allowRequest() {
        if (consecutiveFailures.get() < failureThreshold) {
            return true;
        }
        return System.currentTimeMillis() - openedAtMillis >= cooldownMillis;
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
    }

    public void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            openedAtMillis = System.currentTimeMillis();
        }
    }
}
